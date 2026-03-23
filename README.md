# Comprehensive Productivity Tracker

A personal data pipeline that ingests events from APIs and devices into Kafka, consumes them with Spring Boot, and persists them into Postgres/PostGIS for later enrichment and analysis.

## Local Run

### Prerequisites

Install Docker and Docker Compose.

Before starting the stack, make sure the repository root `.env` contains the adapter settings you want to test:

* `GITHUB_USERNAME`
* `PLAID_CLIENT_ID`
* `PLAID_SECRET`

### Start The Stack

Run from the repository root:

```bash
docker compose up --build
```

This starts:

* auth API on `http://localhost:8000`
* Kafka UI on `http://localhost:8080`
* pgAdmin on `http://localhost:5050`
* PostgreSQL on `localhost:5432`
* the Spring Kafka consumer

### Create Kafka Topics

If these topics are not already present, create them in Kafka UI:

* `saket.dev_activity`
* `saket.wallet`

For local development, one partition and replication factor `1` are enough.

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

### Verify PostgreSQL

Open pgAdmin at `http://localhost:5050` and sign in with:

* email: `admin@admin.com`
* password: `admin`

Use the configured server and enter the database password `pass` if prompted.

Then run:

```sql
select * from dev_logs order by id desc;
select * from transaction_logs order by id desc;
select * from processed_events order by id desc;
```

You should see:

* GitHub commit activity in `dev_logs`
* Plaid transactions in `transaction_logs`
* consumed Kafka event ids in `processed_events`

## Architecture

### Ingestion

Python adapters publish JSON events into Kafka topics such as `saket.dev_activity` and `saket.wallet`.

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
