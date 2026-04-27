# Frontend

Static browser frontend for:

* viewing recent visits and their associated events
* viewing known places
* asking the OpenAI database agent natural-language questions
* sending the browser's current location to `location_api` every 2.5 minutes

## Run

### With Docker Compose

From the repository root:

```bash
docker compose up --build
```

After the container is running, edits in `frontend/` are bind-mounted into Nginx and should appear on refresh without rebuilding the image.

Then open:

```text
http://localhost:3000
```

### Without Docker
Serve the folder over HTTP so browser geolocation works:

```bash
cd frontend
python3 -m http.server 3000
```

Then open:

```text
http://localhost:3000
```

By default the app calls:

```text
http://localhost:8001
```

## Notes

* The app reverse-geocodes coordinates in the browser using OpenStreetMap Nominatim.
* Location sync starts when you click `Start Location Sync`.
* If you open the frontend from another computer over `http://<your-lan-ip>:3000`, the browser will usually block geolocation because that origin is not a secure context. In that setup:
  * `Refresh Data` can still work if the API base URL points to `http://<your-lan-ip>:8001`
  * `Start Location Sync` will not post location until the frontend is served from `https://...` or from `http://localhost` on the same machine as the browser
* The Flask location API now supports:
  * `GET /visits`
  * `GET /known-places`
  * `POST /locations`
  * `POST /agent/query`
