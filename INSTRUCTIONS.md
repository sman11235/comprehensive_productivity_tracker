# INSTRUCTIONS.md

## Purpose

This document is the current local verification guide for the repository. Use it when you need to confirm that:

* the Docker Compose stack starts correctly
* Plaid auth and the Python pollers publish Kafka events
* the frontend can read visits and known places
* browser location sync produces `saket.location` events
* the Spring consumer persists processed data into PostgreSQL

Older instructions that depended on manually publishing batches of location events are obsolete for this repository state.

## Expected Services

The Compose stack exposes:

* frontend: `http://localhost:3000`
* auth API: `http://localhost:8000`
* location API: `http://localhost:8001`
* Kafka UI: `http://localhost:8080`
* pgAdmin: `http://localhost:5050`
* PostgreSQL: `localhost:5432`

Relevant credentials:

* database: `personal_foundry`
* database username: `user`
* database password: `pass`
* pgAdmin email: `admin@admin.com`
* pgAdmin password: `admin`

## Topics And Tables

Kafka topics used by the current workflow:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`

Relevant database tables:

* `dev_logs`
* `transaction_logs`
* `location_logs`
* `health_logs`
* `visits`
* `known_places`
* `processed_events`

`health_logs` is part of the schema and frontend read model, but there is no health producer in the current local verification path.

## Prerequisites

Before starting the stack:

* install Docker and Docker Compose
* create the repository root `.env`
* use [python-producers/.env.example](/home/saket/programming/cs4365_project_comprehensive_productivity_tracker/python-producers/.env.example) as the template

Minimum variables to verify:

* `GITHUB_USERNAME`
* `PLAID_CLIENT_ID`
* `PLAID_SECRET`

Optional variables:

* `GITHUB_TOKEN`, optional for GitHub API rate limits
* `OPENAI_API_KEY`
* `OPENAI_AGENT_MODEL`

If the Kafka topics do not already exist, create them in Kafka UI before running pollers:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`

Use one partition and replication factor `1` for local development.

## Step 1: Start The Stack

From the repository root:

```bash
docker compose up --build
```

Wait for these to become reachable:

* `http://localhost:8000/health`
* `http://localhost:8001/health`
* `http://localhost:3000`
* `http://localhost:8080`
* `http://localhost:5050`

Service notes:

* `python-auth-api` serves the Plaid test page and poller endpoints
* `python-location-api` accepts location posts and serves visits, known places, and agent responses
* `spring-consumer` must be healthy enough to consume Kafka events before verification is meaningful

## Step 2: Open The Auth Test Page

Open:

```text
http://localhost:8000/auth/test
```

The page contains:

* `Connect Plaid`
* `Seed Plaid Sandbox Item`
* `Run All Pollers`

## Step 3: Connect Plaid

Preferred path:

* click `Connect Plaid`
* complete Plaid Link
* wait for the page to show a connected result

Fallback path:

* click `Seed Plaid Sandbox Item`

Either path should write Plaid state into `python-producers/.state/plaid.json`.

## Step 4: Run All Pollers

From the auth test page:

* click `Run All Pollers`

This calls `POST /pollers/run-all` and runs:

* the GitHub dev activity poller
* the Plaid transactions poller

Expected result:

* one result object per poller
* `published_count` shown for successful runs

Important GitHub detail:

* the GitHub poller reads GitHub `PushEvent` activity, not your local `.git` history
* a commit will not increase `published_count` until you push it to GitHub
* if `GITHUB_TOKEN` is unset, the poller only sees public events
* even after push, the count depends on that push appearing in the GitHub user events feed, which GitHub says can lag by roughly 30 seconds to 6 hours

## Step 5: Verify Kafka

Open:

```text
http://localhost:8080
```

Check:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`, after frontend location sync

Minimum validation:

* GitHub events appear in `saket.dev_activity`
* Plaid transaction events appear in `saket.wallet`
* frontend location events appear in `saket.location`

If Kafka stays empty, inspect the auth test page response before moving on.

## Step 6: Test The Frontend Read And Location Flow

Open:

```text
http://localhost:3000
```

Expected frontend capabilities:

* read visits from `GET /visits`
* read known places from `GET /known-places`
* post browser location to `POST /locations`
* run database-agent questions against `POST /agent/query`

Default API base URL:

```text
http://localhost:8001
```

Verification flow:

1. Click `Refresh Data`.
2. Confirm the visits and known places sections load without crashing.
3. Click `Start Location Sync`.
4. Allow browser location permission.
5. Confirm `Sync Status`, `Last Sent`, and `Resolved Place` update.
6. Confirm Kafka UI shows a new event on `saket.location`.
7. Refresh the frontend again and confirm the new location data is visible after the consumer processes it.

Expected location event characteristics:

* topic: `saket.location`
* envelope includes `eventId`, `deviceId`, `source`, `type`, `op`, `observedAt`, `payload`, and `attributes`
* payload includes a timestamp, a device id, a location name, and GeoJSON-style coordinates

Browser geolocation only works from secure origins or `http://localhost`. If the frontend is opened from another machine over `http://<lan-ip>:3000`, geolocation will usually fail unless the frontend is served over HTTPS.

## Step 7: Verify PostgreSQL Persistence

Open pgAdmin:

```text
http://localhost:5050
```

Login:

* email: `admin@admin.com`
* password: `admin`

If pgAdmin asks for the server password, enter:

```text
pass
```

Open database `personal_foundry` and run:

```sql
select * from dev_logs order by id desc;
select * from transaction_logs order by id desc;
select * from location_logs order by id desc;
select * from health_logs order by id desc;
select * from visits order by id desc;
select * from known_places order by id desc;
select * from processed_events order by id desc;
```

Verification criteria:

* GitHub rows exist in `dev_logs`
* Plaid rows exist in `transaction_logs`
* frontend location rows exist in `location_logs`
* visit and known-place rows exist when location aggregation creates or updates them
* consumed Kafka ids exist in `processed_events`

Do not treat an empty `health_logs` table as a failure for this workflow.

## Success Condition

Local verification is successful when all of the following are true:

* the Compose stack starts
* Plaid authentication or sandbox seeding succeeds
* `Run All Pollers` completes without poller errors
* Kafka UI shows new messages in `saket.dev_activity` and `saket.wallet`
* the frontend loads and can refresh visits and known places
* `Start Location Sync` successfully posts a browser location event
* Kafka UI shows a new message in `saket.location`
* PostgreSQL contains new rows in `dev_logs`, `transaction_logs`, `location_logs`, and `processed_events`

## Failure Handling

If Plaid login fails:

* verify `PLAID_CLIENT_ID` and `PLAID_SECRET` in `.env`
* use `Seed Plaid Sandbox Item` as the fallback path

If the pollers fail:

* inspect the JSON response shown by `http://localhost:8000/auth/test`
* verify `GITHUB_USERNAME` is set
* verify the Kafka topics exist
* verify the auth API is using `kafka:29092` inside Compose

If the frontend cannot refresh data:

* verify `http://localhost:8001/health`
* verify the API base URL is `http://localhost:8001`
* inspect browser console and network failures

If location sync fails:

* verify browser location permission
* verify the frontend is opened on `http://localhost:3000` or HTTPS
* verify the location API is reachable and Kafka is healthy

If agent queries fail:

* verify `OPENAI_API_KEY` is set
* remember that agent endpoints are optional and are not required for Kafka or persistence verification
