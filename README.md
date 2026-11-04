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

---

## Deploy on Render

Follow these steps to deploy Campus Commute as a Web Service on [Render](https://render.com):

1. **Create Web Service:**
   - Link this GitHub repository.
   - **Environment / Runtime:** `Docker` (Render automatically uses the multi-stage `Dockerfile` at the repository root).
   - **Region:** `Singapore` (closest low-latency region to Chennai, India).
   - **Instance Type:** `Free` (512 MB RAM, 0.1 CPU).
2. **Health Check Path:**
   - Set **Health Check Path** to: `/api/health`
3. **Configure Environment Variables:**
   Under the **Environment** tab, configure the following variables (use your private secrets; placeholders shown below):
   - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://<supabase-host>:5432/<database>?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME`: `postgres.<project-ref>`
   - `SPRING_DATASOURCE_PASSWORD`: `<your-supabase-db-password>`
   - `JWT_SECRET`: `<your-random-64-character-jwt-secret>`
   - `GOOGLE_ROUTES_API_KEY`: `<optional-google-routes-api-key>` (optional; straight-line haversine x 1.25 is used if omitted)
4. **Deploy:**
   - Click **Create Web Service**.
   - Render will build the Docker container image, execute `java -XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Duser.timezone=Asia/Kolkata -jar app.jar`, and monitor `/api/health` until healthy.


