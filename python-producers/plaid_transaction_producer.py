from __future__ import annotations

import argparse
import hashlib
from datetime import date
from typing import Any

from producer_common import (
    JsonApiClient,
    KafkaEventPublisher,
    build_event,
    env,
    save_json_file,
    load_json_file,
    to_iso8601,
)

TOPIC = "saket.wallet"
EVENT_TYPE = "saket.wallet"
SOURCE = "api.plaid"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch Plaid transactions and publish Kafka events.")
    parser.add_argument("--bootstrap-servers", default=env("KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"))
    parser.add_argument("--device-id", default=env("TXN_DEVICE_ID", default="plaid-producer"))
    parser.add_argument("--state-file", default=env("PLAID_STATE_FILE", default="python-producers/.state/plaid.json"))
    parser.add_argument("--count", type=int, default=int(env("PLAID_COUNT", default="100")))
    return parser.parse_args()


def resolve_access_token(state_file: str) -> str:
    state = load_json_file(state_file, default={})
    access_token = state.get("access_token") or env("PLAID_ACCESS_TOKEN", default="")
    if not access_token:
        raise RuntimeError("Plaid access token is not initialized. Run the auth API exchange flow first.")
    return access_token


def fetch_transactions(cursor: str | None, count: int, *, access_token: str) -> dict[str, Any]:
    client = JsonApiClient(base_url=env("PLAID_BASE_URL", default="https://sandbox.plaid.com"))
    response = client.post(
        "/transactions/sync",
        payload={
            "client_id": env("PLAID_CLIENT_ID", required=True),
            "secret": env("PLAID_SECRET", required=True),
            "access_token": access_token,
            "cursor": cursor,
            "count": count,
        },
    )
    return response


def choose_timestamp(txn: dict[str, Any]) -> str:
    authorized = txn.get("authorized_datetime")
    posted = txn.get("datetime")
    txn_date = txn.get("date")
    fallback = date.fromisoformat(txn_date) if txn_date else None
    if authorized or posted:
        return to_iso8601(authorized or posted, fallback_date=fallback)
    return synthetic_transaction_timestamp(txn, fallback)


def synthetic_transaction_timestamp(txn: dict[str, Any], fallback_date: date | None) -> str:
    if fallback_date is None:
        return to_iso8601(None, fallback_date=None)

    seed = txn.get("transaction_id") or txn.get("pending_transaction_id") or txn.get("name") or "plaid"
    digest = hashlib.sha256(seed.encode("utf-8")).digest()

    seconds_in_day = 24 * 60 * 60
    second_offset = int.from_bytes(digest[:4], "big") % seconds_in_day
    # Spread synthetic timestamps across the day while remaining stable per transaction.
    return to_iso8601(
        None,
        fallback_date=fallback_date,
        fallback_seconds=second_offset,
    )


def choose_category(txn: dict[str, Any]) -> str:
    pfc = txn.get("personal_finance_category") or {}
    if pfc.get("primary"):
        return pfc["primary"]
    categories = txn.get("category") or []
    if categories:
        return categories[-1]
    return txn.get("merchant_name") or txn.get("name") or "uncategorized"


def map_payload(txn: dict[str, Any]) -> dict[str, Any]:
    return {
        "externTxnId": txn["transaction_id"],
        "timestamp": choose_timestamp(txn),
        "amount": txn.get("amount"),
        "category": choose_category(txn),
    }


def main() -> None:
    args = parse_args()
    sync_transactions(
        bootstrap_servers=args.bootstrap_servers,
        device_id=args.device_id,
        state_file=args.state_file,
        count=args.count,
    )


def sync_transactions(*, bootstrap_servers: str, device_id: str, state_file: str, count: int) -> int:
    state = load_json_file(state_file, default={"cursor": None})
    cursor = state.get("cursor")
    access_token = resolve_access_token(state_file)

    added: list[dict[str, Any]] = []
    has_more = True
    next_cursor = cursor
    while has_more:
        response = fetch_transactions(next_cursor, count, access_token=access_token)
        added.extend(response.get("added", []))
        next_cursor = response.get("next_cursor")
        has_more = bool(response.get("has_more"))

    if not added:
        print("No new Plaid transactions to publish.")
        state["cursor"] = next_cursor
        save_json_file(state_file, state)
        return 0

    publisher = KafkaEventPublisher(bootstrap_servers)
    try:
        for txn in added:
            payload = map_payload(txn)
            event = build_event(
                event_id=f"plaid-{txn['transaction_id']}",
                device_id=device_id,
                source=SOURCE,
                event_type=EVENT_TYPE,
                observed_at=payload["timestamp"],
                payload=payload,
                attributes={
                    "provider": "plaid-transactions-sync",
                    "accountId": txn.get("account_id"),
                    "merchantName": txn.get("merchant_name"),
                    "pending": txn.get("pending"),
                },
            )
            publisher.publish(TOPIC, event)
            print(f"Published Plaid transaction {txn['transaction_id']} to {TOPIC}")
    finally:
        publisher.close()

    state["cursor"] = next_cursor
    save_json_file(state_file, state)
    return len(added)


if __name__ == "__main__":
    main()
