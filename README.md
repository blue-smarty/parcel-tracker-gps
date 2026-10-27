# Parcel Tracker GPS

Real-time GPS parcel tracking: Spring Boot + MongoDB backend, a browser-based customer and dispatch portal, and JavaFX/mobile courier clients.

## Run
Requires JDK 17+, Maven and a MongoDB instance (default `mongodb://localhost:27017/parceltracker`, see `backend/src/main/resources/application.properties`).

Start the backend from the repository root:

```
cd backend
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) for the customer and dispatch portal. Keep the backend running while using the site. MongoDB must be running on `localhost:27017` before starting the backend.

The parcel tracker and courier dashboards remain available as optional desktop clients. From `frontend`, choose one:
```
mvn javafx:run
mvn javafx:run -Dfrontend.mainClass=com.parceltracker.CourierApp
```
Use `-Dapi.url=<http-url>` and `-Dws.url=<websocket-url>` to override desktop client server addresses (defaults: `http://localhost:8080` and `ws://localhost:8080`; courier uses only the API URL).

## REST API
| Method | Path | Description |
|---|---|---|
| POST | `/api/parcels` | Create parcel |
| GET | `/api/parcels` | List parcels |
| GET | `/api/parcels/{id}` | Parcel details |
| PUT | `/api/parcels/{id}` | Update parcel |
| DELETE | `/api/parcels/{id}` | Delete parcel |
| POST | `/api/parcels/{id}/location` | Add GPS fix `{latitude, longitude, timestamp?}` |
| GET | `/api/parcels/{id}/history` | Location history |
| PUT | `/api/parcels/{id}/courier` | Assign a courier with `{courierId}` |
| GET | `/api/couriers/{courierId}/deliveries` | List that courier's active assigned deliveries |
| PUT | `/api/couriers/{courierId}/deliveries/{parcelId}/decision` | Accept/reject a new assigned request with `{accepted}` |
| PUT | `/api/couriers/{courierId}/deliveries/{parcelId}/status` | Advance status with `{status}` |
| POST | `/api/couriers/{courierId}/deliveries/{parcelId}/location` | Publish an assigned parcel GPS fix `{latitude, longitude, timestamp?}` |
| POST | `/api/couriers/{courierId}/deliveries/{parcelId}/proof` | Save proof, tip and payout, and complete an out-for-delivery parcel |
| GET | `/api/couriers/{courierId}/earnings` | Summarize completed deliveries, recorded tips and payouts |
| POST | `/api/pickups` | Request a pickup (201 with the created pickup, status `REQUESTED`; 400 with `{error, errors: {field: message}}` on validation failure) |
| GET | `/api/pickups/{id}` | Pickup details |
| GET | `/api/csrf` | Obtain a CSRF request token; send it in `X-XSRF-TOKEN` for POST/PUT/DELETE requests |

Courier status transitions are `CREATED → ACCEPTED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`; `FAILED` can be set from any active step. Existing `CREATED → PICKED_UP` assignments remain supported. A rejected request is unassigned and returns to the available `CREATED` pool. Completing delivery requires at least one signature, barcode or photo proof. Invalid transitions return 400 and a courier can only update parcels assigned to that courier ID. Courier endpoints require HTTP Basic authentication and only allow the authenticated courier to use their own courier ID. State-changing API requests also require the CSRF token from `GET /api/csrf`; the Java and mobile clients obtain and send it automatically. Courier passwords are BCrypt-hashed in MongoDB. The bootstrap courier account is created once from `PARCEL_BOOTSTRAP_COURIER_ID` and `PARCEL_BOOTSTRAP_PASSWORD` environment variables; use a password of at least 12 characters and provision subsequent accounts through a trusted operator. Deploy behind HTTPS: Basic credentials are sent with each request. MongoDB stores parcel assignments as `courier_id` and automatically indexes them. Other parcel, dispatcher and customer APIs retain their existing access behavior and need separate role-based authorization before production.

WebSocket `/ws/tracking/{parcelId}` pushes `{parcelId, trackingNumber, status, location}` on location updates and status changes (the location is `null` for a status-only event); use `all` as id to receive every parcel.

## Frontend
The tracker desktop client provides a parcel table with search, a location plot with route history, details, and live updates with automatic reconnect.

### Browser customer and dispatch portal
The Spring Boot backend serves the website at `/` from `backend/src/main/resources/static/`; no separate frontend build or server is required. The Customer tab creates deliveries, shows a demo price estimate, tracks orders created in that browser, displays GPS/status updates, and provides a demo receipt and local support request. Address suggestions and reverse geocoding use the public Photon service with OpenStreetMap data; **Use my location** requests browser location permission and sends coordinates to Photon to find an address. Location and address fields can always be entered manually. The Dispatch tab lists parcels, plots live GPS positions, shows active/unassigned/escalated metrics, supports manual assignment and least-active-load auto-dispatch, and resolves local support requests. Open the Dispatch tab from the same browser to see customer support requests saved there.

Customer tracking IDs, the demo profile, courier ID suggestions, and demo support requests use browser local storage. Customer tracking is therefore limited to orders created in that browser; there is no customer account or cross-device recovery. Dispatch and parcel APIs are not authenticated, and the customer, pricing, receipt, and support features are demos. Do not expose this portal or backend to the public internet without adding role-based authorization and production payment/support integrations.

### Courier Dashboard
Open **Courier Dashboard** and enter the courier ID and password to load active deliveries. New requests can be accepted or rejected. Select a delivery to advance it through the available statuses, publish latitude/longitude, or record a drawn recipient signature, barcode, drop-off note and optional photo before completing delivery. Photo uploads are limited to 4 MB. When the backend is unavailable, decisions, status changes, GPS fixes and proof submissions are queued in `~/.parcel-tracker/courier-updates.json` and retried in order the next time deliveries load. The earnings panel sums completed jobs and the tip/payout amounts entered with proof; these are demo records, not verified or paid amounts. If delivery coordinates and a current GPS fix are available, **Optimize & navigate** orders stops with a nearest-neighbor distance heuristic and opens Google Maps for turn-by-turn navigation. The map app, not this desktop client, supplies live directions. Delivery assignment is available through `PUT /api/parcels/{id}/courier`.

### Mobile Courier App (iOS and Android)
The Flutter project in `mobile/` provides courier sign-in, active deliveries, accept/reject and status actions, turn-by-turn navigation handoff, foreground GPS updates, barcode/photo/signature proof capture, and an encrypted offline action queue. The API credentials and encryption key are stored using the platform secure-storage plugin. See [mobile/README.md](mobile/README.md) for setup, native permission declarations, TLS configuration, and build guidance.

### Schedule Pickup
Click **Schedule Pickup** in the top bar to open the pickup form: contact name, phone, pickup address (street, city, postal code), parcel description, weight (kg), optional dimensions, pickup date, time window and notes. Fields are validated inline and Submit stays disabled until the form is valid. Submission runs in the background with a progress indicator; on success the pickup ID and status are shown and the form is cleared, on failure the server's validation errors or a connection error are displayed. Reset clears the form and Cancel closes it. The backend URL is taken from `-Dapi.url` (default `http://localhost:8080`).

### Customer App (Non-production desktop demo)
Choose **Customer App (Demo)** to save a local demo profile and create parcel orders with pickup/delivery addresses, optional destination coordinates, package size, weight, delivery speed, scheduled time window, route distance and a demo payment option. Address suggestions are provided by Photon using OpenStreetMap data. **Use approximate location** looks up the desktop's network IP through ipapi.co, then reverse-geocodes the approximate coordinates with Photon; IP-based location can be inaccurate. Address text, search queries and location coordinates are sent to those providers when these features are used, and addresses can always be entered manually. Pricing is a server-calculated estimate using the entered distance, weight and delivery speed; it is not a route lookup or a binding quote. Orders appear in **Track & support**, where the existing GPS map and tracking feed show location/status updates. The window also offers a demo receipt, local-only rating and simulated support chat. Demo card/mobile wallet payments do not collect payment details or charge money; profile data is stored in local Java Preferences, not a customer account. No real email/phone/social verification, push service, payment provider or support agent is connected.

### Admin & Dispatcher Command Center (Non-production desktop demo)
Open **Admin & Dispatcher** from the main window to monitor active parcel locations on the live map, manually assign a selected parcel to a verified courier, or auto-dispatch new unassigned parcels to the currently least-loaded verified courier. The fleet tab lets you save local demo courier records, attach local document files for review, mark document status, and record a commission percentage. These driver records, document paths/review states, and commission rates remain in local Java Preferences; they are not backend fleet accounts, and commissions do not affect real payouts. Estimated delivered value uses parcel price estimates, not collected revenue. The support tab lists and resolves support requests submitted from the Customer App demo; requests stay on this device and are not sent to a support agent. Stale-assignment escalations are shown separately from customer support requests. The map plots parcel GPS locations; it does not track a separate driver device location. This application has no authenticated admin or dispatcher accounts.

## Scheduled jobs
Backend jobs live in `com.parceltracker.scheduler`. Each job is idempotent, logs `start`/`end` with candidate, processed and error counts, and isolates per-parcel errors.

| Job | Purpose | Cron key (default) | Other keys (default) |
|---|---|---|---|
| Stale assigned escalation | Parcels still `CREATED` with a courier assigned for longer than the threshold get `escalated_at` set (once); optionally the courier is unassigned for reassignment | `parcel.scheduler.stale-assigned.cron` (`0 */10 * * * *`) | `.threshold` (`PT2H`), `.unassign` (`false`) |
| Courier location inactivity | `PICKED_UP`/`IN_TRANSIT`/`OUT_FOR_DELIVERY` parcels with no location update within the threshold get `location_inactive=true`; cleared when updates resume. Only saved when the flag changes | `parcel.scheduler.location-inactivity.cron` (`0 */5 * * * *`) | `.threshold` (`PT15M`) |
| Delivery auto-close | `DELIVERED` parcels older than the grace period get `finalized_at` set (once); `updated_at` is preserved | `parcel.scheduler.auto-close.cron` (`0 0 * * * *`) | `.grace-period` (`PT24H`) |

Thresholds are ISO-8601 durations. Age is measured from `updated_at`, falling back to `created_at`; parcels with neither are skipped. A parcel is processed when its age is greater than or equal to the threshold.

Disable locally with `-Dparcel.scheduler.enabled=false` (all jobs) or `-Dparcel.scheduler.<job>.enabled=false` where `<job>` is `stale-assigned`, `location-inactivity` or `auto-close`.
