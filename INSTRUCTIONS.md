# INSTRUCTIONS.md

## Purpose

This document is for an AI agent or developer who needs to verify the new Python adapters locally.

The current local test flow is:

1. start the Docker Compose stack
2. open the auth test page at `http://localhost:8000/auth/test`
3. log in to Plaid
4. run all pollers
5. confirm Kafka received the events
6. confirm PostgreSQL persisted the events

Old instructions for manually publishing six location events are obsolete for this workflow and should be ignored.

## Expected Services

From `docker-compose.yml`, the local stack exposes:

* auth API: `http://localhost:8000`
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

The Spring consumer persists them to these tables:

* `dev_logs`
* `transaction_logs`
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

One partition and replication factor `1` are sufficient for local development.

## Step 1: Start the Stack

From the repository root, run:

```bash
docker compose up --build
```

Wait until these services are reachable:

* `http://localhost:8000/health`
* `http://localhost:8080`
* `http://localhost:5050`

The `python-auth-api` container serves the auth page and runs the poller endpoints. The Spring consumer should also be running before verification starts.

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

Confirm that new messages exist after the pollers run.

At a minimum:

* GitHub events should appear in `saket.dev_activity`
* Plaid transaction events should appear in `saket.wallet`

If no new messages appear, inspect the auth page response for poller errors before moving on.

## Step 6: Verify PostgreSQL Persistence

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
select * from processed_events order by id desc;
```

Verification criteria:

* rows from GitHub events are present in `dev_logs`
* rows from Plaid events are present in `transaction_logs`
* consumed Kafka event ids are present in `processed_events`

## Success Condition

The local verification is successful when all of the following are true:

* Plaid authentication completed successfully
* `Run All Pollers` completed without errors
* Kafka UI shows new messages in `saket.dev_activity` and `saket.wallet`
* pgAdmin shows newly persisted rows in `dev_logs`, `transaction_logs`, and `processed_events`

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
