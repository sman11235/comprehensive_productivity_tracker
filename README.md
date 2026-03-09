# Personal Foundry

A “personal Palantir Foundry” for my life: ingest events from devices + APIs into a unified Postgres/PostGIS data model, then enrich them with location intelligence + visit context.

## Run and Test
#### Prerequisites
Install docker and docker compose

#### Running
To run the app, type ```docker compose up --build```
It may take around 5 minutes for kafka to connect to my application due to its high retry time. 
Once it connects, requests from kafka will be processed immediately, so no worries.\\

Once the application has loaded (should be around 20 secs), navigate to http://localhost:8080 and http://localhost:5050.
These are the Kafka UI and PostgreSQL UI respectively. Open up the kafka UI and navigate to topic. Once there click create topic.
\\
In the topic name, type ```saket.location``` set ```time to retain``` to 12 hours and ```max size on disk``` to 1 GB. 
Leave ```Maximum message size in bytes``` blank.
Fill everything else with ```1``` and click create topic. \\\\

Next, click produce message and fill out the ```value``` portion with the following JSON, one by one, in order of listing.
```
{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:10:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } } 
```

```
{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:20:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }
```

```
{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:30:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }
```

```
{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:41:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }
```

```
{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T15:50:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 43.7501] } }, "attributes": { "provider": "apple-core-location" } }
```

```
{ "eventId": "evt-003", "deviceId": "iphone-15", "source": "ios-location", "type": "LOCATION_RECORDED", "op": "CREATE", "observedAt": "2026-03-09T15:20:00Z", "payload": { "timestamp": "2026-03-09T16:00:00Z", "deviceId": "iphone-15", "loc": { "type": "Point", "coord": [-84.3902, 33.7501] } }, "attributes": { "provider": "apple-core-location" } }
```

This represents a user who stays stationary for a while and walks off, and then comes back. Once you are done with that, navigate to
http://localhost:5050 in login to the pgAdmin interface with ```user: admin@admin.com, password: admin```. Open the servers tab, and
type in the password ```pass```. Open the databases tab, and right click on create script. Delete what ever is on the new tab, and 
paste ```select * from location_logs```. You will check the visit ids and it should read from the top, ```null, 1, 1, 1, 1, null```.
This represents the start (with no visit), the visit (the rows with ones), and the end of the visit (the last null).

## Architecture

### Ingestion

Producers publish JSON events into Kafka topics (saket.location, saket.dev_activity, saket.health, etc.)
A Spring Boot consumer subscribes and routes events by type using strategy beans (ITypeStrategy)

### Enrichment

Location events are aggregated into a sliding window to detect whether I’m stationary vs moving. 
A small user state machine tracks START → MOVING → VISITING
When VISITING, non-location events (dev / health / transactions) are linked to the current Visit

### Storage

Postgres + PostGIS
Key tables: location_logs, known_places, visits, dev_logs, health_logs, transaction_logs
