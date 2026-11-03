# Campus Commute — System Architecture & Handoff Guide

> **Target Audience:** Developers, Reviewers, Evaluators, and Frontend Integrators.  
> **Status:** All 4 Modules (Module 1: Identity & Access, Module 2: Rides Lifecycle, Module 3: Concurrency-Safe Bookings, Module 4: Pricing, Settlement, WebSockets & OpenAPI) and Client Frontend (SPA) are **Completed and Verified**.  
> **Institution:** Easwari Engineering College (EEC), Ramapuram, Chennai, India.  

---

## 1. Project Overview & Multi-Module Structure

Campus Commute is a peer-to-peer carpooling and bike-pooling platform localized exclusively for verified students and faculty of **Easwari Engineering College, Chennai**. The backend is architected as a **single Spring Boot 3.2.5 application** running on **Java 17** with a cloud-hosted **Supabase PostgreSQL database** (with in-memory H2 fallback for test isolation), complemented by a single-page application (SPA).

> **SCOPE CLARIFICATION:**  
> The project covers peer-to-peer student carpooling and two-wheeler ride-sharing. **College bus tracking is NOT included in this project.**
> **PRICING POLICY:**  
> Fixed prices are **never displayed during ride listing or booking**. Fuel expenses are dynamically calculated by the system **only upon ride completion** based on actual distance, vehicle mileage, and the prevailing fuel rate, split equally among occupants.

### Module Breakdown & Ownership

| Module | Package | Scope & Responsibilities | Status |
| :--- | :--- | :--- | :--- |
| **Module 1** | `com.campuscommute.campuscommute.auth` & `...common` | Institutional Identity Verification, Walled-Garden Onboarding, Stateless JWT Security, Shared Exception Handling, Single Page Application | **COMPLETED & FROZEN** |
| **Module 2** | `com.campuscommute.campuscommute.rides` | Ride Lifecycle (`SCHEDULED` → `ONGOING` → `COMPLETED`/`CANCELLED`), Distance Calculation (Google Routes API / Haversine fallback), Route Filtering & Ownership | **COMPLETED & FROZEN** |
| **Module 3** | `com.campuscommute.campuscommute.bookings` | Concurrency-Safe Seat Reservation (Pessimistic Locking), Instant Bookings & Cancellations | **COMPLETED & FROZEN** |
| **Module 4** | `com.campuscommute.campuscommute.pricing` | Post-trip Dynamic Fuel Pricing, UPI Peer-to-Peer Settlement, STOMP/WebSocket Live Tracking (`/ws/**`) | **COMPLETED & FROZEN** |

---

## 2. Core Contracts & Data Model

### 2.1 Database Schema

#### `student_directory` (Static Institution Records — Read-Only)
Represents the verified student roster of Easwari Engineering College. Managed strictly via database seeds (`data.sql`).
```sql
CREATE TABLE student_directory (
    register_number VARCHAR(12) PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    department      VARCHAR(50) NOT NULL,
    year_of_study   INT NOT NULL
);
```

#### `users` (Active User Accounts)
Every registered user is linked to an institutional record. Roles are strictly `DRIVER` or `RIDER`.
```sql
CREATE TABLE users (
    user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    register_number VARCHAR(12) NOT NULL UNIQUE,
    phone_number    VARCHAR(15) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL, -- 'DRIVER' or 'RIDER'
    upi_id          VARCHAR(100),
    CONSTRAINT fk_users_student_directory FOREIGN KEY (register_number) 
        REFERENCES student_directory(register_number)
);
```

#### `rides` (Rides Offered)
Represents peer-to-peer carpool and bike-pool offers published by drivers.
```sql
CREATE TABLE rides (
    ride_id              BIGSERIAL PRIMARY KEY,
    driver_id            BIGINT NOT NULL,
    start_location       VARCHAR(255) NOT NULL,
    destination          VARCHAR(255) NOT NULL,
    vehicle_type         VARCHAR(50) NOT NULL, -- 'TWO_WHEELER', 'FOUR_WHEELER'
    vehicle_model        VARCHAR(100),
    vehicle_plate        VARCHAR(20),
    departure_time       TIMESTAMP,
    pickup_lat           DOUBLE PRECISION,
    pickup_lng           DOUBLE PRECISION,
    notes                VARCHAR(500),
    mileage_kmpl         NUMERIC(10, 2) NOT NULL,
    total_distance_km    NUMERIC(10, 2) NOT NULL,
    available_seats      INT NOT NULL,
    ride_status          VARCHAR(50) NOT NULL, -- 'SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'
    fuel_rate_per_litre  NUMERIC(10, 2),
    CONSTRAINT fk_rides_driver FOREIGN KEY (driver_id) REFERENCES users(user_id)
);
```

#### `bookings` (Seat Reservations)
```sql
CREATE TABLE bookings (
    booking_id           BIGSERIAL PRIMARY KEY,
    ride_id              BIGINT NOT NULL,
    passenger_id         BIGINT NOT NULL,
    seats_booked         INT NOT NULL DEFAULT 1,
    status               VARCHAR(50) NOT NULL, -- 'CONFIRMED', 'CANCELLED'
    created_at           TIMESTAMP,
    CONSTRAINT fk_bookings_ride FOREIGN KEY (ride_id) REFERENCES rides(ride_id),
    CONSTRAINT fk_bookings_passenger FOREIGN KEY (passenger_id) REFERENCES users(user_id)
);
```

---

## 3. Security & Authentication Context

All incoming requests to protected endpoints are filtered through `JwtAuthFilter`.

1. **Authenticated Principal:**
   - `Authentication.getPrincipal()` returns the authenticated user's **`Long userId`**.
   - In Spring MVC Controllers, inject the current user directly via `@AuthenticationPrincipal Long userId`.

2. **Granted Authorities:**
   - Security authorities are assigned as: `ROLE_DRIVER` or `ROLE_RIDER`.
   - Use `@PreAuthorize("hasAuthority('ROLE_DRIVER')")` or `@PreAuthorize("hasAuthority('ROLE_RIDER')")`.

3. **Open vs Protected Endpoints:**
   - Whitelisted without authentication:
     - `/api/auth/**` (Registration, Login, Token Refresh)
     - `/ws/**` (WebSocket connections for real-time tracking)
     - `/`, `/index.html`, `/static/**`, `/images/**`, `/favicon.ico`
     - `/swagger-ui/**`, `/v3/api-docs/**`
     - `/error`
   - **All other paths require:** `Authorization: Bearer <access_token>` in HTTP headers.

---

## 4. REST API Reference

### 4.1 Authentication (`/api/auth`)
- **`POST /api/auth/register`**
  - **Body:** `{"registerNumber": "310621104001", "phoneNumber": "9876543210", "password": "securePassword123"}`
  - **Behavior:** Verifies register number against `student_directory` (401 if missing). Checks uniqueness (409 if already registered). BCrypt hashes password, defaults role to `RIDER`. Returns access + refresh tokens.
- **`POST /api/auth/login`**
  - **Body:** `{"phoneNumber": "9876543210", "password": "securePassword123"}`
  - **Behavior:** Verifies credentials, returns access + refresh tokens.
- **`POST /api/auth/refresh`**
  - **Body:** `{"refreshToken": "<refresh_token>"}`
  - **Behavior:** Validates refresh token (rejects access tokens). Returns fresh access and refresh token pair containing the latest user role.

### 4.2 User Profile (`/api/users`)
- **`GET /api/users/me`**
  - **Headers:** `Authorization: Bearer <access_token>`
  - **Response:** Current user's profile (`userId`, `registerNumber`, `fullName`, `department`, `yearOfStudy`, `phoneNumber`, `role`, `upiId`).
- **`PATCH /api/users/me/role`**
  - **Body:** `{"role": "DRIVER"}` (or `"RIDER"`)
  - **Behavior:** Updates user role to `DRIVER` or `RIDER`. Client subsequently calls `/api/auth/refresh` to issue new JWT tokens.
- **`PATCH /api/users/me/upi`**
  - **Body:** `{"upiId": "driver@okaxis"}`
  - **Behavior:** Updates UPI ID for dynamic settlement links.

### 4.3 Rides Management (`/api/rides`)
- **`POST /api/rides`**
  - **Authorization:** `ROLE_DRIVER`
  - **Body:** `{"startLocation": "...", "vehicleType": "FOUR_WHEELER", "vehicleModel": "Swift", "vehiclePlate": "TN09AB1234", "departureTime": "2026-11-04T08:30:00", "pickupLat": 13.03, "pickupLng": 80.17, "mileageKmpl": 15.0, "availableSeats": 3, "notes": "..."}`
  - **Behavior:** Destination is always Easwari Engineering College (fixed coordinates). Server calculates distance (Google Routes API or Haversine × 1.25 fallback). Persists ride as `SCHEDULED`.
- **`GET /api/rides`**
  - **Query Params:** `?start=...` & `?destination=...` (optional filters)
  - **Response:** List of open `SCHEDULED` rides with available seats and future departure, excluding caller's own rides.
- **`GET /api/rides/mine`**
  - **Authorization:** `ROLE_DRIVER`
  - **Response:** All rides created by the current driver, ordered by departure time desc.
- **`GET /api/rides/{id}`**
  - **Response:** Ride details (`404 Not Found` if missing).
- **`PATCH /api/rides/{id}/status`**
  - **Authorization:** Driver of the ride
  - **Body:** `{"status": "ONGOING"}` (or `"COMPLETED"`, `"CANCELLED"`)
  - **Behavior:** On cancel, cascades cancellation to all confirmed bookings. On completion, snapshots the current petrol rate into `fuel_rate_per_litre`.
- **`DELETE /api/rides/{id}`**
  - **Authorization:** Driver of the ride
  - **Behavior:** Allowed only when there are no confirmed bookings (otherwise 409).
- **`GET /api/rides/{id}/bookings`**
  - **Authorization:** Driver of the ride
  - **Response:** List of confirmed passengers (name, department, phone number).

### 4.4 Bookings (`/api/bookings`)
- **`POST /api/bookings?rideId={id}`** (or JSON body `{"rideId": id}`)
  - **Authorization:** `ROLE_RIDER`
  - **Behavior:** Concurrency-safe seat reservation using pessimistic locking (`PESSIMISTIC_WRITE`). Blocks own-ride booking.
- **`GET /api/bookings/me`**
  - **Authorization:** `ROLE_RIDER`
  - **Response:** Current user's bookings with ride details and driver contact.
- **`PATCH /api/bookings/{id}/cancel`**
  - **Authorization:** Passenger of the booking
  - **Behavior:** Locks the ride, updates booking status to `CANCELLED`, and restores the available seat if the ride is still `SCHEDULED`.

### 4.5 Pricing & Live Tracking (`/api/rides` & `/ws`)
- **`GET /api/rides/{id}/pricing`**
  - **Authorization:** Driver or confirmed passenger of that ride
  - **Response:** Fuel pricing metrics (`totalDistanceKm`, `mileageKmpl`, `fuelRatePerLitre`, `totalFuelCost`).
- **`GET /api/rides/{id}/settlement`**
  - **Authorization:** Driver or confirmed passenger of that ride
  - **Response:** Equal share per occupant (`perPersonCost`), total occupants, and UPI payment link (`upi://pay?...`) or driver phone number.
- **WebSocket STOMP (`/ws`)**:
  - Connection: Handshake with `Authorization: Bearer <token>`.
  - Topic: `/topic/ride/{id}/location` (singular `ride`), subscribed by driver and confirmed passengers.
  - Driver publish destination: `/app/ride/{id}/location`. Updates are accepted only while ride is `ONGOING`.

---

## 5. Pre-Seeded Student Records (`student_directory`)

The database is pre-seeded with verified Easwari Engineering College student records in `src/main/resources/data.sql`:

| Register Number | Student Name | Department | Year of Study |
| :--- | :--- | :--- | :---: |
| `310621104001` | Kavitha R | Computer Science and Engineering | 3 |
| `310621104002` | Arun Kumar S | Information Technology | 4 |
| `310621104003` | Divya M | Mechanical Engineering | 2 |
| `310621104004` | Siddharth V | Electronics and Communication Engineering | 3 |
| `310621104005` | Pooja N | Artificial Intelligence and Data Science | 1 |
| `310621104006` | Rohan Balaji | Computer Science and Engineering | 4 |
| `310621104007` | Sneha Ramachandran | Civil Engineering | 3 |
| `310621104008` | Vigneshwaran K | Electrical and Electronics Engineering | 2 |
| `310621104009` | Ananya Krishnan | Information Technology | 3 |
| `310621104010` | Deepak Sundaram | Automobile Engineering | 4 |
| `310625104397` | Tamizholiyan | Computer Science and Engineering | 2 |
| `310625104423` | Varshini | Computer Science and Engineering | 2 |
| `310625104425` | Vedikaa S | Computer Science and Engineering | 2 |
| `310625104437` | Vijitha Basker | Computer Science and Engineering | 2 |

---

## 6. Running Instructions

- **Run Dev Server:**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
- **Execute Integration Tests:**
  ```powershell
  .\mvnw.cmd test
  ```
- **Access Web Application:**
  Open `http://localhost:8081` in your browser.
