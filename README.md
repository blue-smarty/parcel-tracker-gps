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
export TOMTOM_API_KEY='your-tomtom-api-key'
cd backend
mvn spring-boot:run
```

In Windows PowerShell, use `$env:NAME = 'value'` instead of `export` for each variable. All admin and courier passwords must be at least 12 characters. Keep the same terminal open so the backend inherits these variables. `TOMTOM_API_KEY` is required for address autocomplete; keep it private and configure it on the backend, not in browser code. Addresses can still be entered manually when suggestions are unavailable. For a managed PostgreSQL database, set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` instead; require TLS in the connection URL (for example, `?sslmode=require`). Flyway creates the schema on startup and Hibernate validates it. Existing MongoDB data is not automatically migrated.

**Installer (optional):** with JDK 17+, Maven and PostgreSQL running, the installer prompts for an admin username and password, a courier username (courier ID) and password, and an optional TomTom API key for address suggestions. It creates the database role and database with a generated database password, builds the backend and writes a start script to `~/.parcel-tracker`. Usernames must differ and contain 1-255 letters, digits, dots, underscores or hyphens; passwords must contain 12-72 printable ASCII characters, including at least one non-space character. Password input is hidden, and credentials and the API key are kept in an owner-only file. On reruns, you can enter a new API key to add or replace the configured one, or press Enter to keep the current setting.

```bash
installer/install.sh              # Linux/macOS; optional argument: install directory
installer\install.ps1             # Windows PowerShell; also prompts for the PostgreSQL admin password
~/.parcel-tracker/start.sh        # start the backend (Windows: start.ps1)
```

Linux/macOS overrides: `PARCEL_PG_ADMIN`, `PARCEL_PG_HOST`, `PARCEL_DB_NAME`, `PARCEL_DB_USER`.

Open [http://localhost:8080](http://localhost:8080) and use **Create account** for the customer portal, or sign in with the admin account for the admin portal. Keep the backend and PostgreSQL running while using the site.

From the dispatch workspace, an administrator or dispatcher can select **New ad hoc delivery** to enter sender contact, pickup and delivery details and select a provisioned courier. The parcel is created and assigned in one request, appears in the dispatch queue and is offered to the courier for acceptance. Ad hoc parcels are not linked to a customer account. This creates a parcel, not a separate scheduled pickup request.

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
| POST | `/api/parcels` | Create parcel (customer or admin authentication required) |
| POST | `/api/parcels/adhoc` | Create a parcel and assign a provisioned courier (admin or dispatcher) |
| GET | `/api/parcels` | List parcels (admin or dispatcher) |
| GET | `/api/parcels/mine` | List the authenticated customer's parcels |
| GET | `/api/parcels/{id}` | Parcel details (owner, admin or dispatcher) |
| PUT | `/api/parcels/{id}` | Update parcel (admin or dispatcher) |
| DELETE | `/api/parcels/{id}` | Delete parcel (admin authentication required) |
| POST | `/api/parcels/{id}/location` | Add GPS fix `{latitude, longitude, timestamp?}` (admin authentication required) |
| GET | `/api/parcels/{id}/history` | Location history (owner, admin or dispatcher) |
| PUT | `/api/parcels/{id}/courier` | Assign a provisioned courier with `{courierId}` (admin or dispatcher) |
| GET | `/api/dispatch/couriers` | List courier IDs for dispatch (admin or dispatcher) |
| GET, POST | `/api/admin/dispatchers` | List or create dispatcher accounts (admin only) |
| PUT, DELETE | `/api/admin/dispatchers/{username}/password`, `/api/admin/dispatchers/{username}` | Reset password or revoke dispatcher account (admin only) |
| GET | `/api/admin/me` | Verify admin credentials |
| GET | `/api/couriers/{courierId}/deliveries` | List that courier's active assigned deliveries |
| PUT | `/api/couriers/{courierId}/deliveries/{parcelId}/decision` | Accept/reject a new assigned request with `{accepted}` |
| PUT | `/api/couriers/{courierId}/deliveries/{parcelId}/status` | Advance status with `{status}` |
| POST | `/api/couriers/{courierId}/deliveries/{parcelId}/location` | Publish an assigned parcel GPS fix `{latitude, longitude, timestamp?}` |
| POST | `/api/couriers/{courierId}/deliveries/{parcelId}/proof` | Save delivery proof and complete an out-for-delivery parcel |
| POST | `/api/pickups` | Request a pickup (201 with the created pickup, status `REQUESTED`; 400 with `{error, errors: {field: message}}` on validation failure) |
| GET | `/api/pickups/{id}` | Pickup details (owner or admin authentication required) |
| POST | `/api/auth/register` | Create a customer account `{name, email, password}` (201; 409 if the email is taken) |
| POST | `/api/auth/password-reset` | Request a customer password reset `{email}` (always returns the same accepted message when enabled) |
| POST | `/api/auth/password-reset/confirm` | Set a new password `{token, password}` using an unexpired reset link |
| GET | `/api/auth/me` | Verify Basic credentials; returns `{username, name, role}` |
| GET | `/api/csrf` | Obtain a CSRF request token; send it in `X-XSRF-TOKEN` for POST/PUT/DELETE requests |

Courier status transitions are `CREATED → ACCEPTED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`; `FAILED` can be set from any active step. Existing `CREATED → PICKED_UP` assignments remain supported. A rejected request is unassigned and returns to the available `CREATED` pool. Completing delivery requires at least one signature, barcode or photo proof. Invalid transitions return 400 and a courier can only update parcels assigned to that courier ID. Courier endpoints require HTTP Basic authentication and only allow the authenticated courier to use their own courier ID. State-changing API requests also require the CSRF token from `GET /api/csrf`; the Java and mobile clients obtain and send it automatically. Courier passwords are BCrypt-hashed in PostgreSQL. The bootstrap courier account is created from `PARCEL_BOOTSTRAP_COURIER_ID` and `PARCEL_BOOTSTRAP_PASSWORD` environment variables at startup, and its password is reset to the configured value if it differs (restart the backend after changing it); use a password of at least 12 characters and provision subsequent accounts through a trusted operator. Admin credentials are configured with `PARCEL_ADMIN_USERNAME` and `PARCEL_ADMIN_PASSWORD`; both must be set, and the password must contain at least 12 characters. The browser dispatch workspace accepts admins and dispatchers; the desktop dispatcher still requires admin sign-in. Credentials are checked using BCrypt and API requests use HTTP Basic authentication. Customer parcel and pickup creation require customer or admin authentication, and new customer records are linked to the authenticated account. Customers can access only their own parcels and pickups. Admins and dispatchers can list and update parcels and assign couriers; deletion and direct location updates remain admin-only. Customer parcel creation discards client-supplied tracking numbers, status, courier, location and proof-of-delivery state. The WebSocket feed requires authentication; the `all` channel is available to admins and dispatchers, while parcel subscriptions also allow the owner or assigned courier. Browser portals poll for updates rather than subscribing to an unscoped live feed. Deploy behind HTTPS: Basic credentials are sent with each request. Parcel records and courier assignment data are stored in PostgreSQL.

WebSocket `/ws/tracking/{parcelId}` pushes `{parcelId, trackingNumber, status, location}` on location updates and status changes (the location is `null` for a status-only event). Connections require HTTP Basic authentication; `all` is available to admins and dispatchers, while per-parcel subscriptions also allow the owner or assigned courier.

## Frontend
The tracker desktop client requires admin sign-in before it loads parcel data. It provides a parcel table with search, a location plot with route history, details, and live updates with automatic reconnect.

### Browser customer and admin portals
The Spring Boot backend serves the website from `backend/src/main/resources/static/`; no separate frontend build or server is required. The site is split into separate pages that share `common.js`:

| Page | Audience | Purpose |
|------|----------|---------|
| `/` | Everyone | Landing page with links to sign in or register |
| `/login.html` | Everyone | Sign in (customers and admins) or create a customer account; signed-in users are routed to their portal |
| `/customer.html` | Customers | Book deliveries with address autocomplete, demo price estimate, track orders on a live map, demo receipt and support request |
| `/admin.html` | Admins | Configure delivery pricing and create, reset or revoke dispatcher accounts |
| `/dispatch.html` | Admins and dispatchers | Parcel queue, fleet map, assignments, ad hoc deliveries and local support requests |
| `/admin-settings.html` | Admins | Redirects to the admin area |

Address suggestions are requested from the same-origin `/api/address-suggestions` endpoint and looked up through the TomTom Search API. Configure `TOMTOM_API_KEY` in the backend environment; the API key is never sent to the browser. Suggestions are optional; addresses can always be entered manually.

Administrators can update delivery estimate rates from **Admin settings** in the dispatch and ad hoc delivery pages. The saved rates are used for customer previews and newly created parcels; previously saved parcel estimates do not change. Defaults remain a $5 base fee, $1.20 per kilometer, $0.50 per kilogram and a 1.75 same-day multiplier.

The browser refreshes an expired CSRF token once when a write is rejected for that reason. If a save reports that the session is not authorized, sign out and sign back in with an administrator account; the browser must allow same-origin cookies.

Customers register with name, email and a password of 12-72 characters (`POST /api/auth/register`); accounts are stored in the `customer_accounts` table with BCrypt hashes and authenticate with HTTP Basic like couriers, dispatchers and admins. `GET /api/auth/me` verifies credentials and returns `{username, name, role}`, where role is `CUSTOMER`, `ADMIN`, `DISPATCHER` or `COURIER`. Admins are configured with `PARCEL_ADMIN_USERNAME`/`PARCEL_ADMIN_PASSWORD` and can create, reset and revoke dispatcher accounts in the admin area. Dispatcher usernames use 3-40 letters, digits, dots, dashes or underscores and are stored lowercase; passwords require at least 12 characters and at most 72 UTF-8 bytes. Dispatchers sign in at the same login page and are routed to their separate workspace; they cannot access pricing or account management APIs. Courier accounts sign in with the mobile app, not the website. The browser keeps the credential in `sessionStorage` (cleared when the tab closes). Each portal page verifies the role and redirects to the right page otherwise.

Customer password recovery is disabled by default. To enable it, configure an SMTP server and a public HTTPS URL for the reset page before setting `PARCEL_PASSWORD_RESET_ENABLED=true`:

```bash
export SPRING_MAIL_HOST=smtp.example.com
export SPRING_MAIL_PORT=587
export SPRING_MAIL_USERNAME='your-smtp-username'
export SPRING_MAIL_PASSWORD='your-smtp-password'
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
export SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
export PARCEL_PASSWORD_RESET_FROM='ParcelPilot <no-reply@example.com>'
export PARCEL_PASSWORD_RESET_URL='https://your-domain.example/forgot-password.html'
export PARCEL_PASSWORD_RESET_ENABLED=true
```

The request endpoint responds identically for existing and unknown accounts. Reset tokens are random, expire after 30 minutes, are stored only as hashes, and can be used once; a one-minute per-account resend cooldown applies. The reset page uses CSRF-protected requests, and reset links must point to the deployed HTTPS origin. Use SMTP credentials from a dedicated mail provider and store them as deployment secrets, not in source control.

Customer orders are linked to the signed-in customer in the backend and load across browsers; demo support requests and courier ID suggestions still use local storage. Dispatch assignment and parcel-wide listing require admin or dispatcher authentication. Pricing, receipt, and support features are demos. Do not expose this portal or backend to the public internet without production payment/support integrations.

### Courier Dashboard
Open **Courier Dashboard** and enter the courier ID and password to load active deliveries. New requests can be accepted or rejected. Select a delivery to advance it through the available statuses, publish latitude/longitude, or record a drawn recipient signature, barcode, drop-off note and optional photo before completing delivery. Photo uploads are limited to 4 MB. When the backend is unavailable, decisions, status changes, GPS fixes and proof submissions are queued in `~/.parcel-tracker/courier-updates.json` and retried in order the next time deliveries load. If delivery coordinates and a current GPS fix are available, **Optimize & navigate** orders stops with a nearest-neighbor distance heuristic and opens Google Maps for turn-by-turn navigation. The map app, not this desktop client, supplies live directions. Delivery assignment is available through `PUT /api/parcels/{id}/courier`.

### Mobile Courier App (iOS and Android)
The Flutter project in `mobile/` provides courier sign-in, active deliveries, accept/reject and status actions, turn-by-turn navigation handoff, foreground GPS updates, barcode/photo/signature proof capture, and an encrypted offline action queue. The API credentials and encryption key are stored using the platform secure-storage plugin. See [mobile/README.md](mobile/README.md) for setup, native permission declarations, TLS configuration, and build guidance.

### Schedule Pickup
Click **Schedule Pickup** in the top bar and sign in as a customer or admin to open the pickup form: contact name, phone, pickup address (street, city, postal code), parcel description, weight (kg), optional dimensions, pickup date, time window and notes. Fields are validated inline and Submit stays disabled until the form is valid. Submission runs in the background with a progress indicator; on success the pickup ID and status are shown and the form is cleared, on failure the server's validation errors or a connection error are displayed. Reset clears the form and Cancel closes it. The backend URL is taken from `-Dapi.url` (default `http://localhost:8080`).

### Customer App (Non-production desktop demo)
Choose **Customer App (Demo)** and sign in with a customer account created through the browser portal to create parcel orders with pickup/delivery addresses, optional destination coordinates, package size, weight, delivery speed, scheduled time window, route distance and a demo payment option. Orders are loaded from the authenticated account, not matched by a local profile. Pricing is a server-calculated estimate using the entered distance, weight and delivery speed; it is not a route lookup or binding quote. The window also offers a demo receipt, local-only rating and simulated support chat. Demo card/mobile wallet payments do not collect payment details or charge money. No push service, payment provider or support agent is connected.

### Admin & Dispatcher Command Center (Non-production desktop demo)
Open **Admin & Dispatcher** from the main window and sign in with the configured admin account to monitor active parcel locations on the live map, manually assign a selected parcel to a verified courier, or auto-dispatch new unassigned parcels to the currently least-loaded verified courier. The standalone dispatcher desktop client also requires admin sign-in. The fleet tab lets you save local demo courier records, attach local document files for review and mark document status. These driver records and document paths/review states remain in local Java Preferences; they are not backend fleet accounts. Estimated delivered value uses parcel price estimates, not collected revenue. The support tab lists and resolves support requests submitted from the Customer App demo; requests stay on this device and are not sent to a support agent. Stale-assignment escalations are shown separately from customer support requests. The map plots parcel GPS locations; it does not track a separate driver device location.

## Scheduled jobs
Backend jobs live in `com.parceltracker.scheduler`. Each job is idempotent, logs `start`/`end` with candidate, processed and error counts, and isolates per-parcel errors.

| Job | Purpose | Cron key (default) | Other keys (default) |
|---|---|---|---|
| Stale assigned escalation | Parcels still `CREATED` with a courier assigned for longer than the threshold get `escalated_at` set (once); optionally the courier is unassigned for reassignment | `parcel.scheduler.stale-assigned.cron` (`0 */10 * * * *`) | `.threshold` (`PT2H`), `.unassign` (`false`) |
| Courier location inactivity | `PICKED_UP`/`IN_TRANSIT`/`OUT_FOR_DELIVERY` parcels with no location update within the threshold get `location_inactive=true`; cleared when updates resume. Only saved when the flag changes | `parcel.scheduler.location-inactivity.cron` (`0 */5 * * * *`) | `.threshold` (`PT15M`) |
| Delivery auto-close | `DELIVERED` parcels older than the grace period get `finalized_at` set (once); `updated_at` is preserved | `parcel.scheduler.auto-close.cron` (`0 0 * * * *`) | `.grace-period` (`PT24H`) |

Thresholds are ISO-8601 durations. Age is measured from `updated_at`, falling back to `created_at`; parcels with neither are skipped. A parcel is processed when its age is greater than or equal to the threshold.

Disable locally with `-Dparcel.scheduler.enabled=false` (all jobs) or `-Dparcel.scheduler.<job>.enabled=false` where `<job>` is `stale-assigned`, `location-inactivity` or `auto-close`.
