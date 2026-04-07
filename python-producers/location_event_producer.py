from __future__ import annotations

import uuid
from datetime import UTC, datetime
from typing import Any

from producer_common import KafkaEventPublisher, build_event, env, utc_now_iso

TOPIC = "saket.location"
EVENT_TYPE = "saket.location"
SOURCE = "frontend.location"


def normalize_timestamp(value: str | None) -> str:
    if not value:
        return utc_now_iso()

    normalized = value.replace("Z", "+00:00")
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def publish_location_event(
    *,
    bootstrap_servers: str,
    device_id: str,
    latitude: float,
    longitude: float,
    location_name: str | None = None,
    observed_at: str | None = None,
    attributes: dict[str, Any] | None = None,
) -> dict[str, Any]:
    timestamp = normalize_timestamp(observed_at)
    event = build_event(
        event_id=f"location-{uuid.uuid4()}",
        device_id=device_id,
        source=SOURCE,
        event_type=EVENT_TYPE,
        observed_at=timestamp,
        payload={
            "timestamp": timestamp,
            "deviceId": device_id,
            "locationName": location_name,
            "loc": {
                "type": "Point",
                "coord": [longitude, latitude],
            },
        },
        attributes=attributes or {"provider": "frontend-location-api"},
    )

    publisher = KafkaEventPublisher(bootstrap_servers or env("KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"))
    try:
        publisher.publish(TOPIC, event)
    finally:
        publisher.close()

    return event
