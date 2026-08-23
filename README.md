# Booking Platform

A generic, multi-tenant booking & appointment management SaaS.

One deployment serves many businesses — hair salons, pilates studios, dentists,
photographers, room rentals — through **configuration**, not per-industry code.
The domain is deliberately generic:

```
Service · Staff · Resource · Working Hours · Blocked Time · Customer · Booking
```

| Business type | Service            | Staff | Resource     |
|---------------|--------------------|-------|--------------|
| Hair salon    | Haircut            | Ana   | –            |
| Pilates       | Reformer Pilates   | Ana   | Reformer 1   |
| Room rental   | Meeting Room       | –     | Room A       |

The same availability engine and booking pipeline handle all three. Which of
staff/resource a booking needs is two boolean columns on the service, not a
branch in the code.

---

## Stack

**Backend** — Java 21 · Spring Boot 3.3 · Spring Web / Security / Data JPA ·
PostgreSQL 16 · Flyway · JWT · Maven (modular monolith, no microservices)

**Frontend** — React 18 · TypeScript · Vite · React Router · TanStack Query ·
Axios · Material UI · React Hook Form · Zod

---

## Repository layout

```
booking-platform/
├── backend/                 Spring Boot modular monolith
│   └── src/main/java/com/bookingsaas/
│       ├── auth/            registration, login, JWT, principals
│       ├── business/        profile, settings, members, opening hours
│       ├── service/         bookable services (duration, price, buffers)
│       ├── staff/           staff, staff↔service, staff working hours
│       ├── resource/        bookable resources, resource↔service
│       ├── customer/        per-business customer records
│       ├── booking/         booking lifecycle, conflict prevention, dashboard
│       ├── availability/    the availability engine, blocked time
│       ├── notification/    channel-agnostic outbox + email sender
│       ├── publicapi/       the unauthenticated /api/public/* surface
│       └── common/          errors, config, security plumbing, shared types
├── frontend/                React + TypeScript SPA
├── docker-compose.yml       Postgres + Mailpit (+ optional app containers)
└── .env.example             every configurable value
```

Module dependencies form a DAG. `availability` never depends on `booking`:
the engine declares `BookedIntervalProvider` and the booking module implements
it, so the engine can be reasoned about — and unit-tested — with no booking
persistence in sight.

---

## Quick start (local development)

### 1. Infrastructure

```bash
cp .env.example .env
docker compose up -d postgres mailpit
```

* Postgres → `localhost:5432` (db `booking`, user `booking`, password `booking`)
* Mailpit  → SMTP `localhost:1025`, web UI <http://localhost:8025>

**If PostgreSQL is already installed on this machine**, change `POSTGRES_PORT`
in `.env` (e.g. `55432`) before starting the container. On Windows the local
service and the container can both bind 5432 without either reporting a
conflict, and the backend then reaches the wrong server — which shows up as
`FATAL: password authentication failed for user "booking"`.

Using an existing PostgreSQL instead of the container is fine — create a
`booking` database and point `DATABASE_*` at it. The role needs permission to
run `CREATE EXTENSION btree_gist` (migration `V1`), which the exclusion
constraints in `V4` depend on.

### 2. Backend

`docker compose` reads `.env`, but Spring Boot does not — pass the connection
settings as environment variables (or leave them out entirely if Postgres is on
the default `localhost:5432` with the default credentials):

```bash
cd backend

# bash / Git Bash
DATABASE_URL=jdbc:postgresql://localhost:55432/booking \
DATABASE_USERNAME=booking DATABASE_PASSWORD=booking \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```powershell
# PowerShell
$env:DATABASE_URL = "jdbc:postgresql://localhost:55432/booking"
$env:DATABASE_USERNAME = "booking"; $env:DATABASE_PASSWORD = "booking"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

API on <http://localhost:8080>. The `dev` profile adds the seed migration
(two demo businesses, services, staff, resources, working hours, bookings).

Health: <http://localhost:8080/actuator/health>

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

App on <http://localhost:5173>. The dev server proxies `/api` to the backend,
so the browser sees one origin and no CORS preflight is involved.

### Demo logins (dev profile only)

```
owner@demo-pilates.mk / password123    → Demo Pilates Studio
owner@downtown.mk     / password123    → Downtown Barbers
```

Two tenants on purpose: a single-tenant seed makes it impossible to notice when
a query has lost its `business_id` filter.

Public booking page: <http://localhost:5173/book/demo-pilates-studio>

---

## Running everything in Docker

```bash
docker compose --profile full up --build
```

Frontend on <http://localhost:3000>, backend on <http://localhost:8080>.
In this setup nginx proxies `/api` to the backend container.

---

## Tests

```bash
cd backend && ./mvnw test
```

The availability engine has the bulk of the coverage (33 unit tests): opening
hours, closed days, the slot grid, existing bookings, buffers before and after,
staff-specific hours, blocked time, "any available" staff, multiple resources,
minimum notice and maximum advance. The engine takes a fully loaded context and
performs no I/O, so each rule is testable in a few lines.

Integration tests use Testcontainers, are tagged `integration`, and are skipped
automatically when no Docker daemon is reachable:

```bash
./mvnw verify -Dgroups=integration
```

---

## Design notes

**Multi-tenancy.** Every business-owned row carries `business_id`. The tenant is
resolved from the authenticated principal — never from a path variable — by
`CurrentTenant`, and every repository finder is scoped by it (`findByIdAndBusinessId`,
not `findById`). There is deliberately no `/api/businesses/{id}` route, so
changing an id in a URL cannot cross tenants. Memberships are re-read from the
database on every request, so revoking access takes effect immediately rather
than when a token happens to expire.

**Time.** Instants are persisted as `timestamptz` (UTC). Working hours are
stored as wall-clock `LocalTime` + day-of-week and interpreted in the *business*
timezone, so "Monday 09:00" stays 09:00 across DST changes. The server timezone
is never used for a domain decision. API responses carry both the instant and
the business-local wall clock, so the UI does no timezone maths.

**Double booking.** Prevented at three levels: the availability engine never
offers a taken slot; a conflict re-check runs inside the booking transaction;
and PostgreSQL `EXCLUDE` constraints over `tstzrange` (via `btree_gist`) on
staff and resource reject the second of two genuinely simultaneous inserts. The
third is the guarantee — the first two exist to turn a race into a clear 409
`BOOKING_NOT_AVAILABLE` rather than a database error.

**Buffers.** A booking stores two windows: the appointment the customer sees,
and the block window including buffers. Conflict detection everywhere — engine,
service layer and database constraint — uses the block window, so changing a
service's buffers later cannot make existing bookings retroactively overlap.

**Lead times.** Minimum notice and maximum advance apply to customers booking
themselves, not to the business taking a booking over the phone. Conflict and
working-hours rules apply to both.

**Notifications.** Booking logic publishes an event and never talks to SMTP.
A listener writes to an outbox table after the transaction commits; a scheduled
dispatcher drains it. A mail outage cannot fail a booking, and adding
SMS/WhatsApp/Viber later means a new `NotificationSender` bean plus a channel
value — no change to booking or availability code.
