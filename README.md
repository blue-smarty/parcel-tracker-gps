# Parcel Tracker GPS

Real-time GPS parcel tracking: Spring Boot + MongoDB backend and a JavaFX desktop client.

## Run
Requires JDK 17+, Maven and a MongoDB instance (default `mongodb://localhost:27017/parceltracker`, see `backend/src/main/resources/application.properties`).

Start the backend and frontend in separate terminals, each opened at the repository root. Keep the backend running while using the frontend.

Terminal 1:
```
cd backend
mvn spring-boot:run
```

Terminal 2:
```
cd frontend
mvn clean javafx:run
```
(or from the repository root: `mvn -f frontend/pom.xml clean javafx:run`). MongoDB must be running on `localhost:27017` before starting the backend.
Use `-Dapi.url=<http-url>` and `-Dws.url=<websocket-url>` with `mvn javafx:run` to override the frontend's default server addresses (`http://localhost:8080` and `ws://localhost:8080`).

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
| PUT | `/api/couriers/{courierId}/deliveries/{parcelId}/status` | Advance status with `{status}` |
| POST | `/api/couriers/{courierId}/deliveries/{parcelId}/location` | Publish an assigned parcel GPS fix `{latitude, longitude, timestamp?}` |
| POST | `/api/pickups` | Request a pickup (201 with the created pickup, status `REQUESTED`; 400 with `{error, errors: {field: message}}` on validation failure) |
| GET | `/api/pickups/{id}` | Pickup details |

Courier status transitions are `CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`; `FAILED` can be set from any active step. Invalid transitions return 400 and a courier can only update parcels assigned to that courier ID. Assignment and courier endpoints use an ID for identification; this application does not currently provide authenticated user accounts. MongoDB stores the assignment as `courier_id` and automatically indexes it.

WebSocket `/ws/tracking/{parcelId}` pushes `{parcelId, trackingNumber, status, location}` on location updates and status changes (the location is `null` for a status-only event); use `all` as id to receive every parcel.

## Frontend
Parcel table with search, map with markers and route of the selected parcel, details and history panels, live updates with automatic reconnect.

### Courier Dashboard
Open **Courier Dashboard** and enter the courier ID assigned to a parcel to load active deliveries. Select a delivery to advance it through the available statuses or manually enter latitude and longitude and choose **Publish location**. Courier status and GPS changes are reflected in the existing tracking feed. Delivery assignment is available through `PUT /api/parcels/{id}/courier`; courier IDs are identifiers, not credentials, because authentication is not implemented.

### Schedule Pickup
Click **Schedule Pickup** in the top bar to open the pickup form: contact name, phone, pickup address (street, city, postal code), parcel description, weight (kg), optional dimensions, pickup date, time window and notes. Fields are validated inline and Submit stays disabled until the form is valid. Submission runs in the background with a progress indicator; on success the pickup ID and status are shown and the form is cleared, on failure the server's validation errors or a connection error are displayed. Reset clears the form and Cancel closes it. The backend URL is taken from `-Dapi.url` (default `http://localhost:8080`).

### Customer App (Non-production demo)
Choose **Customer App (Demo)** to save a local demo profile and create parcel orders with pickup/delivery addresses, package size, weight, delivery speed, scheduled time window, route distance and a demo payment option. Pricing is a server-calculated estimate using the entered distance, weight and delivery speed; it is not a route lookup or a binding quote. Orders appear in **Track & support**, where the existing GPS map and tracking feed show location/status updates. The window also offers a demo receipt, local-only rating and simulated support chat. Demo card/mobile wallet payments do not collect payment details or charge money; profile data is stored in local Java Preferences, not a customer account. No real email/phone/social verification, push service, payment provider, proof-of-delivery capture or support agent is connected.

## Scheduled jobs
Backend jobs live in `com.parceltracker.scheduler`. Each job is idempotent, logs `start`/`end` with candidate, processed and error counts, and isolates per-parcel errors.

| Job | Purpose | Cron key (default) | Other keys (default) |
|---|---|---|---|
| Stale assigned escalation | Parcels still `CREATED` with a courier assigned for longer than the threshold get `escalated_at` set (once); optionally the courier is unassigned for reassignment | `parcel.scheduler.stale-assigned.cron` (`0 */10 * * * *`) | `.threshold` (`PT2H`), `.unassign` (`false`) |
| Courier location inactivity | `PICKED_UP`/`IN_TRANSIT`/`OUT_FOR_DELIVERY` parcels with no location update within the threshold get `location_inactive=true`; cleared when updates resume. Only saved when the flag changes | `parcel.scheduler.location-inactivity.cron` (`0 */5 * * * *`) | `.threshold` (`PT15M`) |
| Delivery auto-close | `DELIVERED` parcels older than the grace period get `finalized_at` set (once); `updated_at` is preserved | `parcel.scheduler.auto-close.cron` (`0 0 * * * *`) | `.grace-period` (`PT24H`) |

Thresholds are ISO-8601 durations. Age is measured from `updated_at`, falling back to `created_at`; parcels with neither are skipped. A parcel is processed when its age is greater than or equal to the threshold.

Disable locally with `-Dparcel.scheduler.enabled=false` (all jobs) or `-Dparcel.scheduler.<job>.enabled=false` where `<job>` is `stale-assigned`, `location-inactivity` or `auto-close`.
