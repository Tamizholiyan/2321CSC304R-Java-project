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

To run the application connected to the Supabase PostgreSQL session pooler:

1. Copy the example configuration template at the repository root to create your private, gitignored properties file:
   ```bash
   cp application-supabase.properties.example application-supabase.properties
   # On Windows PowerShell:
   Copy-Item application-supabase.properties.example application-supabase.properties
   ```
2. Open `application-supabase.properties`, paste the Supabase database password (shared privately between teammates), and supply a 64+ character JWT secret. **Never commit `application-supabase.properties` to version control.**
3. Launch the application with the `supabase` Spring profile:
   - **PowerShell (Windows):**
     ```powershell
     ./mvnw spring-boot:run "-Dspring-boot.run.profiles=supabase"
     ```
     *(Note: Quotes around `"-Dspring-boot.run.profiles=supabase"` are mandatory in PowerShell to prevent argument splitting).*
   - **Bash (macOS / Linux):**
     ```bash
     ./mvnw spring-boot:run -Dspring-boot.run.profiles=supabase
     ```

---

## Running Automated Tests

Tests execute against an isolated in-memory H2 database using test credentials defined in `src/test/resources/application.properties`.

```bash
./mvnw test
# or on Windows:
.\mvnw.cmd test
```

