# INSTRUCTIONS.md

## Purpose

This document is for an AI agent or developer who needs to verify the new Python adapters and frontend location flow locally.

The current local test flow is:

1. start the Docker Compose stack
2. open the auth test page at `http://localhost:8000/auth/test`
3. log in to Plaid
4. run all pollers
5. confirm Kafka received the events
6. confirm PostgreSQL persisted the events
7. open the frontend at `http://localhost:3000`
8. refresh visit and known-place data
9. start location sync and confirm a browser location event reaches Kafka/Postgres

Old instructions for manually publishing six location events are obsolete for this workflow and should be ignored.

## Expected Services

From `docker-compose.yml`, the local stack exposes:

* auth API: `http://localhost:8000`
* location API: `http://localhost:8001`
* frontend: `http://localhost:3000`
* Kafka UI: `http://localhost:8080`
* pgAdmin: `http://localhost:5050`
* PostgreSQL: `localhost:5432`

Relevant credentials:

* Postgres database: `personal_foundry`
* Postgres username: `user`
* Postgres password: `pass`
* pgAdmin email: `admin@admin.com`
* pgAdmin password: `admin`

## Topics and Tables

The Python adapters publish to these Kafka topics:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`

The Spring consumer persists them to these tables:

* `dev_logs`
* `transaction_logs`
* `location_logs`
* `visits`
* `known_places`
* `processed_events`

## Prerequisites

Before starting the stack:

* Docker and Docker Compose must be installed.
* The repository root `.env` file must contain valid adapter configuration.
* At minimum, verify:
  * `GITHUB_USERNAME`
  * `PLAID_CLIENT_ID`
  * `PLAID_SECRET`

If the Kafka topics do not already exist, create them in Kafka UI before running the pollers:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`

One partition and replication factor `1` are sufficient for local development.

## Step 1: Start the Stack

From the repository root, run:

```bash
docker compose up --build
```

Wait until these services are reachable:

* `http://localhost:8000/health`
* `http://localhost:8001/health`
* `http://localhost:3000`
* `http://localhost:8080`
* `http://localhost:5050`

The `python-auth-api` container serves the auth page and runs the poller endpoints. The `python-location-api` container accepts frontend location posts and serves visit/known-place reads. The Spring consumer should also be running before verification starts.

## Step 2: Open the Auth Test Page

Open:

```text
http://localhost:8000/auth/test
```

This page contains three relevant actions:

* `Connect Plaid`
* `Seed Plaid Sandbox Item`
* `Run All Pollers`

## Step 3: Log In to Plaid

Preferred path:

* click `Connect Plaid`
* complete Plaid Link
* wait for the page to report that Plaid is connected

If interactive Plaid Link is unavailable in the local environment, the sandbox shortcut is acceptable:

* click `Seed Plaid Sandbox Item`

Either path should save Plaid credentials into `python-producers/.state/plaid.json` for later poller runs.

## Step 4: Run All Pollers

From the same auth test page:

* click `Run All Pollers`

This calls `POST /pollers/run-all`, which runs:

* the GitHub dev activity poller
* the Plaid transactions poller

Successful output should show a result entry per poller and a `published_count` for each one.

## Step 5: Verify Kafka

Open Kafka UI:

```text
http://localhost:8080
```

Check the topics:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`, after the frontend location test

Confirm that new messages exist after the pollers run.

At a minimum:

* GitHub events should appear in `saket.dev_activity`
* Plaid transaction events should appear in `saket.wallet`
* frontend location events should appear in `saket.location` after the frontend location test

If no new messages appear, inspect the auth page response for poller errors before moving on.

## Step 6: Test the Frontend Location Flow

Open the frontend:

```text
http://localhost:3000
```

The page can:

* display recent visits and their associated events
* display known places
* send the browser's current location to the location API every 2.5 minutes
* reverse-geocode coordinates into a readable place name before publishing

The default Location API Base URL should be:

```text
http://localhost:8001
```

To verify the frontend:

1. Click `Refresh Data`.
2. Confirm the visits and known places sections either load data or show an empty state without crashing.
3. Click `Start Location Sync`.
4. Allow browser location permission when prompted.
5. Confirm `Sync Status`, `Last Sent`, and `Resolved Place` update.
6. Open Kafka UI and confirm a new event appears on `saket.location`.

Expected location event shape:

* Kafka topic: `saket.location`
* event envelope includes `eventId`, `deviceId`, `source`, `type`, `op`, `observedAt`, `payload`, and `attributes`
* payload includes `timestamp`, `deviceId`, `locationName`, and `loc.coord`

Browser geolocation only works from secure origins. `http://localhost:3000` is acceptable for local testing. If the frontend is opened from another computer over `http://<lan-ip>:3000`, the browser may block location sync unless the page is served over HTTPS.

## Step 7: Verify PostgreSQL Persistence

Open pgAdmin:

```text
http://localhost:5050
```

Log in with:

* email: `admin@admin.com`
* password: `admin`

Open the configured server. If prompted for the server password, enter:

```text
pass
```

Open the `personal_foundry` database and run these queries:

```sql
select * from dev_logs order by id desc;
select * from transaction_logs order by id desc;
select * from location_logs order by id desc;
select * from visits order by id desc;
select * from known_places order by id desc;
select * from processed_events order by id desc;
```

Verification criteria:

* rows from GitHub events are present in `dev_logs`
* rows from Plaid events are present in `transaction_logs`
* rows from frontend location events are present in `location_logs`
* visit and known-place rows are present when the location workflow creates or updates them
* consumed Kafka event ids are present in `processed_events`

## Success Condition

The local verification is successful when all of the following are true:

* Plaid authentication completed successfully
* `Run All Pollers` completed without errors
* Kafka UI shows new messages in `saket.dev_activity` and `saket.wallet`
* the frontend loads at `http://localhost:3000`
* `Refresh Data` successfully reads from the location API
* `Start Location Sync` successfully posts a browser location event
* Kafka UI shows a new message in `saket.location`
* pgAdmin shows newly persisted rows in `dev_logs`, `transaction_logs`, `location_logs`, and `processed_events`

## Failure Handling

If Plaid login fails:

* verify `PLAID_CLIENT_ID` and `PLAID_SECRET` in `.env`
* use `Seed Plaid Sandbox Item` as a local fallback

If the pollers fail:

* inspect the JSON response shown on `http://localhost:8000/auth/test`
* verify `GITHUB_USERNAME` is set
* verify the Kafka topics exist
* verify the auth API is using the correct Kafka bootstrap server for Compose: `kafka:29092`

If Kafka receives messages but PostgreSQL does not:

* inspect the `spring-consumer` container logs
* verify the Spring consumer is connected to Kafka
* verify the database schema exists and the consumer is healthy

If the frontend cannot post location:

* verify `http://localhost:8001/health` returns `ok: true`
* verify the Location API Base URL is `http://localhost:8001`
* verify the browser granted location permission
* verify the page is opened from `http://localhost:3000` or another secure origin
* inspect the `python-location-api` container logs for Kafka or database errors
