# Parcel Tracker GPS

Real-time GPS parcel tracking: Spring Boot + PostgreSQL backend, browser-based customer and dispatch portals, and a mobile courier app.

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
mvn clean spring-boot:run
```

In Windows PowerShell, use `$env:NAME = 'value'` instead of `export` for each variable. All admin and courier passwords must be at least 12 characters. Keep the same terminal open so the backend inherits these variables. `TOMTOM_API_KEY` must be configured on the backend with TomTom Search and Routing access for address autocomplete and route-based quotes; keep it private and never put it in browser code. Addresses can still be entered manually when suggestions are unavailable, but route quotes require the key. For a managed PostgreSQL database, set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` instead; require TLS in the connection URL (for example, `?sslmode=require`). Flyway creates the schema on startup and Hibernate validates it. Existing MongoDB data is not automatically migrated.

**Installer (optional):** with JDK 17+, Maven and PostgreSQL running, the installer prompts for an admin username and password, a courier username (courier ID) and password, and an optional TomTom API key for address suggestions and route quotes. It creates the database role and database with a generated database password, builds the backend and writes a start script to `~/.parcel-tracker`. Usernames must differ and contain 1-255 letters, digits, dots, underscores or hyphens; passwords must contain 12-72 printable ASCII characters, including at least one non-space character. Password input is hidden, and credentials and the API key are kept in an owner-only file. On reruns, you can enter a new API key to add or replace the configured one, or press Enter to keep the current setting.

```bash
installer/install.sh              # Linux/macOS; optional argument: install directory
installer\install.ps1             # Windows PowerShell; also prompts for the PostgreSQL admin password
~/.parcel-tracker/start.sh        # start the backend (Windows: start.ps1)
```

Linux/macOS overrides: `PARCEL_PG_ADMIN`, `PARCEL_PG_HOST`, `PARCEL_DB_NAME`, `PARCEL_DB_USER`.

Open [http://localhost:8080](http://localhost:8080) and use **Create account** for the customer portal, or sign in with the admin account for the admin portal. Keep the backend and PostgreSQL running while using the site.

From the dispatch workspace, an administrator or dispatcher can select **New ad hoc delivery** to build a parcel from item tiles, adjust its dimensions and weight, calculate a driving route and estimate, and select a provisioned courier. The parcel is created and assigned in one request, appears in the dispatch queue and is offered to the courier for acceptance. Ad hoc parcels are not linked to a customer account. This creates a parcel, not a separate scheduled pickup request.

Administrators can choose driving-route per-kilometre pricing or configure fixed distance charges for straight-line pickup-to-delivery bands. Radius estimates use geocoded pickup and delivery addresses; the last band applies to all longer trips. Base, weight and same-day charges continue to apply. Payments remain simulated and no charge is collected.

## REST API
| Method | Path | Description |
|---|---|---|
| POST | `/api/parcels` | Create parcel (customer or admin authentication required) |
| POST | `/api/booking-route` | Calculate a TomTom driving route from `{origin, destination}` (public, rate-limited) |
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
| GET, PUT | `/api/admin/service-area` | Read or change the Melbourne metro delivery restriction (admin only) |
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

Courier status transitions are `CREATED → ACCEPTED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`; `FAILED` can be set from any active step. Existing `CREATED → PICKED_UP` assignments remain supported. A rejected request is unassigned and returns to the available `CREATED` pool. Completing delivery requires at least one signature, barcode or photo proof. Invalid transitions return 400 and a courier can only update parcels assigned to that courier ID. Courier endpoints require HTTP Basic authentication and only allow the authenticated courier to use their own courier ID. State-changing API requests also require the CSRF token from `GET /api/csrf`; the mobile client obtains and sends it automatically. Courier passwords are BCrypt-hashed in PostgreSQL. The bootstrap courier account is created from `PARCEL_BOOTSTRAP_COURIER_ID` and `PARCEL_BOOTSTRAP_PASSWORD` environment variables at startup, and its password is reset to the configured value if it differs (restart the backend after changing it); use a password of at least 12 characters and provision subsequent accounts through a trusted operator. Admin credentials are configured with `PARCEL_ADMIN_USERNAME` and `PARCEL_ADMIN_PASSWORD`; both must be set, and the password must contain at least 12 characters. The browser dispatch workspace accepts admins and dispatchers. Credentials are checked using BCrypt and API requests use HTTP Basic authentication. Customer parcel and pickup creation require customer or admin authentication, and new customer records are linked to the authenticated account. Customers can access only their own parcels and pickups. Admins and dispatchers can list and update parcels and assign couriers; deletion and direct location updates remain admin-only. Customer parcel creation discards client-supplied tracking numbers, status, courier, location and proof-of-delivery state. The WebSocket feed requires authentication; the `all` channel is available to admins and dispatchers, while parcel subscriptions also allow the owner or assigned courier. Browser portals poll for updates rather than subscribing to an unscoped live feed. Deploy behind HTTPS: Basic credentials are sent with each request. Parcel records and courier assignment data are stored in PostgreSQL.

WebSocket `/ws/tracking/{parcelId}` pushes `{parcelId, trackingNumber, status, location}` on location updates and status changes (the location is `null` for a status-only event). Connections require HTTP Basic authentication; `all` is available to admins and dispatchers, while per-parcel subscriptions also allow the owner or assigned courier.

## Browser customer and admin portals
The Spring Boot backend serves the website from `backend/src/main/resources/static/`; no separate frontend build or server is required. The site is split into separate pages that share `common.js`:

| Page | Audience | Purpose |
|------|----------|---------|
| `/` | Everyone | Landing page with an account-free parcel quote calculator, plus links to sign in or register |
| `/login.html` | Everyone | Sign in (customers and admins) or create a customer account; signed-in users are routed to their portal |
| `/customer.html` | Customers | Parcel builder with item selection, route calculation, live price estimate, order tracking, demo receipt and support request |
| `/book.html` | Customers | Redirects to the customer portal |
| `/admin.html` | Admins | Configure delivery pricing and create, reset or revoke dispatcher accounts |
| `/dispatch.html` | Admins and dispatchers | Parcel queue, fleet map, assignments, ad hoc deliveries and local support requests |
| `/admin-settings.html` | Admins | Redirects to the admin area |

Address suggestions are requested from the same-origin `/api/address-suggestions` endpoint and looked up through the TomTom Search API. Configure `TOMTOM_API_KEY` in the backend environment; the API key is never sent to the browser. Suggestions are optional; addresses can always be entered manually.

The homepage quote calculator accepts origin, destination and parcel weight and dimensions, then displays a route-based estimate without creating an account or booking a delivery. Public route lookups are limited to 10 requests per client address per minute. A quote is an estimate only; booking and customer tracking still require a customer account.

**Driving route:** set `TOMTOM_API_KEY` to a TomTom API key with access to the Search and Routing APIs (do not commit it), then restart the backend. In the parcel builder at `/customer.html` or the admin ad hoc delivery page, enter both addresses and select **Calculate driving route** to display the distance, driving time and route. Addresses are sent to TomTom for geocoding and routing; verify the matched route before booking. A successful route lookup is required to book through the parcel builder. The key stays on the server. Booking prices are demo estimates based on the calculated distance.

Administrators can update delivery estimate rates from **Admin settings** in the dispatch and ad hoc delivery pages. The saved rates are used for customer previews and newly created parcels; previously saved parcel estimates do not change. Defaults remain a $5 base fee, $1.20 per kilometer, $0.50 per kilogram and a 1.75 same-day multiplier.

Administrators can enable **Melbourne metro councils only** in the admin area. The default is unrestricted. When enabled, new customer and ad hoc parcels must have both addresses geocoded by TomTom as street addresses and checked against the 31 metropolitan local government areas in the Victorian Government's [Vicmap Admin LGA service](https://services-ap1.arcgis.com/P744lA0wf4LlBZ84/ArcGIS/rest/services/Vicmap_Admin/FeatureServer/9). Pickup requests and changes to a parcel's delivery address are checked too. The full council areas are included, including their rural portions. Existing deliveries are not changed. Verification requires `TOMTOM_API_KEY` and network access to TomTom and Vicmap; bookings are rejected if verification is unavailable. Address matching depends on TomTom and should be confirmed by the operator. Settings changes are recorded with the administrator and timestamp in `service_area_audit`. Boundary source: State of Victoria, Vicmap Admin, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

**Custom Australian coverage** combines selected whole states and territories, council areas and administrator-drawn polygons. An address inside any selected area is accepted; both pickup and delivery must qualify. Draw polygons by choosing **Draw area**, clicking at least three points on the map, and choosing **Finish area**. Remove individual polygons before saving. Only the Australian mainland and Tasmania are supported for drawn areas. The 2025 [ABS local government area dataset](https://geo.abs.gov.au/arcgis/rest/services/ASGS2025/LGA/MapServer/0) supplies state and council checks; its mesh-block boundaries are statistical approximations, not legal boundaries, and the ABS recommends them for statistical purposes only. Verify operational suitability and border addresses before relying on them. TomTom receives the address and the ABS service receives its coordinates; if required verification fails, the booking is rejected. Drawing uses OpenStreetMap tiles. Custom changes and the complete configuration are recorded in the existing audit table.

The council selector and settings validation use a bundled 2025 catalogue, so they do not depend on the ABS list service being online. Booking checks for selected states or councils still require the live ABS boundary service and fail closed if it is unavailable. The catalogue must be updated alongside any change to the ABS boundary edition. Source: Australian Bureau of Statistics, [ASGS Edition 3 LGA 2025 allocation file](https://www.abs.gov.au/statistics/standards/australian-statistical-geography-standard-asgs-edition-3/jul2021-jun2026/access-and-downloads/allocation-files/LGA_2025_AUST.xlsx), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), via the [BushfireReadyGPT processed summary](https://github.com/shuxiachai/BushfireReadyGPT/blob/0067f4fcabc0c4eb966d3f37433d159246cae451/data_australia/processed/asgs_allocations/lga_2025_summary.csv). The catalogue selects code, name and state and excludes non-spatial, zero-area entries; it is not endorsed by the ABS.

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

Customers can add, edit and delete saved addresses in their address book and select them for pickup or delivery when booking. Addresses are stored with their account and available across browsers. Administrators and dispatchers can open and manage a customer's address book using the customer's account email; customers cannot access another customer's entries. Saved addresses still require the normal route calculation and service-area checks when booking.

## Mobile Courier App (iOS and Android)
The Flutter project in `mobile/` provides courier sign-in, active deliveries, accept/reject and status actions, turn-by-turn navigation handoff, foreground GPS updates, barcode/photo/signature proof capture, and an encrypted offline action queue. The API credentials and encryption key are stored using the platform secure-storage plugin. See [mobile/README.md](mobile/README.md) for setup, native permission declarations, TLS configuration, and build guidance.

## Scheduled jobs
Backend jobs live in `com.parceltracker.scheduler`. Each job is idempotent, logs `start`/`end` with candidate, processed and error counts, and isolates per-parcel errors.

| Job | Purpose | Cron key (default) | Other keys (default) |
|---|---|---|---|
| Stale assigned escalation | Parcels still `CREATED` with a courier assigned for longer than the threshold get `escalated_at` set (once); optionally the courier is unassigned for reassignment | `parcel.scheduler.stale-assigned.cron` (`0 */10 * * * *`) | `.threshold` (`PT2H`), `.unassign` (`false`) |
| Courier location inactivity | `PICKED_UP`/`IN_TRANSIT`/`OUT_FOR_DELIVERY` parcels with no location update within the threshold get `location_inactive=true`; cleared when updates resume. Only saved when the flag changes | `parcel.scheduler.location-inactivity.cron` (`0 */5 * * * *`) | `.threshold` (`PT15M`) |
| Delivery auto-close | `DELIVERED` parcels older than the grace period get `finalized_at` set (once); `updated_at` is preserved | `parcel.scheduler.auto-close.cron` (`0 0 * * * *`) | `.grace-period` (`PT24H`) |

Thresholds are ISO-8601 durations. Age is measured from `updated_at`, falling back to `created_at`; parcels with neither are skipped. A parcel is processed when its age is greater than or equal to the threshold.

Disable locally with `-Dparcel.scheduler.enabled=false` (all jobs) or `-Dparcel.scheduler.<job>.enabled=false` where `<job>` is `stale-assigned`, `location-inactivity` or `auto-close`.
