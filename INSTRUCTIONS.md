# instructions.md

## Purpose

This document is written for an AI agent that must start, exercise, and verify the **Comprehensive Productivity Tracker** locally.

The system ingests JSON events from Kafka into a Spring Boot consumer, enriches location events with visit context, and persists results to PostgreSQL/PostGIS.

---

## Goal

Bring the stack up with Docker Compose, create the Kafka topic, publish a fixed sequence of location events, and verify that the resulting `location_logs.visit_id` values are:

null, 1, 1, 1, 1, null

This expected output represents:

* the start of movement with no active visit,
* a visit being created and associated with the middle stationary rows,
* and a final row after leaving the visit.

---

## Environment Assumptions

The agent should assume the following:

* Docker is installed.
* Docker Compose is available.
* The repository root contains a valid `docker-compose.yml`.
* The Compose stack exposes:

  * Kafka UI at `http://localhost:8080`
  * pgAdmin at `http://localhost:5050`
* PostgreSQL credentials are:

  * database: `personal_foundry`
  * username: `user`
  * password: `pass`
* pgAdmin credentials are:

  * email: `admin@admin.com`
  * password: `admin`
* Kafka broker is expected to be reachable internally at `kafka:29092`.
* The Kafka topic to use is `saket.location`.

---

## High-Level Workflow

1. Start the full Docker Compose stack.
2. Wait for the Spring consumer and Kafka to become ready.
3. Create the Kafka topic `saket.location`.
4. Publish the required six JSON messages in the exact order listed below.
5. Query PostgreSQL.
6. Confirm that `location_logs.visit_id` matches the expected pattern.

---

## Step 1: Start the Stack

From the repository root, run:

docker compose up --build

### Readiness Notes

* The application itself may become generally available in about 20 seconds.
* Kafka-to-consumer processing may take significantly longer on first startup.
* A first-message delay of several minutes is acceptable in this environment.
* After Kafka successfully connects to the Spring consumer, later messages should process immediately.

The agent should not assume failure solely because the first Kafka message is delayed.

---

## Step 2: Wait for Services

The agent should verify that the following UIs are reachable:

* Kafka UI: `http://localhost:8080`
* pgAdmin: `http://localhost:5050`

The agent should also monitor container logs if possible, especially for the Spring consumer, until it appears capable of consuming Kafka messages.

---

## Step 3: Create the Kafka Topic

Create a topic with the following configuration:

* topic name: `saket.location`
* time to retain: `12 hours`
* max size on disk: `1 GB`
* maximum message size in bytes: leave blank
* all other numeric fields: `1`

If topic creation is performed programmatically instead of via Kafka UI, preserve the same intent:

* one partition
* replication factor one
* retention equivalent to twelve hours
* retention bytes equivalent to one gigabyte

---

## Step 4: Publish the Test Event Sequence

Publish the following six messages to `saket.location` one by one, in the exact order shown.

### Message 1

{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:10:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }

### Message 2

{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:20:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }

### Message 3

{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:30:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }

### Message 4

{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:41:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }

### Message 5

{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:50:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 43.7501] } }, "attributes": { "provider": "apple-core-location" } }

### Message 6

{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T16:00:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }

### Semantic Meaning of the Sequence

This sequence represents a user who:

* remains stationary for a period of time,
* leaves that stationary context,
* then returns.

The backend should interpret the middle rows as belonging to one visit.

---

## Step 5: Verify in PostgreSQL

After all six messages have been processed, query the database.

If using pgAdmin:

1. Open `http://localhost:5050`.
2. Log in with:

   * email: `admin@admin.com`
   * password: `admin`
3. Open the registered server.
4. When prompted for the server password, enter:

pass

5. Open the `personal_foundry` database.
6. Open a query/script tab.
7. Run:

select * from location_logs;

If using `psql` instead of pgAdmin, any equivalent query method is acceptable.

---

## Step 6: Expected Result

Inspect the `visit_id` column in insertion order from top to bottom.

The expected pattern is:

null, 1, 1, 1, 1, null

### Interpretation

* first row: no visit yet
* middle four rows: attached to visit `1`
* last row: no active visit after departure

If the agent sees this exact pattern, the run is successful.

---

## Failure Handling Notes

### If Kafka messages do not process immediately

The first Kafka delivery may be delayed for several minutes on startup. This is expected in this environment and should not immediately be treated as failure.

### If later messages are also delayed

The agent should inspect:

* Docker container logs
* Kafka topic existence
* whether the Spring consumer is connected to Kafka
* whether the consumer group is actively reading from `saket.location`

### If the database shape does not match expectations

The agent should verify:

* all six messages were published
* the messages were published in the correct order
* the coordinates and timestamps were not altered
* the topic name was exactly `saket.location`

---

## System Architecture Summary

### Ingestion

Producers publish JSON events into Kafka topics such as:

* `saket.location`
* `saket.dev_activity`
* `saket.health`

A Spring Boot consumer subscribes and routes events by type using strategy beans such as `ITypeStrategy`.

### Enrichment

Location events are aggregated into a sliding window to determine whether the user is stationary or moving.

A user state machine tracks:

START → MOVING → VISITING

When the user is in `VISITING`, non-location events such as development activity, health data, and transactions may be linked to the active `Visit`.

### Storage

The persistence layer is PostgreSQL with PostGIS.

Important tables include:

* `location_logs`
* `known_places`
* `visits`
* `dev_logs`
* `health_logs`
* `transaction_logs`

---

## Completion Condition

The task is complete only when:

1. the stack has started,
2. the topic exists,
3. all six events have been published in order,
4. `select * from location_logs;` shows the expected visit association pattern.
