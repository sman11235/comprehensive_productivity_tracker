# INSTRUCTIONS.md

## Purpose

This is the current local verification guide for the repository. Use it to confirm that:

* the Docker Compose stack starts cleanly
* Plaid auth or sandbox seeding works
* the Python pollers publish Kafka events
* the frontend can read visits and known places
* browser location sync produces `saket.location` events
* the Spring consumer persists processed data into PostgreSQL

Older instructions that depended on manually publishing location batches are obsolete for the current repository state.

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

`health_logs` is part of the schema and frontend read model, but there is no health producer in the current verification path.

## Prerequisites

Before starting the stack:

* install Docker and Docker Compose
* create a repository root `.env`
* use [python-producers/.env.example](/home/saket/programming/cs4365_project_comprehensive_productivity_tracker/python-producers/.env.example) as the starting template

Minimum variables to set:

* `GITHUB_USERNAME`
* `PLAID_CLIENT_ID`
* `PLAID_SECRET`

Optional variables:

* `GITHUB_TOKEN` for GitHub rate limits
* `OPENAI_API_KEY` for agent endpoints
* `OPENAI_AGENT_MODEL` to override the default agent model

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

PostgreSQL state is stored in the named Docker volume `postgres-data`, mounted at `/var/lib/postgresql` to match PostgreSQL 18+ image expectations.

If you previously started the stack with an older `/var/lib/postgresql/data` volume layout, PostgreSQL 18+ may fail to start. If that old local data is disposable:

```bash
docker compose down -v
```

If the data matters, migrate it before switching layouts.

Wait for these endpoints:

* `http://localhost:8000/health`
* `http://localhost:8001/health`
* `http://localhost:3000`
* `http://localhost:8080`
* `http://localhost:5050`

Service notes:

* `python-auth-api` serves the Plaid test page and manual poller endpoints
* `python-location-api` accepts location posts and serves visits, known places, and agent responses
* `spring-consumer` must be running before event verification is meaningful

## Step 2: Open The Auth Test Page

Open:

```text
http://localhost:8000/auth/test
```

The page exposes:

* `Connect Plaid`
* `Seed Plaid Sandbox Item`
* `Run All Pollers`

## Step 3: Connect Plaid

Preferred path:

* click `Connect Plaid`
* complete Plaid Link
* wait for a successful connected result

Fallback path:

* click `Seed Plaid Sandbox Item`

Either path should persist Plaid state into `python-producers/.state/plaid.json`.

## Step 4: Run All Pollers

From the auth test page:

* click `Run All Pollers`

This calls `POST /pollers/run-all` and runs:

* the GitHub dev activity poller
* the Plaid transaction poller

Expected result:

* one result object per poller
* `published_count` shown for successful runs

GitHub constraints:

* the poller reads GitHub user `PushEvent` data, not local `.git` history
* a commit is invisible until it is pushed to GitHub
* without `GITHUB_TOKEN`, only public events are visible
* GitHub event feeds can lag by roughly 30 seconds to 6 hours

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
* run natural-language database questions against `GET` or `POST /agent/query`

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
* payload includes timestamp, device id, location name, and GeoJSON-style coordinates

Browser geolocation works on `http://localhost` and other secure origins. If the frontend is opened from another machine over `http://<lan-ip>:3000`, geolocation usually fails unless the site is served over HTTPS.

## Step 7: Verify PostgreSQL Persistence

Open pgAdmin:

```text
http://localhost:5050
```

Login:

* email: `admin@admin.com`
* password: `admin`

If pgAdmin asks for the server password, use:

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
* use `Seed Plaid Sandbox Item` as the fallback

If the pollers fail:

* inspect the JSON response shown by `http://localhost:8000/auth/test`
* verify `GITHUB_USERNAME` is set
* verify the Kafka topics exist
* verify the Compose services are using `kafka:29092`

If the frontend cannot refresh data:

* verify `http://localhost:8001/health`
* verify the API base URL is `http://localhost:8001`
* inspect browser network failures

If location sync fails:

* verify browser location permission
* verify the frontend is opened from `http://localhost:3000` or HTTPS
* verify the location API is reachable and Kafka is healthy

If agent queries fail:

* verify `OPENAI_API_KEY` is set
* remember that agent endpoints are optional and not required for Kafka or persistence verification
