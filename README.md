# Campus Commute — Easwari Engineering College Carpool & Bike-pool

Peer-to-peer carpooling and bike-pooling platform localized exclusively for verified students of **Easwari Engineering College (EEC), Chennai**.

## Tech Stack
- **Backend:** Spring Boot 3.2.5, Java 17, Spring Data JPA, Spring Security + JJWT, WebSocket/STOMP
- **Database:** Supabase PostgreSQL (production / staging), H2 in-memory (local development / testing)
- **Frontend:** Single-page application (SPA) in `src/main/resources/static/index.html` with Leaflet / OpenStreetMap

---

## Environment Variables

| Variable | Required | Default (if unset) | Description |
| :--- | :--- | :--- | :--- |
| `JWT_SECRET` | Yes (or via `application-local.properties`) | None | 256-bit secret (Base64 or raw string) for signing HMAC-SHA256 JWT tokens. |
| `SPRING_DATASOURCE_URL` | No | `jdbc:h2:mem:campuscommute;DB_CLOSE_DELAY=-1;MODE=PostgreSQL` | JDBC connection URL (e.g. Supabase connection string). |
| `SPRING_DATASOURCE_USERNAME` | No | `sa` | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | No | (empty) | Database password. |
| `SPRING_DATASOURCE_DRIVER` | No | `org.h2.Driver` | JDBC driver class name (`org.postgresql.Driver` for Supabase). |
| `PORT` | No | `8081` | HTTP server port. |
| `GOOGLE_ROUTES_API_KEY` | No | (empty) | Optional Google Routes API key. When absent, server calculates distance using Haversine formula × 1.25. |

---

## Run Locally (Default: H2 in-memory DB)

For local development, an `application-local.properties` file (gitignored) provides a dev JWT secret automatically.

### On macOS / Linux
```bash
./mvnw spring-boot:run
```

### On Windows
```powershell
.\mvnw.cmd spring-boot:run
```

The application starts at `http://localhost:8081`.

---

## Run Against Supabase PostgreSQL

To connect to a managed Supabase PostgreSQL instance:

### PowerShell
```powershell
$env:JWT_SECRET="YourBase64Or256BitSecretHere"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://db.YOUR_PROJECT_REF.supabase.co:5432/postgres?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="YOUR_SUPABASE_PASSWORD"
$env:SPRING_DATASOURCE_DRIVER="org.postgresql.Driver"

.\mvnw.cmd spring-boot:run
```

### Bash
```bash
export JWT_SECRET="YourBase64Or256BitSecretHere"
export SPRING_DATASOURCE_URL="jdbc:postgresql://db.YOUR_PROJECT_REF.supabase.co:5432/postgres?sslmode=require"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="YOUR_SUPABASE_PASSWORD"
export SPRING_DATASOURCE_DRIVER="org.postgresql.Driver"

./mvnw spring-boot:run
```

---

## Running Automated Tests

Tests execute against an isolated in-memory H2 database using test credentials defined in `src/test/resources/application.properties`.

```bash
./mvnw test
# or on Windows:
.\mvnw.cmd test
```

