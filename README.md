# Comprehensive Productivity Tracker

This repository is a local-first productivity data pipeline:

* Python APIs and pollers ingest external activity and browser location data
* Kafka transports normalized events
* A Spring Boot consumer persists events into PostgreSQL/PostGIS
* A static frontend shows visits, known places, and an OpenAI-backed database query experience

## Services

Run the stack with Docker Compose and the repository exposes:

* frontend: `http://localhost:3000`
* auth API: `http://localhost:8000`
* location API: `http://localhost:8001`
* Kafka UI: `http://localhost:8080`
* pgAdmin: `http://localhost:5050`
* PostgreSQL: `localhost:5432`

## Prerequisites

Before starting the stack:

* install Docker and Docker Compose
* create the repository root `.env`
* use [python-producers/.env.example](/home/saket/programming/cs4365_project_comprehensive_productivity_tracker/python-producers/.env.example) as the template

Common variables:

* `GITHUB_USERNAME`
* `GITHUB_TOKEN`, optional but useful for private GitHub activity
* `PLAID_CLIENT_ID`
* `PLAID_SECRET`
* `OPENAI_API_KEY`, only required for the natural-language database agent

## Start The Stack

From the repository root:

```bash
docker compose up --build
```

Wait until these endpoints are reachable:

* `http://localhost:8000/health`
* `http://localhost:8001/health`
* `http://localhost:3000`
* `http://localhost:8080`
* `http://localhost:5050`

## Kafka Topics

Create these topics in Kafka UI if they do not already exist:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`

For local development, one partition and replication factor `1` are sufficient.

## Local Verification Flow

### 1. Connect Plaid And Run Pollers

Open:

```text
http://localhost:8000/auth/test
```

Use the test page actions:

* `Connect Plaid`
* `Seed Plaid Sandbox Item`
* `Run All Pollers`

Recommended path:

1. Connect Plaid.
2. If interactive Plaid Link is not practical, use `Seed Plaid Sandbox Item` instead.
3. Run `Run All Pollers`.

`Run All Pollers` triggers:

* GitHub dev activity sync
* Plaid transactions sync

The auth API also exposes direct endpoints for named pollers and Plaid auth flows, but the test page is the intended local workflow.

### 2. Verify Kafka

Open Kafka UI:

```text
http://localhost:8080
```

Confirm new messages appear in:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`, after testing browser location sync

### 3. Test The Frontend

Open:

```text
http://localhost:3000
```

The frontend supports:

* viewing recent visits with linked location, transaction, health, and dev events
* viewing known places
* asking natural-language questions through the location API agent
* posting the browser's current location every 2.5 minutes
* reverse-geocoding coordinates in the browser before publishing

Default API base URL:

```text
http://localhost:8001
```

Suggested test flow:

1. Click `Refresh Data`.
2. Confirm visits and known places load without errors.
3. Click `Start Location Sync`.
4. Allow browser location permission.
5. Confirm `Sync Status`, `Last Sent`, and `Resolved Place` update.
6. Check Kafka UI for a new event on `saket.location`.

Browser geolocation works on `http://localhost` for local testing. If you open the frontend from a LAN IP over plain HTTP, refresh and agent queries can still work, but location sync usually requires HTTPS.

### 4. Verify PostgreSQL

Open pgAdmin:

```text
http://localhost:5050
```

Credentials:

* email: `admin@admin.com`
* password: `admin`

Database connection:

* database: `personal_foundry`
* username: `user`
* password: `pass`

Run:

```sql
select * from dev_logs order by id desc;
select * from transaction_logs order by id desc;
select * from location_logs order by id desc;
select * from health_logs order by id desc;
select * from visits order by id desc;
select * from known_places order by id desc;
select * from processed_events order by id desc;
```

Expected results:

* GitHub commit activity in `dev_logs`
* Plaid transactions in `transaction_logs`
* browser-published location events in `location_logs`
* visit and known-place records when location aggregation creates or updates them
* consumed event ids in `processed_events`

`health_logs` exists in the schema and is shown in the frontend when populated, but the current local workflow in this repository does not include a health producer.

## APIs

### Auth API

Base URL:

```text
http://localhost:8000
```

Key endpoints:

* `GET /health`
* `GET /auth/test`
* `POST /pollers/github/run`
* `POST /pollers/plaid/run`
* `POST /pollers/run-all`
* `POST /auth/plaid/link-token`
* `POST /auth/plaid/exchange-public-token`
* `POST /auth/plaid/sandbox-seed`

### Location API

Base URL:

```text
http://localhost:8001
```

Key endpoints:

* `GET /health`
* `POST /locations`
* `GET /visits`
* `GET /known-places`
* `GET /agent/query`
* `POST /agent/query`
* `GET /agent/user-summary`
* `POST /agent/user-summary`

The OpenAI-backed agent requires `OPENAI_API_KEY`. Without it, agent endpoints return an error while the rest of the stack continues to work.

## Architecture

### Ingestion

* the auth API manages Plaid connection state and can trigger the GitHub and Plaid pollers
* the location API accepts browser location posts and publishes `saket.location`
* Python pollers publish normalized JSON events into Kafka

### Consumption

* the Spring Boot consumer subscribes to Kafka topics
* strategy classes route events by topic and payload type
* processed event ids are stored for deduplication

### Storage And Read APIs

PostgreSQL/PostGIS stores:

* `dev_logs`
* `transaction_logs`
* `location_logs`
* `health_logs`
* `visits`
* `known_places`
* `processed_events`

The location API reads visits and known places directly from Postgres for the frontend and delegates natural-language analysis to the OpenAI-backed SQL agent.
