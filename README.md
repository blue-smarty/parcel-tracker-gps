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
mvn javafx:run
```
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

WebSocket `/ws/tracking/{parcelId}` pushes `{parcelId, trackingNumber, status, location}` on each update; use `all` as id to receive every parcel.

## Frontend
Parcel table with search, map with markers and route of the selected parcel, details and history panels, live updates with automatic reconnect.
