# Personal Foundry

A “personal Palantir Foundry” for my life: ingest events from devices + APIs into a unified Postgres/PostGIS data model, then enrich them with location intelligence + visit context.

## Architecture

### Ingestion

Producers publish JSON events into Kafka topics (saket.location, saket.dev_activity, saket.health, etc.)
A Spring Boot consumer subscribes and routes events by type using strategy beans (ITypeStrategy)

### Enrichment

Location events are aggregated into a sliding window to detect whether I’m stationary vs moving
A small user state machine tracks START → MOVING → VISITING
When VISITING, non-location events (dev / health / transactions) are linked to the current Visit

### Storage

Postgres + PostGIS
Key tables: location_logs, known_places, visits, dev_logs, health_logs, transaction_logs
