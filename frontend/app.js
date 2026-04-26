const LOCATION_SYNC_INTERVAL_MS = 150000;
const VISITS_LIMIT = 50;
const KNOWN_PLACES_LIMIT = 100;
const DEFAULT_API_BASE_URL = "http://localhost:8001";
const DEVICE_ID_STORAGE_KEY = "tracker_frontend_device_id";
const USER_ID_STORAGE_KEY = "tracker_frontend_user_id";
const API_BASE_STORAGE_KEY = "tracker_frontend_api_base_url";

const state = {
  locationIntervalId: null,
  isSyncing: false,
  lastResolvedPlace: "",
  lastSentAt: "",
};

const elements = {
  apiBaseUrl: document.querySelector("#apiBaseUrl"),
  userId: document.querySelector("#userId"),
  refreshButton: document.querySelector("#refreshButton"),
  startSyncButton: document.querySelector("#startSyncButton"),
  stopSyncButton: document.querySelector("#stopSyncButton"),
  deviceIdValue: document.querySelector("#deviceIdValue"),
  syncStatusValue: document.querySelector("#syncStatusValue"),
  lastSentValue: document.querySelector("#lastSentValue"),
  resolvedPlaceValue: document.querySelector("#resolvedPlaceValue"),
  visitsSummary: document.querySelector("#visitsSummary"),
  knownPlacesSummary: document.querySelector("#knownPlacesSummary"),
  visitsList: document.querySelector("#visitsList"),
  knownPlacesList: document.querySelector("#knownPlacesList"),
  messageBanner: document.querySelector("#messageBanner"),
  agentQueryInput: document.querySelector("#agentQueryInput"),
  runAgentQueryButton: document.querySelector("#runAgentQueryButton"),
  agentSummary: document.querySelector("#agentSummary"),
  agentAnswer: document.querySelector("#agentAnswer"),
  agentQueriesList: document.querySelector("#agentQueriesList"),
};

function geolocationRequiresSecureOrigin() {
  return !window.isSecureContext;
}

function geolocationSetupMessage() {
  const origin = window.location.origin;
  return `Location sync is unavailable from ${origin}. Browsers only allow geolocation on HTTPS or localhost. Use https for remote access, or open the frontend on the same machine with http://localhost:3000. Changing the API base URL alone will not bypass this browser restriction.`;
}

function getOrCreateDeviceId() {
  const existing = localStorage.getItem(DEVICE_ID_STORAGE_KEY);
  if (existing) {
    return existing;
  }

  const deviceId = `browser-${generateDeviceUuid()}`;
  localStorage.setItem(DEVICE_ID_STORAGE_KEY, deviceId);
  return deviceId;
}

function generateDeviceUuid() {
  const cryptoApi = window.crypto || window.msCrypto;
  if (cryptoApi && typeof cryptoApi.randomUUID === "function") {
    return cryptoApi.randomUUID();
  }

  const randomBytes = new Uint8Array(16);
  if (cryptoApi && typeof cryptoApi.getRandomValues === "function") {
    cryptoApi.getRandomValues(randomBytes);
  } else {
    for (let index = 0; index < randomBytes.length; index += 1) {
      randomBytes[index] = Math.floor(Math.random() * 256);
    }
  }

  randomBytes[6] = (randomBytes[6] & 15) | 64;
  randomBytes[8] = (randomBytes[8] & 63) | 128;

  let hex = "";
  for (let index = 0; index < randomBytes.length; index += 1) {
    const byteHex = randomBytes[index].toString(16);
    hex += byteHex.length === 1 ? `0${byteHex}` : byteHex;
  }
  return [
    hex.slice(0, 8),
    hex.slice(8, 12),
    hex.slice(12, 16),
    hex.slice(16, 20),
    hex.slice(20),
  ].join("-");
}

function getApiBaseUrl() {
  return elements.apiBaseUrl.value.trim().replace(/\/$/, "") || DEFAULT_API_BASE_URL;
}

function saveSettings() {
  localStorage.setItem(API_BASE_STORAGE_KEY, getApiBaseUrl());
  localStorage.setItem(USER_ID_STORAGE_KEY, elements.userId.value.trim());
}

function loadSettings() {
  elements.apiBaseUrl.value = localStorage.getItem(API_BASE_STORAGE_KEY) || DEFAULT_API_BASE_URL;
  elements.userId.value = localStorage.getItem(USER_ID_STORAGE_KEY) || "";
}

function showMessage(message) {
  elements.messageBanner.hidden = false;
  elements.messageBanner.textContent = message;
}

function clearMessage() {
  elements.messageBanner.hidden = true;
  elements.messageBanner.textContent = "";
}

function setSyncStatus(text) {
  elements.syncStatusValue.textContent = text;
}

function setResolvedPlace(text) {
  state.lastResolvedPlace = text;
  elements.resolvedPlaceValue.textContent = text || "-";
}

function setLastSent(isoTimestamp) {
  state.lastSentAt = isoTimestamp;
  elements.lastSentValue.textContent = isoTimestamp ? formatDateTime(isoTimestamp) : "Never";
}

function formatDateTime(value) {
  if (!value) {
    return "Unknown";
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatCoordinate(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "Unknown";
  }

  return Number(value).toFixed(5);
}

function escapeHtml(value) {
  return String(value ?? "")
    .split("&")
    .join("&amp;")
    .split("<")
    .join("&lt;")
    .split(">")
    .join("&gt;")
    .split('"')
    .join("&quot;")
    .split("'")
    .join("&#39;");
}

function renderEmptyState(target, message) {
  target.innerHTML = `<div class="empty-state">${escapeHtml(message)}</div>`;
}

function eventSummaryLabel(events) {
  return [
    `Locations ${events.locations.length}`,
    `Transactions ${events.transactions.length}`,
    `Health ${events.health.length}`,
    `Dev ${events.dev.length}`,
  ];
}

function summarizeEventItem(type, event) {
  if (type === "locations") {
    return `${formatDateTime(event.timestamp)} • ${event.location_name || "Unnamed place"}`;
  }
  if (type === "transactions") {
    return `${formatDateTime(event.timestamp)} • ${event.category || "Uncategorized"} • $${event.amount ?? "0.00"}`;
  }
  if (type === "health") {
    return `${formatDateTime(event.timestamp)} • ${event.metric_type}: ${event.val} ${event.unit}`;
  }
  return `${formatDateTime(event.timestamp)} • ${event.platform}/${event.action_type} • ${event.target}`;
}

function renderKnownPlaces(knownPlaces) {
  elements.knownPlacesSummary.textContent = `${knownPlaces.length} places`;
  if (!knownPlaces.length) {
    renderEmptyState(elements.knownPlacesList, "No known places have been created yet.");
    return;
  }

  elements.knownPlacesList.innerHTML = knownPlaces
    .map(
      (place) => `
        <article class="place-card">
          <div class="visit-header">
            <h3>${escapeHtml(place.name || "Unnamed place")}</h3>
            <span class="pill">${escapeHtml(place.status || "unknown")}</span>
          </div>
          <p class="place-meta">${escapeHtml(place.category || "No category")}</p>
          <p class="place-coordinates">
            ${formatCoordinate(place.latitude)}, ${formatCoordinate(place.longitude)}
          </p>
          <div class="place-stats">
            <span>${place.visit_count || 0} visits</span>
            <span>Created ${formatDateTime(place.created_at)}</span>
            <span>Last visit ${place.last_visit_at ? formatDateTime(place.last_visit_at) : "Never"}</span>
          </div>
        </article>
      `,
    )
    .join("");
}

function renderVisits(visits) {
  elements.visitsSummary.textContent = `${visits.length} recent visits`;
  if (!visits.length) {
    renderEmptyState(elements.visitsList, "No visits found.");
    return;
  }

  elements.visitsList.innerHTML = visits
    .map((visit) => {
      const placeName = visit.place?.name || "Unknown place";
      const placeMeta = [visit.place?.category, visit.place?.status].filter(Boolean).join(" • ");
      const eventSections = ["locations", "transactions", "health", "dev"]
        .map((type) => {
          const items = visit.events[type];
          return `
            <section class="event-group">
              <h4>${escapeHtml(type)}</h4>
              ${
                items.length
                  ? `<ul>${items
                      .slice(0, 4)
                      .map((event) => `<li>${escapeHtml(summarizeEventItem(type, event))}</li>`)
                      .join("")}</ul>`
                  : `<p class="event-empty">No ${escapeHtml(type)} events.</p>`
              }
            </section>
          `;
        })
        .join("");

      return `
        <article class="visit-card">
          <div class="visit-header">
            <div>
              <h3>Visit #${visit.id}</h3>
              <p class="visit-place">${escapeHtml(placeName)}</p>
              <p class="place-meta">${escapeHtml(placeMeta || "No place metadata")}</p>
            </div>
            <span class="pill">${visit.exitTime ? "Closed" : "Active"}</span>
          </div>
          <p class="visit-times">
            ${formatDateTime(visit.entryTime)} to ${visit.exitTime ? formatDateTime(visit.exitTime) : "Present"}
          </p>
          <div class="event-counts">
            ${eventSummaryLabel(visit.events).map((label) => `<span>${escapeHtml(label)}</span>`).join("")}
          </div>
          <div class="event-groups">${eventSections}</div>
        </article>
      `;
    })
    .join("");
}

async function fetchJson(path) {
  const response = await fetch(`${getApiBaseUrl()}${path}`);
  const data = await response.json();
  if (!response.ok || !data.ok) {
    throw new Error(data.error || `Request failed: ${response.status}`);
  }
  return data;
}

async function fetchApiJson(path, options = {}) {
  const response = await fetch(`${getApiBaseUrl()}${path}`, options);
  const data = await response.json();
  if (!response.ok || !data.ok) {
    throw new Error(data.error || `Request failed: ${response.status}`);
  }
  return data;
}

async function refreshData() {
  clearMessage();
  elements.visitsSummary.textContent = "Loading...";
  elements.knownPlacesSummary.textContent = "Loading...";

  try {
    const [visitsResponse, knownPlacesResponse] = await Promise.all([
      fetchJson(`/visits?limit=${VISITS_LIMIT}`),
      fetchJson(`/known-places?limit=${KNOWN_PLACES_LIMIT}`),
    ]);
    renderVisits(visitsResponse.visits);
    renderKnownPlaces(knownPlacesResponse.knownPlaces);
  } catch (error) {
    renderEmptyState(elements.visitsList, "Unable to load visits.");
    renderEmptyState(elements.knownPlacesList, "Unable to load known places.");
    showMessage(error.message);
  }
}

async function reverseGeocode(latitude, longitude) {
  const url = new URL("https://nominatim.openstreetmap.org/reverse");
  url.searchParams.set("format", "jsonv2");
  url.searchParams.set("lat", String(latitude));
  url.searchParams.set("lon", String(longitude));

  const response = await fetch(url.toString(), {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Reverse geocoding failed: ${response.status}`);
  }

  const data = await response.json();
  return data.name || data.display_name || `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`;
}

function getCurrentPosition() {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 20000,
      maximumAge: 30000,
    });
  });
}

function describeGeolocationError(error) {
  if (!error) {
    return "Unable to acquire your current position.";
  }

  if (error.code === 1) {
    return "Location access was denied. Enable browser location permission and try again.";
  }

  if (error.code === 2) {
    return "Your position is currently unavailable. Check location services and try again.";
  }

  if (error.code === 3) {
    return "Timed out while acquiring your position. Try again in an area with better signal.";
  }

  if (geolocationRequiresSecureOrigin()) {
    return geolocationSetupMessage();
  }

  if (error.message) {
    return `Unable to acquire your current position: ${error.message}`;
  }

  return "Unable to acquire your current position.";
}

async function sendCurrentLocation() {
  if (!navigator.geolocation) {
    throw new Error("This browser does not support geolocation.");
  }

  if (geolocationRequiresSecureOrigin()) {
    throw new Error(geolocationSetupMessage());
  }

  setSyncStatus("Resolving current position");
  let position;
  try {
    position = await getCurrentPosition();
  } catch (error) {
    throw new Error(describeGeolocationError(error));
  }
  const latitude = position.coords.latitude;
  const longitude = position.coords.longitude;

  setSyncStatus("Reverse geocoding place name");
  let placeName = `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`;
  try {
    placeName = await reverseGeocode(latitude, longitude);
  } catch (error) {
    showMessage(`Location sent with coordinate fallback because place lookup failed: ${error.message}`);
  }
  setResolvedPlace(placeName);

  setSyncStatus("Posting to location API");
  const response = await fetch(`${getApiBaseUrl()}/locations`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      deviceId: getOrCreateDeviceId(),
      userId: elements.userId.value.trim() || null,
      latitude,
      longitude,
      locationName: placeName,
      observedAt: new Date().toISOString(),
    }),
  });

  const data = await response.json();
  if (!response.ok || !data.ok) {
    throw new Error(data.error || `Location post failed: ${response.status}`);
  }

  setLastSent(data.observedAt || new Date().toISOString());
  setSyncStatus("Running");
  clearMessage();
  await refreshData();
}

async function runSyncCycle() {
  try {
    await sendCurrentLocation();
  } catch (error) {
    setSyncStatus("Error");
    showMessage(error.message);
  }
}

function stopLocationSync() {
  if (state.locationIntervalId !== null) {
    clearInterval(state.locationIntervalId);
    state.locationIntervalId = null;
  }
  state.isSyncing = false;
  setSyncStatus("Stopped");
}

async function startLocationSync() {
  saveSettings();
  if (state.isSyncing) {
    await runSyncCycle();
    return;
  }

  setSyncStatus("Starting");
  try {
    await sendCurrentLocation();
  } catch (error) {
    state.isSyncing = false;
    state.locationIntervalId = null;
    setSyncStatus("Error");
    showMessage(error.message);
    return;
  }

  state.isSyncing = true;
  state.locationIntervalId = window.setInterval(runSyncCycle, LOCATION_SYNC_INTERVAL_MS);
}

function browserTimezone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || "America/New_York";
}

function renderAgentQueries(queries) {
  if (!queries.length) {
    renderEmptyState(elements.agentQueriesList, "No SQL queries were recorded for this answer.");
    return;
  }

  elements.agentQueriesList.innerHTML = queries
    .map(
      (query, index) => `
        <article class="agent-query-card">
          <h3>Query ${index + 1}</h3>
          <p>${escapeHtml(query.reason || "No reason provided")} • ${escapeHtml(String(query.rowCount || 0))} rows${
            query.truncated ? " (truncated)" : ""
          }</p>
          <pre>${escapeHtml(query.sql || "")}</pre>
        </article>
      `,
    )
    .join("");
}

function renderAgentResult(result) {
  elements.agentSummary.textContent = `${result.queryCount} SQL quer${result.queryCount === 1 ? "y" : "ies"} • ${result.timezone}`;
  elements.agentAnswer.textContent = result.answer || "No answer returned.";
  elements.agentAnswer.classList.remove("empty-state");
  renderAgentQueries(result.queries || []);
}

async function runAgentQuery() {
  const query = elements.agentQueryInput.value.trim();
  if (!query) {
    showMessage("Enter a question for the database agent.");
    return;
  }

  saveSettings();
  clearMessage();
  elements.runAgentQueryButton.disabled = true;
  elements.agentSummary.textContent = "Running agent...";
  elements.agentAnswer.textContent = "Analyzing your database activity...";
  elements.agentAnswer.classList.remove("empty-state");
  renderEmptyState(elements.agentQueriesList, "Waiting for SQL queries...");

  try {
    const result = await fetchApiJson("/agent/query", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        query,
        timezone: browserTimezone(),
        userId: elements.userId.value.trim() || null,
      }),
    });
    renderAgentResult(result);
  } catch (error) {
    elements.agentSummary.textContent = "Agent request failed";
    elements.agentAnswer.textContent = "Unable to generate an answer.";
    elements.agentAnswer.classList.add("empty-state");
    renderEmptyState(elements.agentQueriesList, "No SQL queries to show.");
    showMessage(error.message);
  } finally {
    elements.runAgentQueryButton.disabled = false;
  }
}

function bindEvents() {
  elements.refreshButton.addEventListener("click", async () => {
    saveSettings();
    await refreshData();
  });

  elements.startSyncButton.addEventListener("click", async () => {
    await startLocationSync();
  });

  elements.stopSyncButton.addEventListener("click", () => {
    stopLocationSync();
  });

  elements.runAgentQueryButton.addEventListener("click", async () => {
    await runAgentQuery();
  });

  elements.apiBaseUrl.addEventListener("change", saveSettings);
  elements.userId.addEventListener("change", saveSettings);
}

function init() {
  loadSettings();
  elements.deviceIdValue.textContent = getOrCreateDeviceId();
  setResolvedPlace("");
  setLastSent("");
  setSyncStatus("Stopped");
  elements.agentQueryInput.value = "On what days and times am I most productive?";
  bindEvents();
  if (geolocationRequiresSecureOrigin()) {
    showMessage(geolocationSetupMessage());
  }
  refreshData();
}

init();
