# Parcel Tracker GPS

Real-time GPS parcel tracking: Spring Boot + MongoDB backend and a JavaFX desktop client.

## Run
Requires JDK 17+, Maven and a MongoDB instance (default `mongodb://localhost:27017/parceltracker`, see `backend/src/main/resources/application.properties`).

```
cd backend && mvn spring-boot:run
cd frontend && mvn javafx:run      # -Dapi.url / -Dws.url to override server addresses
```

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

WebSocket `/ws/tracking/{parcelId}` pushes `{parcelId, trackingNumber, status, location}` on each update; use `all` as id to receive every parcel.

## Frontend
Parcel table with search, map with markers and route of the selected parcel, details and history panels, live updates with automatic reconnect.
