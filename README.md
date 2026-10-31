# Parcel Tracker GPS

Real-time GPS parcel tracking: Spring Boot + PostgreSQL backend, a browser-based customer and dispatch portal, and JavaFX/mobile courier clients.

## Run
Requires JDK 17+, Maven and PostgreSQL 16+. Install PostgreSQL directly on your computer (no Docker required), then start its service:

- **macOS:** install PostgreSQL 16 with `brew install postgresql@16`, then start it with `brew services start postgresql@16`.
- **Ubuntu/Debian:** install PostgreSQL with `sudo apt install postgresql`, then start it with `sudo systemctl enable --now postgresql`. Confirm the installed version is 16 or newer (`psql --version`).
- **Windows:** install PostgreSQL 16 or newer using the official installer at [postgresql.org/download/windows](https://www.postgresql.org/download/windows/). Keep the PostgreSQL command-line tools selected in the installer.

Create a dedicated database role and database. In a terminal, run `createuser -h localhost -U postgres --pwprompt --createdb parceltracker`, enter a strong database password when prompted, then run `createdb -h localhost -U postgres -O parceltracker parceltracker`. On Homebrew PostgreSQL, the administrator role is usually your macOS username instead of `postgres`; replace `postgres` in those commands with that role. On Linux, if local admin login requires the operating-system `postgres` account, prefix each command with `sudo -u postgres`.

In the same terminal that will run the backend, set the following values (replace the examples; don't commit real passwords):

```bash
export DATABASE_PASSWORD='your-database-password'
export PARCEL_ADMIN_USERNAME=admin
export PARCEL_ADMIN_PASSWORD='your-admin-password'
export PARCEL_BOOTSTRAP_COURIER_ID=courier-001
export PARCEL_BOOTSTRAP_PASSWORD='your-courier-password'
cd backend
mvn spring-boot:run
```

In Windows PowerShell, use `$env:NAME = 'value'` instead of `export` for each variable. All admin and courier passwords must be at least 12 characters. Keep the same terminal open so the backend inherits these variables. For a managed PostgreSQL database, set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` instead; require TLS in the connection URL (for example, `?sslmode=require`). Flyway creates the schema on startup and Hibernate validates it. Existing MongoDB data is not automatically migrated.

Open [http://localhost:8080](http://localhost:8080) for the customer and dispatch portal. Keep the backend and PostgreSQL running while using the site.

The parcel tracker and courier dashboards remain available as optional desktop clients. From `frontend`, choose one:
```
mvn javafx:run
mvn javafx:run -Dfrontend.mainClass=com.parceltracker.CourierApp
mvn javafx:run -Dfrontend.mainClass=com.parceltracker.DispatcherApp
```
Use `-Dapi.url=<http-url>` and `-Dws.url=<websocket-url>` to override desktop client server addresses (defaults: `http://localhost:8080` and `ws://localhost:8080`; courier uses only the API URL).

## REST API
| Method | Path | Description |
|---|---|---|
| POST | `/api/parcels` | Create parcel |
| GET | `/api/parcels` | List parcels |
| GET | `/api/parcels/{id}` | Parcel details |
| PUT | `/api/parcels/{id}` | Update parcel (admin authentication required) |
| DELETE | `/api/parcels/{id}` | Delete parcel (admin authentication required) |
| POST | `/api/parcels/{id}/location` | Add GPS fix `{latitude, longitude, timestamp?}` |
| GET | `/api/parcels/{id}/history` | Location history |
| PUT | `/api/parcels/{id}/courier` | Assign a courier with `{courierId}` (admin authentication required) |
| GET | `/api/admin/me` | Verify admin credentials |
| GET | `/api/couriers/{courierId}/deliveries` | List that courier's active assigned deliveries |
| PUT | `/api/couriers/{courierId}/deliveries/{parcelId}/decision` | Accept/reject a new assigned request with `{accepted}` |
| PUT | `/api/couriers/{courierId}/deliveries/{parcelId}/status` | Advance status with `{status}` |
| POST | `/api/couriers/{courierId}/deliveries/{parcelId}/location` | Publish an assigned parcel GPS fix `{latitude, longitude, timestamp?}` |
| POST | `/api/couriers/{courierId}/deliveries/{parcelId}/proof` | Save proof, tip and payout, and complete an out-for-delivery parcel |
| GET | `/api/couriers/{courierId}/earnings` | Summarize completed deliveries, recorded tips and payouts |
| POST | `/api/pickups` | Request a pickup (201 with the created pickup, status `REQUESTED`; 400 with `{error, errors: {field: message}}` on validation failure) |
| GET | `/api/pickups/{id}` | Pickup details |
| GET | `/api/csrf` | Obtain a CSRF request token; send it in `X-XSRF-TOKEN` for POST/PUT/DELETE requests |

Courier status transitions are `CREATED → ACCEPTED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`; `FAILED` can be set from any active step. Existing `CREATED → PICKED_UP` assignments remain supported. A rejected request is unassigned and returns to the available `CREATED` pool. Completing delivery requires at least one signature, barcode or photo proof. Invalid transitions return 400 and a courier can only update parcels assigned to that courier ID. Courier endpoints require HTTP Basic authentication and only allow the authenticated courier to use their own courier ID. State-changing API requests also require the CSRF token from `GET /api/csrf`; the Java and mobile clients obtain and send it automatically. Courier passwords are BCrypt-hashed in PostgreSQL. The bootstrap courier account is created once from `PARCEL_BOOTSTRAP_COURIER_ID` and `PARCEL_BOOTSTRAP_PASSWORD` environment variables; use a password of at least 12 characters and provision subsequent accounts through a trusted operator. Admin credentials are configured with `PARCEL_ADMIN_USERNAME` and `PARCEL_ADMIN_PASSWORD`; both must be set, and the password must contain at least 12 characters. The browser Dispatch tab and desktop dispatcher require admin sign-in; credentials are held only in memory. Admin credentials are checked using BCrypt and admin API requests use HTTP Basic authentication. Parcel updates, deletes and courier assignments require the admin role; customer parcel booking, parcel reads and location updates retain their public access behavior. Deploy behind HTTPS: Basic credentials are sent with each request. Parcel records and courier assignment data are stored in PostgreSQL.

WebSocket `/ws/tracking/{parcelId}` pushes `{parcelId, trackingNumber, status, location}` on location updates and status changes (the location is `null` for a status-only event); use `all` as id to receive every parcel.

## Frontend
The tracker desktop client provides a parcel table with search, a location plot with route history, details, and live updates with automatic reconnect.

### Browser customer and dispatch portal
The Spring Boot backend serves the website at `/` from `backend/src/main/resources/static/`; no separate frontend build or server is required. The Customer tab creates deliveries, shows a demo price estimate, tracks orders created in that browser, displays GPS/status updates, and provides a demo receipt and local support request. The Dispatch tab lists parcels, plots live GPS positions, shows active/unassigned/escalated metrics, supports manual assignment and least-active-load auto-dispatch, and resolves local support requests. Open the Dispatch tab from the same browser to see customer support requests saved there.

Customer tracking IDs, the demo profile, courier ID suggestions, and demo support requests use browser local storage. Customer tracking is therefore limited to orders created in that browser; there is no customer account or cross-device recovery. Dispatch assignment endpoints require admin authentication; parcel reads and customer APIs remain public, and the customer, pricing, receipt, and support features are demos. Do not expose this portal or backend to the public internet without production payment/support integrations.

### Courier Dashboard
Open **Courier Dashboard** and enter the courier ID and password to load active deliveries. New requests can be accepted or rejected. Select a delivery to advance it through the available statuses, publish latitude/longitude, or record a drawn recipient signature, barcode, drop-off note and optional photo before completing delivery. Photo uploads are limited to 4 MB. When the backend is unavailable, decisions, status changes, GPS fixes and proof submissions are queued in `~/.parcel-tracker/courier-updates.json` and retried in order the next time deliveries load. The earnings panel sums completed jobs and the tip/payout amounts entered with proof; these are demo records, not verified or paid amounts. If delivery coordinates and a current GPS fix are available, **Optimize & navigate** orders stops with a nearest-neighbor distance heuristic and opens Google Maps for turn-by-turn navigation. The map app, not this desktop client, supplies live directions. Delivery assignment is available through `PUT /api/parcels/{id}/courier`.

### Mobile Courier App (iOS and Android)
The Flutter project in `mobile/` provides courier sign-in, active deliveries, accept/reject and status actions, turn-by-turn navigation handoff, foreground GPS updates, barcode/photo/signature proof capture, and an encrypted offline action queue. The API credentials and encryption key are stored using the platform secure-storage plugin. See [mobile/README.md](mobile/README.md) for setup, native permission declarations, TLS configuration, and build guidance.

### Schedule Pickup
Click **Schedule Pickup** in the top bar to open the pickup form: contact name, phone, pickup address (street, city, postal code), parcel description, weight (kg), optional dimensions, pickup date, time window and notes. Fields are validated inline and Submit stays disabled until the form is valid. Submission runs in the background with a progress indicator; on success the pickup ID and status are shown and the form is cleared, on failure the server's validation errors or a connection error are displayed. Reset clears the form and Cancel closes it. The backend URL is taken from `-Dapi.url` (default `http://localhost:8080`).

### Customer App (Non-production desktop demo)
Choose **Customer App (Demo)** to save a local demo profile and create parcel orders with pickup/delivery addresses, optional destination coordinates, package size, weight, delivery speed, scheduled time window, route distance and a demo payment option. Pricing is a server-calculated estimate using the entered distance, weight and delivery speed; it is not a route lookup or a binding quote. Orders appear in **Track & support**, where the existing GPS map and tracking feed show location/status updates. The window also offers a demo receipt, local-only rating and simulated support chat. Demo card/mobile wallet payments do not collect payment details or charge money; profile data is stored in local Java Preferences, not a customer account. No real email/phone/social verification, push service, payment provider or support agent is connected.

### Admin & Dispatcher Command Center (Non-production desktop demo)
Open **Admin & Dispatcher** from the main window and sign in with the configured admin account to monitor active parcel locations on the live map, manually assign a selected parcel to a verified courier, or auto-dispatch new unassigned parcels to the currently least-loaded verified courier. The standalone dispatcher desktop client also requires admin sign-in. The fleet tab lets you save local demo courier records, attach local document files for review, mark document status, and record a commission percentage. These driver records, document paths/review states, and commission rates remain in local Java Preferences; they are not backend fleet accounts, and commissions do not affect real payouts. Estimated delivered value uses parcel price estimates, not collected revenue. The support tab lists and resolves support requests submitted from the Customer App demo; requests stay on this device and are not sent to a support agent. Stale-assignment escalations are shown separately from customer support requests. The map plots parcel GPS locations; it does not track a separate driver device location.

## Scheduled jobs
Backend jobs live in `com.parceltracker.scheduler`. Each job is idempotent, logs `start`/`end` with candidate, processed and error counts, and isolates per-parcel errors.

| Job | Purpose | Cron key (default) | Other keys (default) |
|---|---|---|---|
| Stale assigned escalation | Parcels still `CREATED` with a courier assigned for longer than the threshold get `escalated_at` set (once); optionally the courier is unassigned for reassignment | `parcel.scheduler.stale-assigned.cron` (`0 */10 * * * *`) | `.threshold` (`PT2H`), `.unassign` (`false`) |
| Courier location inactivity | `PICKED_UP`/`IN_TRANSIT`/`OUT_FOR_DELIVERY` parcels with no location update within the threshold get `location_inactive=true`; cleared when updates resume. Only saved when the flag changes | `parcel.scheduler.location-inactivity.cron` (`0 */5 * * * *`) | `.threshold` (`PT15M`) |
| Delivery auto-close | `DELIVERED` parcels older than the grace period get `finalized_at` set (once); `updated_at` is preserved | `parcel.scheduler.auto-close.cron` (`0 0 * * * *`) | `.grace-period` (`PT24H`) |

Thresholds are ISO-8601 durations. Age is measured from `updated_at`, falling back to `created_at`; parcels with neither are skipped. A parcel is processed when its age is greater than or equal to the threshold.

Disable locally with `-Dparcel.scheduler.enabled=false` (all jobs) or `-Dparcel.scheduler.<job>.enabled=false` where `<job>` is `stale-assigned`, `location-inactivity` or `auto-close`.
