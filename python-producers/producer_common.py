from __future__ import annotations

import json
import os
import uuid
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from typing import Any

import requests
from kafka import KafkaProducer


def utc_now_iso() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def to_iso8601(value: str | None, *, fallback_date: date | None = None, fallback_seconds: int = 12 * 60 * 60) -> str:
    if value:
        normalized = value.replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=UTC)
        return parsed.astimezone(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")

    if fallback_date is None:
        fallback_date = datetime.now(UTC).date()

    parsed = datetime.combine(fallback_date, datetime.min.time(), tzinfo=UTC) + timedelta(seconds=fallback_seconds % (24 * 60 * 60))
    return parsed.isoformat().replace("+00:00", "Z")


def env(name: str, *, default: str | None = None, required: bool = False) -> str:
    value = os.getenv(name, default)
    if required and not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value or ""


def load_json_file(path: str, *, default: Any) -> Any:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            return json.load(handle)
    except FileNotFoundError:
        return default


def save_json_file(path: str, value: Any) -> None:
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(value, handle, indent=2, sort_keys=True)


def build_event(
    *,
    event_id: str,
    device_id: str,
    source: str,
    event_type: str,
    observed_at: str,
    payload: dict[str, Any],
    attributes: dict[str, Any] | None = None,
    op: str = "CREATE",
) -> dict[str, Any]:
    return {
        "eventId": event_id,
        "deviceId": device_id,
        "source": source,
        "type": event_type,
        "op": op,
        "observedAt": observed_at,
        "payload": payload,
        "attributes": attributes or {},
    }


@dataclass
class KafkaEventPublisher:
    bootstrap_servers: str

    def __post_init__(self) -> None:
        servers = [item.strip() for item in self.bootstrap_servers.split(",") if item.strip()]
        self._producer = KafkaProducer(
            bootstrap_servers=servers,
            value_serializer=lambda value: json.dumps(value).encode("utf-8"),
        )

    def publish(self, topic: str, event: dict[str, Any]) -> None:
        self._producer.send(topic, value=event).get(timeout=30)

    def close(self) -> None:
        self._producer.flush()
        self._producer.close()


class JsonApiClient:
    def __init__(self, *, base_url: str, headers: dict[str, str] | None = None, timeout_seconds: int = 30):
        self.base_url = base_url.rstrip("/")
        self.headers = headers or {}
        self.timeout_seconds = timeout_seconds

    def get(self, path: str, *, params: dict[str, Any] | None = None) -> Any:
        response = requests.get(
            f"{self.base_url}/{path.lstrip('/')}",
            headers=self.headers,
            params=params,
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        return response.json()

    def post(self, path: str, *, payload: dict[str, Any]) -> Any:
        response = requests.post(
            f"{self.base_url}/{path.lstrip('/')}",
            headers={**self.headers, "Content-Type": "application/json"},
            json=payload,
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        return response.json()


def new_device_id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:12]}"


def decimal_to_float(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, Decimal):
        return float(value)
    return float(value)
