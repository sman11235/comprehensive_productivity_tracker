# Comprehensive Productivity Tracker

This repository is a local-first event pipeline for collecting personal activity data, normalizing it through Kafka, persisting it in PostgreSQL/PostGIS, and exposing it through a small frontend plus read APIs.

The current stack includes:

* Python APIs and pollers for GitHub activity, Plaid transactions, and browser location ingestion
* Kafka for event transport
* a Spring Boot consumer for persistence, deduplication, and visit aggregation
* a static frontend for browsing visits and known places and for running optional OpenAI-backed queries

## Stack Overview

Services exposed by Docker Compose:

* frontend: `http://localhost:3000`
* auth API: `http://localhost:8000`
* location API: `http://localhost:8001`
* Kafka UI: `http://localhost:8080`
* pgAdmin: `http://localhost:5050`
* PostgreSQL: `localhost:5432`

Default local credentials:

* database: `personal_foundry`
* database user: `user`
* database password: `pass`
* pgAdmin email: `admin@admin.com`
* pgAdmin password: `admin`

## Prerequisites

Before starting the stack:

* install Docker and Docker Compose
* create a repository root `.env`
* use [python-producers/.env.example](/home/saket/programming/cs4365_project_comprehensive_productivity_tracker/python-producers/.env.example) as the starting template

Minimum useful variables:

* `GITHUB_USERNAME`
* `PLAID_CLIENT_ID`
* `PLAID_SECRET`

Optional variables:

* `GITHUB_TOKEN` for GitHub API rate limits
* `OPENAI_API_KEY` for `/agent/query` and `/agent/user-summary`
* `OPENAI_AGENT_MODEL` to override the default OpenAI model used by the location API

## Quick Start

From the repository root:

```bash
docker compose up --build
```

The frontend bind-mounts `./frontend` into Nginx, so HTML/CSS/JS edits usually appear after a browser refresh without rebuilding the image.

PostgreSQL state is stored in the named Docker volume `postgres-data`, mounted at `/var/lib/postgresql` to match PostgreSQL 18+ image expectations.

If you previously used an older volume layout mounted at `/var/lib/postgresql/data`, PostgreSQL 18+ may refuse startup. If you do not need the old local data:

```bash
docker compose down -v
```

If you do need that data, migrate it before switching layouts.

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

For local development, one partition and replication factor `1` are enough.

## Local Verification

### 1. Connect Plaid And Run Pollers

Open:

```text
http://localhost:8000/auth/test
```

Useful actions on that page:

* `Connect Plaid`
* `Seed Plaid Sandbox Item`
* `Run All Pollers`

Recommended flow:

1. Connect Plaid, or use `Seed Plaid Sandbox Item` if you want a fast local sandbox path.
2. Run `Run All Pollers`.

That triggers:

* GitHub dev activity polling
* Plaid transaction polling

Important GitHub constraint:

* the GitHub poller reads GitHub user `PushEvent` data, not your local `.git` history
* local commits do not appear until they are pushed to GitHub
* without `GITHUB_TOKEN`, only public events are visible
* GitHub event feeds can lag by roughly 30 seconds to 6 hours

### 2. Verify Kafka

Open:

```text
http://localhost:8080
```

Confirm new messages appear in:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`, after location sync is tested from the frontend

### 3. Test The Frontend

Open:

```text
http://localhost:3000
```

The frontend supports:

* viewing recent visits and their linked events
* viewing known places
* sending the browser's current location every 2.5 minutes after sync is enabled
* reverse-geocoding coordinates in the browser
* sending natural-language database questions to the optional OpenAI-backed agent

Default API base URL:

```text
http://localhost:8001
```

Suggested flow:

1. Click `Refresh Data`.
2. Confirm visits and known places load without errors.
3. Click `Start Location Sync`.
4. Allow browser location access.
5. Confirm status fields update.
6. Check Kafka UI for a new message on `saket.location`.

Browser geolocation works on `http://localhost` for local testing. If the frontend is opened from a LAN IP over plain HTTP, data refresh can still work, but geolocation usually requires HTTPS.

### 4. Verify PostgreSQL

Open pgAdmin:

```text
http://localhost:5050
```

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
* browser location events in `location_logs`
* visit and known-place records after location aggregation runs
* consumed event ids in `processed_events`

`health_logs` exists in the schema and frontend read model, but there is no health producer in the current local workflow.

## API Summary

### Auth API

Base URL:

```text
http://localhost:8000
```

Key endpoints:

* `GET /health`
* `GET /auth/test`
* `POST /pollers/<name>/run`
* `POST /pollers/run-all`
* `POST /auth/plaid/link-token`
* `POST /auth/plaid/exchange-public-token`
* `POST /auth/plaid/sandbox-seed`
* `GET /auth/plaid/config`

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

The agent endpoints require `OPENAI_API_KEY`. Without it, the rest of the stack still works, but agent requests fail.

## Architecture

### Ingestion

* the auth API manages Plaid state and can trigger the GitHub and Plaid pollers
* the location API accepts browser location payloads and publishes `saket.location`
* Python pollers publish normalized JSON events into Kafka

### Consumption

* the Spring Boot consumer subscribes to Kafka topics
* topic strategies map events into the correct domain models
* processed event ids are stored for deduplication
* location events can create or update visits and known places

### Storage And Read APIs

PostgreSQL/PostGIS stores:

* `dev_logs`
* `transaction_logs`
* `location_logs`
* `health_logs`
* `visits`
* `known_places`
* `processed_events`

The location API reads Postgres directly for the frontend and delegates optional natural-language analysis to the OpenAI-backed SQL agent.
