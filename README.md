# Comprehensive Productivity Tracker

A personal data pipeline that ingests events from APIs and devices into Kafka, consumes them with Spring Boot, and persists them into Postgres/PostGIS for later enrichment and analysis.

## Local Run

### Prerequisites

Install Docker and Docker Compose.

Before starting the stack, make sure the repository root `.env` contains the adapter settings you want to test:

* `GITHUB_USERNAME`
* `PLAID_CLIENT_ID`
* `PLAID_SECRET`
* `OPENAI_API_KEY`, if you want to use the natural-language database agent

You can get them from the .env.example if you dont want to create them yourself.

### Start The Stack

Run from the repository root:

```bash
docker compose up --build
```

This starts:

* auth API on `http://localhost:8000`
* location API on `http://localhost:8001`
* frontend on `http://localhost:3000`
* Kafka UI on `http://localhost:8080`
* pgAdmin on `http://localhost:5050`
* PostgreSQL on `localhost:5432`
* the Spring Kafka consumer

### Create Kafka Topics

If these topics are not already present, create them in Kafka UI:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`

For local development, one partition and replication factor `1` are enough.

## Frontend Location Flow

Open the frontend after the stack starts:

```text
http://localhost:3000
```

The frontend supports:

* viewing recent visits and their associated location, transaction, health, and dev events
* viewing known places
* asking an OpenAI-powered database agent natural-language questions about productivity, spending, visits, and commit activity
* sending the browser's current location to the location API every 2.5 minutes
* reverse-geocoding the browser coordinates into a place name before publishing

The default Location API Base URL is:

```text
http://localhost:8001
```

To test the frontend:

1. Start the Docker Compose stack.
2. Open `http://localhost:3000`.
3. Leave the Location API Base URL as `http://localhost:8001` unless you are testing a different host.
4. Click `Refresh Data` to load visits and known places from the location API.
5. Click `Start Location Sync`.
6. Allow browser location permission when prompted.
7. Confirm the page updates `Sync Status`, `Last Sent`, and `Resolved Place`.
8. Check Kafka UI for a new message on `saket.location`.
9. Check pgAdmin for location rows after the Spring consumer processes the event.

Browser geolocation only works from secure origins. `http://localhost:3000` is allowed by browsers for local testing. If you open the frontend from another machine using a LAN IP, refresh-only workflows can still work, but location sync usually requires HTTPS.

## Python Adapter Test Flow

The current end-to-end verification path for the Python adapters is:

1. Open `http://localhost:8000/auth/test`
2. Log in to Plaid with `Connect Plaid`
3. Run `Run All Pollers`
4. Check Kafka for published events
5. Check pgAdmin for persisted rows

If you only need a local sandbox Plaid item, `Seed Plaid Sandbox Item` is an acceptable shortcut.

### Auth Test Page

Open:

```text
http://localhost:8000/auth/test
```

The page exposes:

* `Connect Plaid`
* `Seed Plaid Sandbox Item`
* `Run All Pollers`

`Run All Pollers` executes both Python pollers:

* GitHub dev activity producer
* Plaid transaction producer

### Verify Kafka

Open Kafka UI at `http://localhost:8080` and confirm new messages are present in:

* `saket.dev_activity`
* `saket.wallet`
* `saket.location`, after testing the frontend location sync flow

### Verify PostgreSQL

Open pgAdmin at `http://localhost:5050` and sign in with:

* email: `admin@admin.com`
* password: `admin`

Use the configured server and enter the database password `pass` if prompted.

Then run:

```sql
select * from dev_logs order by id desc;
select * from transaction_logs order by id desc;
select * from location_logs order by id desc;
select * from visits order by id desc;
select * from known_places order by id desc;
select * from processed_events order by id desc;
```

You should see:

* GitHub commit activity in `dev_logs`
* Plaid transactions in `transaction_logs`
* browser-published location events in `location_logs`
* visit and known-place rows when the location workflow creates or updates them
* consumed Kafka event ids in `processed_events`

## Architecture

### Ingestion

Python adapters publish JSON events into Kafka topics such as `saket.dev_activity` and `saket.wallet`.

The separate location API accepts frontend location posts, publishes `saket.location` events to Kafka, and reads visit history directly from Postgres.

It also exposes an OpenAI agent endpoint that accepts natural-language questions, runs read-only SQL against Postgres/PostGIS through the OpenAI Agents SDK, and returns an analyzed answer to the frontend.

The Spring Boot consumer subscribes to Kafka and routes incoming events by topic and type using strategy classes.

### Enrichment

The consumer persists incoming events into relational tables and can associate them with visit context when location-based workflows are active.

### Storage

Postgres + PostGIS

Key tables:

* `dev_logs`
* `transaction_logs`
* `location_logs`
* `visits`
* `known_places`
* `processed_events`
