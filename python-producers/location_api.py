from __future__ import annotations

from decimal import Decimal
from typing import Any

import psycopg
from flask import Flask, jsonify, request
from psycopg.rows import dict_row

from location_event_producer import publish_location_event
from producer_common import env

app = Flask(__name__)


@app.before_request
def handle_preflight():
    if request.method != "OPTIONS":
        return None
    return ("", 204)


@app.after_request
def add_cors_headers(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    requested_headers = request.headers.get("Access-Control-Request-Headers")
    response.headers["Access-Control-Allow-Headers"] = requested_headers or "*"
    response.headers["Access-Control-Allow-Methods"] = "GET,POST,PUT,PATCH,DELETE,OPTIONS"
    response.headers["Access-Control-Max-Age"] = "86400"
    return response


def postgres_dsn() -> str:
    explicit_dsn = env("POSTGRES_DSN", default="")
    if explicit_dsn:
        return explicit_dsn

    host = env("POSTGRES_HOST", default="localhost")
    port = env("POSTGRES_PORT", default="5432")
    dbname = env("POSTGRES_DB", default="personal_foundry")
    user = env("POSTGRES_USER", default="user")
    password = env("POSTGRES_PASSWORD", default="pass")
    sslmode = env("POSTGRES_SSLMODE", default="prefer")
    return f"host={host} port={port} dbname={dbname} user={user} password={password} sslmode={sslmode}"


def db_connection() -> psycopg.Connection:
    return psycopg.connect(postgres_dsn(), row_factory=dict_row)


def json_value(value: Any) -> Any:
    if isinstance(value, Decimal):
        return float(value)
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return value


def serialize_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [{key: json_value(value) for key, value in row.items()} for row in rows]


def fetch_visit_events(
    conn: psycopg.Connection,
    *,
    table_name: str,
    visit_ids: list[int],
    columns: str,
) -> dict[int, list[dict[str, Any]]]:
    if not visit_ids:
        return {}

    query = f"""
        SELECT {columns}
        FROM {table_name}
        WHERE visit_id = ANY(%s)
        ORDER BY timestamp ASC, id ASC
    """
    with conn.cursor() as cursor:
        cursor.execute(query, (visit_ids,))
        rows = serialize_rows(cursor.fetchall())

    grouped: dict[int, list[dict[str, Any]]] = {}
    for row in rows:
        grouped.setdefault(row["visit_id"], []).append(row)
    return grouped


def get_visits_with_events(*, limit: int) -> list[dict[str, Any]]:
    with db_connection() as conn:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                SELECT
                    v.id,
                    v.entry_time,
                    v.exit_time,
                    kp.id AS place_id,
                    kp.name AS place_name,
                    kp.category AS place_category,
                    kp.status AS place_status,
                    ST_Y(kp.loc::geometry) AS place_latitude,
                    ST_X(kp.loc::geometry) AS place_longitude
                FROM visits v
                LEFT JOIN known_places kp ON kp.id = v.place_id
                ORDER BY v.entry_time DESC, v.id DESC
                LIMIT %s
                """,
                (limit,),
            )
            visits = serialize_rows(cursor.fetchall())

        visit_ids = [visit["id"] for visit in visits]
        location_events = fetch_visit_events(
            conn,
            table_name="location_logs",
            visit_ids=visit_ids,
            columns="""
                id,
                visit_id,
                timestamp,
                device_id,
                location_name,
                ST_Y(loc::geometry) AS latitude,
                ST_X(loc::geometry) AS longitude
            """,
        )
        transaction_events = fetch_visit_events(
            conn,
            table_name="transaction_logs",
            visit_ids=visit_ids,
            columns="id, visit_id, timestamp, extern_txn_id, amount, category",
        )
        health_events = fetch_visit_events(
            conn,
            table_name="health_logs",
            visit_ids=visit_ids,
            columns="id, visit_id, timestamp, metric_type, val, unit",
        )
        dev_events = fetch_visit_events(
            conn,
            table_name="dev_logs",
            visit_ids=visit_ids,
            columns="id, visit_id, timestamp, platform, action_type, target, metadata",
        )

    visits_with_events: list[dict[str, Any]] = []
    for visit in visits:
        visit_id = visit["id"]
        visits_with_events.append(
            {
                "id": visit_id,
                "entryTime": visit["entry_time"],
                "exitTime": visit["exit_time"],
                "place": None
                if visit["place_id"] is None
                else {
                    "id": visit["place_id"],
                    "name": visit["place_name"],
                    "category": visit["place_category"],
                    "status": visit["place_status"],
                    "latitude": visit["place_latitude"],
                    "longitude": visit["place_longitude"],
                },
                "events": {
                    "locations": location_events.get(visit_id, []),
                    "transactions": transaction_events.get(visit_id, []),
                    "health": health_events.get(visit_id, []),
                    "dev": dev_events.get(visit_id, []),
                },
            }
        )
    return visits_with_events


def get_known_places(*, limit: int) -> list[dict[str, Any]]:
    with db_connection() as conn:
        with conn.cursor() as cursor:
            cursor.execute(
                """
                SELECT
                    kp.id,
                    kp.name,
                    kp.category,
                    kp.status,
                    kp.created_at,
                    ST_Y(kp.loc::geometry) AS latitude,
                    ST_X(kp.loc::geometry) AS longitude,
                    COUNT(v.id) AS visit_count,
                    MAX(v.entry_time) AS last_visit_at
                FROM known_places kp
                LEFT JOIN visits v ON v.place_id = kp.id
                GROUP BY kp.id
                ORDER BY last_visit_at DESC NULLS LAST, kp.created_at DESC, kp.id DESC
                LIMIT %s
                """,
                (limit,),
            )
            return serialize_rows(cursor.fetchall())


def extract_coordinates(body: dict[str, Any]) -> tuple[float, float]:
    if "latitude" in body and "longitude" in body:
        return float(body["latitude"]), float(body["longitude"])

    loc = body.get("loc") or {}
    coords = loc.get("coord") or []
    if len(coords) >= 2:
        return float(coords[1]), float(coords[0])

    raise ValueError("location must include latitude/longitude or loc.coord as [longitude, latitude]")


@app.get("/health")
def health():
    try:
        with db_connection() as conn:
            with conn.cursor() as cursor:
                cursor.execute("SELECT 1")
                cursor.fetchone()
        database_ok = True
        database_error = None
    except Exception as exc:
        database_ok = False
        database_error = str(exc)

    return (
        jsonify(
            {
                "ok": database_ok,
                "database_ok": database_ok,
                "database_error": database_error,
            }
        ),
        200 if database_ok else 500,
    )


@app.post("/locations")
def create_location():
    body = request.get_json(silent=True) or {}
    device_id = body.get("deviceId") or body.get("device_id")
    if not device_id:
        return jsonify({"ok": False, "error": "missing_device_id"}), 400

    try:
        latitude, longitude = extract_coordinates(body)
    except (TypeError, ValueError) as exc:
        return jsonify({"ok": False, "error": str(exc)}), 400

    location_name = body.get("locationName") or body.get("location_name")
    observed_at = body.get("timestamp") or body.get("observedAt")
    user_id = body.get("userId") or body.get("user_id")

    try:
        event = publish_location_event(
            bootstrap_servers=env("KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"),
            device_id=str(device_id),
            latitude=latitude,
            longitude=longitude,
            location_name=location_name,
            observed_at=observed_at,
            attributes={
                "provider": "frontend-location-api",
                "userId": user_id,
            },
        )
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)}), 500

    return (
        jsonify(
            {
                "ok": True,
                "topic": "saket.location",
                "eventId": event["eventId"],
                "observedAt": event["observedAt"],
                "payload": event["payload"],
            }
        ),
        202,
    )


@app.get("/visits")
def list_visits():
    limit_arg = request.args.get("limit", "50")
    try:
        limit = max(1, min(int(limit_arg), 200))
    except ValueError:
        return jsonify({"ok": False, "error": "limit must be an integer"}), 400

    try:
        visits = get_visits_with_events(limit=limit)
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)}), 500

    return jsonify({"ok": True, "count": len(visits), "visits": visits}), 200


@app.get("/known-places")
def list_known_places():
    limit_arg = request.args.get("limit", "100")
    try:
        limit = max(1, min(int(limit_arg), 200))
    except ValueError:
        return jsonify({"ok": False, "error": "limit must be an integer"}), 400

    try:
        places = get_known_places(limit=limit)
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)}), 500

    return jsonify({"ok": True, "count": len(places), "knownPlaces": places}), 200


def main() -> None:
    host = env("LOCATION_API_HOST", default="0.0.0.0")
    port = int(env("LOCATION_API_PORT", default="8001"))
    app.run(host=host, port=port, debug=False)


if __name__ == "__main__":
    main()
