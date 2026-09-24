# Campus Commute — System Architecture & Handoff Guide

> **Target Audience:** AI Assistants and Developers implementing Modules 2, 3, and 4, and frontend integrations.  
> **Status:** Module 1 (Identity & Access + Shared Infrastructure) and Client Frontend (Single-Page Application) are **Completed, Verified, and Frozen**.  
> **Institution:** Easwari Engineering College (EEC), Ramapuram, Chennai, India.  

---

## 1. Project Overview & Multi-Module Structure

Campus Commute is a peer-to-peer carpooling and bike-pooling platform localized exclusively for verified students and faculty of **Easwari Engineering College, Chennai**. The backend is architected as a **single Spring Boot 3.2.5 application** running on **Java 17** with a shared **MySQL database (`java_project`)**, complemented by an intuitive, market-ready single-page application (SPA).

> **SCOPE CLARIFICATION:**  
> The project covers peer-to-peer student carpooling and two-wheeler ride-sharing. **College bus tracking is NOT included in this project.**
> **PRICING POLICY:**  
> Fixed prices are **never displayed during ride listing or booking**. Fuel expenses are dynamically calculated by the system **only upon ride completion** based on actual distance, vehicle mileage, and the prevailing fuel rate, split equally among occupants.

### Module Breakdown & Ownership

| Module | Package | Scope & Responsibilities | Status |
| :--- | :--- | :--- | :--- |
| **Module 1** | `com.campuscommute.campuscommute.auth` & `...common` | Institutional Identity Verification, Walled-Garden Onboarding, Stateless JWT Security, Shared Exception Handling, Unified Frontend SPA | **COMPLETED & FROZEN** |
| **Module 2** | `com.campuscommute.campuscommute.rides` | Ride Lifecycle (`SCHEDULED` → `ONGOING` → `COMPLETED`/`CANCELLED`), Route Matching, Dynamic Fuel Calculation Engine | *Ready to implement* |
| **Module 3** | `com.campuscommute.campuscommute.bookings` | Concurrency-Safe Seat Reservation (Pessimistic/Optimistic Locking), Booking Requests & Settlements | *Ready to implement* |
| **Module 4** | `com.campuscommute.campuscommute.coordination` | Real-time Trip Coordination, WebSocket (`/ws/**`) & Live Peer Notifications | *Ready to implement* |

---

## 2. Frozen Contracts (DO NOT BREAK)

### 2.1 Database Schema & Tables Owned by Module 1

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
Every registered user is linked to an institutional record.
```sql
CREATE TABLE users (
    user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    register_number VARCHAR(12) NOT NULL UNIQUE,
    phone_number    VARCHAR(15) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            ENUM('DRIVER', 'RIDER') NOT NULL,
    CONSTRAINT fk_users_student_directory FOREIGN KEY (register_number) 
        REFERENCES student_directory(register_number)
);
```
> **CRITICAL RULE FOR ALL MODULES:**  
> When referencing a driver or passenger in your tables (e.g. `rides.driver_id` or `bookings.passenger_id`), use `BIGINT` foreign keys referencing `users(user_id)`.

---

### 2.2 Security & Authentication Context

All incoming requests to protected endpoints are filtered through `JwtAuthFilter`.

1. **Authenticated Principal:**
   - `Authentication.getPrincipal()` returns the authenticated user's **`Long userId`**.
   - In Spring MVC Controllers, inject the current user directly via `@AuthenticationPrincipal Long userId`.
   - **Example:**
     ```java
     @PostMapping("/rides")
     public ResponseEntity<RideResponse> createRide(
             @AuthenticationPrincipal Long currentUserId,
             @Valid @RequestBody CreateRideRequest request
     ) {
         // currentUserId is the Long user_id of the authenticated student
     }
     ```

2. **Granted Authorities:**
   - Security authorities are assigned as: `ROLE_DRIVER` or `ROLE_RIDER`.
   - Use `@PreAuthorize` on controller or service methods:
     ```java
     @PreAuthorize("hasRole('DRIVER')")
     @PostMapping("/rides")
     public ResponseEntity<?> offerRide(...) { ... }
     ```

3. **Open vs Protected Endpoints:**
   - Whitelisted without authentication:
     - `/api/auth/**` (Registration, Login, Token Refresh)
     - `/ws/**` (WebSocket connections for Module 4 Real-time Coordination)
     - `/`, `/index.html`, `/static/**`, `/images/**`, `/favicon.ico`
     - `/error`
   - **All other paths require:** `Authorization: Bearer <access_token>` in HTTP headers.

---

### 2.3 Shared Exception Handling Skeleton (`common`)

All modules must return uniform JSON error responses using the `ApiError` record:

```java
package com.campuscommute.campuscommute.common;

public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}
```

#### How to Add Exceptions for Your Module:
In `com.campuscommute.campuscommute.common.GlobalExceptionHandler`, simply add an `@ExceptionHandler` method for your module's custom runtime exceptions:

```java
@ExceptionHandler(RideNotFoundException.class)
public ResponseEntity<ApiError> handleRideNotFound(
        RideNotFoundException ex, HttpServletRequest request) {
    ApiError error = ApiError.of(
            HttpStatus.NOT_FOUND.value(),
            HttpStatus.NOT_FOUND.getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI()
    );
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
}
```

---

### 2.4 Global Business Rules: Dynamic Pricing & Seat Booking

1. **Post-Completion Dynamic Fuel Pricing (Rule for Module 2 & 3):**
   - **Do NOT display fixed prices during ride listing or booking.**
   - Pricing is computed **only upon ride completion** via the system formula:
     ```
     total_fuel_cost = (total_distance_km / mileage_kmpl) * current_petrol_rate_per_litre
     per_person_share = total_fuel_cost / (confirmed_passengers + 1 driver)
     ```
   - Petrol rate must be a configurable application property (e.g. `campuscommute.pricing.petrol-rate-per-litre`), not a hardcoded literal.
   - Settlement: The API returns the exact per-person cost upon ride completion; actual payment is executed directly between peers (e.g., UPI).

2. **Concurrency-Safe Seat Booking (Rule for Module 3):**
   - Lock target `rides` row, verify `available_seats > 0`, create booking, atomically decrement `available_seats`, flip `ride_status` to `FULL` when seats reach zero — all inside one transaction.
   - Use pessimistic row lock (`SELECT ... FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)`) or optimistic locking (`@Version`).

---

## 3. Frontend Client Architecture (SPA)

The client is a zero-dependency, modern single-page application located in [src/main/resources/static/index.html](file:///c:/Users/inspe/Downloads/campus%20commute/src/main/resources/static/index.html) and served statically at the root URL `/`.

### 3.1 View Architecture & Navigation

The frontend is divided into two primary root views (`view-section`):
1. **Landing View (`#landingView`)**:
   - Hero section with quick role-aware action button (`#heroFindRidesBtn`).
   - 3 Pillar feature cards: Verified College Network, Fair Dynamic Fuel Cost, Direct Campus Routes.
   - Greener campus initiative banner.
2. **Dashboard View (`#dashboardView`)**:
   - Contains a persistent **Top Commuter Mode Banner (`#commuteModeBanner`)** showing active mode (`DRIVER MODE (Vehicle Owner)` vs `PASSENGER MODE`) with instant 1-click toggle button (`#btnSwitchMode`).
   - Contains an **Adaptive Sidebar (`#sidebarMenu`)** dynamically rendering role-appropriate navigation tabs:
     - **Driver Mode Tabs:**
       - 🚗 **Offer a Ride** (`#tabOfferRide` $\rightarrow$ `#subViewOfferRide`)
       - 📋 **My Offers & Requests** (`#tabMyOfferedRides` $\rightarrow$ `#subViewMyOfferedRides`)
       - 👤 **Profile & Role** (`#tabProfile` $\rightarrow$ `#subViewProfile`)
     - **Passenger Mode Tabs:**
       - 🔍 **Find Rides** (`#tabFindRides` $\rightarrow$ `#subViewFindRides`)
       - 🚗 **My Bookings** (`#tabMyRides` $\rightarrow$ `#subViewMyRides`)
       - 👤 **Profile & Role** (`#tabProfile` $\rightarrow$ `#subViewProfile`)

### 3.2 Role-Guarded Navigation Logic
- **Brand Click (`handleBrandClick`)**: Clicking the top-left "Campus Commute" brand when logged in routes the user straight to their active mode dashboard (`offerRide` for Driver, `findRides` for Passenger).
- **Tab Guard (`showDashboardTab`)**: Strictly prevents Driver mode from accidentally viewing passenger screens, automatically redirecting `findRides` requests to `offerRide` when in Driver mode.

### 3.3 Map & Geolocation Engine
- **Tile Layer**: Powered by OpenStreetMap (`https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`). Free, high-resolution, no blur, and zero API keys.
- **Teardrop Pins**: Replaced old capsule badges with vertical SVG teardrop map pins (`map-vertical-pin`, width 36px, height 48px, needle anchor at `[18, 48]`):
  - **Pickup Pin**: Green pin with white inner dot and floating `PICKUP` badge.
  - **EEC Drop Pin**: Red pin with white inner dot and floating `EEC DROP` badge.
  - Fully draggable with real-time Nominatim reverse-geocoding, address autofill, and dynamic route polylines.
- **Device GPS**: Hardware GPS lookup via `navigator.geolocation` with accuracy radius circle and smooth camera panning.
- **Nearby Vehicle Markers**: Visual car (blue) and bike (green) markers with popups linked directly to ride cards.

---

## 4. API Endpoints Provided by Module 1

### Authentication (`/api/auth`)
- **`POST /api/auth/register`**
  - **Body:** `{"registerNumber": "310621104001", "phoneNumber": "9876543210", "password": "securePassword123"}`
  - **Behavior:** Verifies against `student_directory` (401 if missing). Checks uniqueness (409 if already registered). BCrypt hashes password, defaults role to `RIDER`. Returns access + refresh tokens.
- **`POST /api/auth/login`**
  - **Body:** `{"phoneNumber": "9876543210", "password": "securePassword123"}`
  - **Behavior:** Verifies credentials, returns access + refresh tokens.
- **`POST /api/auth/refresh`**
  - **Body:** `{"refreshToken": "<refresh_token>"}`
  - **Behavior:** Validates refresh token (rejects access tokens). Returns fresh access and refresh token pair.

### User Profile (`/api/users`)
- **`GET /api/users/me`**
  - **Headers:** `Authorization: Bearer <access_token>`
  - **Response:** Current user's profile combined with static `student_directory` data (`userId`, `registerNumber`, `fullName`, `department`, `yearOfStudy`, `phoneNumber`, `role`).
- **`PATCH /api/users/me/role`**
  - **Headers:** `Authorization: Bearer <access_token>`
  - **Body:** `{"role": "DRIVER"}` (or `"RIDER"`)
  - **Response:** Updated profile reflecting the new role.

---

## 5. Specifications for Upcoming Modules

### Module 2: Rides Lifecycle & Route Matching (`com.campuscommute.campuscommute.rides`)

#### Recommended Entity: `Ride`
```java
@Entity
@Table(name = "rides")
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rideId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId; // FK to users.user_id

    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType; // CARPOOL, BIKEPOOL

    private String vehicleModel;       // e.g. "Honda City"
    private String vehiclePlateNumber;  // e.g. "TN 09 BX 4521"
    private Double mileageKmpl;         // e.g. 15.5

    private String originAddress;
    private Double originLatitude;
    private Double originLongitude;

    private String destinationAddress;
    private Double destinationLatitude;
    private Double destinationLongitude;

    private LocalDateTime departureTime;
    private Integer totalSeats;
    private Integer availableSeats;

    @Enumerated(EnumType.STRING)
    private RideStatus status; // SCHEDULED, ONGOING, COMPLETED, CANCELLED

    private Double completedDistanceKm; // Set upon ride completion
    private BigDecimal totalFuelCost;    // Set upon ride completion
    private BigDecimal perPersonCost;    // Set upon ride completion
}
```

#### Required Endpoints for Module 2:
- `POST /api/rides`: Create/publish a new ride (Driver only).
- `GET /api/rides`: List available rides (supports filtering by `vehicleType`, `origin`, and proximity).
- `GET /api/rides/me`: List rides offered by current driver.
- `PATCH /api/rides/{id}/status`: Transition ride status (`SCHEDULED` $\rightarrow$ `ONGOING` $\rightarrow$ `COMPLETED`).
- `POST /api/rides/{id}/complete`: Finalize ride, accept actual GPS odometer distance, calculate dynamic fuel cost, and return per-person split.

---

### Module 3: Concurrency-Safe Bookings & Settlements (`com.campuscommute.campuscommute.bookings`)

#### Recommended Entity: `Booking`
```java
@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId; // FK to users.user_id

    private Integer seatsBooked; // typically 1

    @Enumerated(EnumType.STRING)
    private BookingStatus status; // PENDING, CONFIRMED, REJECTED, CANCELLED

    private BigDecimal settlementAmount; // populated on ride completion
}
```

#### Required Endpoints for Module 3:
- `POST /api/rides/{rideId}/book`: Request a seat (uses pessimistic lock on `rides` row).
- `GET /api/bookings/my`: List bookings for current passenger.
- `DELETE /api/bookings/{bookingId}`: Cancel booking and restore available seat.
- `PATCH /api/bookings/{bookingId}/respond`: Driver approves or declines seat request.

---

### Module 4: Real-Time Coordination & WebSockets (`com.campuscommute.campuscommute.coordination`)

#### WebSocket Configuration (`/ws/**`):
- Endpoint already whitelisted in `SecurityConfig`.
- Use STOMP over SockJS:
  - Destination Prefix: `/app`
  - Broker Prefix: `/topic`, `/queue`
- **Topics:**
  - `/topic/rides/{rideId}/location`: Driver broadcasts live GPS coords.
  - `/queue/users/{userId}/notifications`: Direct alerts for booking confirmations, trip starts, and payment reminders.

---

## 6. Pre-Seeded Student Records (`student_directory`)

The database is pre-seeded with verified Easwari Engineering College student records in [src/main/resources/data.sql](file:///c:/Users/inspe/Downloads/campus%20commute/src/main/resources/data.sql):

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

---

## 7. Running & Testing Instructions

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
