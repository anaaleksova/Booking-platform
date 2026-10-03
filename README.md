# Reservation Platform

A generic, configurable reservation and business management platform.

It is not built around one kind of business. A business defines its **services**,
the **resource types** it works with (rooms, tables, chairs, machines,
employees, courts …), the concrete **resources**, **opening hours**,
**prices** and **discounts**. Customers then book, and the platform decides
which resources are free, assigns them, and prevents conflicts.

```
Business → Services → Resource requirements → Resources → Availability
         → Reservations → Customers → Discounts / Revenue / Analytics
```

The same code runs both demo businesses:

| Business        | Service          | Needs                                      | Priced        |
|-----------------|------------------|--------------------------------------------|---------------|
| Serenity Studio | Couples Massage  | 1 Treatment Room + 2 Employees + 2 Chairs  | per booking   |
| Serenity Studio | Classic Massage  | 1 Room + 1 Employee (customer picks) + 1 Chair | per booking |
| Harbor Bistro   | Dinner           | 1 Table (customer picks), seats ≥ guests   | per person    |

---

## Stack

**Backend**: Java 21, Spring Boot 3.3 (Web, Data JPA, Validation, Security),
PostgreSQL 16, Liquibase, springdoc OpenAPI, Maven. A modular monolith.

**Frontend**: React 18, TypeScript, Vite, React Router, TanStack Query,
Axios, Material UI, Recharts.

---

## Quick start

### 1. Database

```bash
cp .env.example .env          # set POSTGRES_PORT if 5432 is taken (e.g. 15432)
docker compose up -d postgres mailpit
```

### 2. Backend

```bash
cd backend
# bash
DATABASE_URL=jdbc:postgresql://localhost:15432/booking ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

```powershell
# PowerShell
$env:DATABASE_URL = "jdbc:postgresql://localhost:15432/booking"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

The `dev` profile turns on the Liquibase `dev` context, which loads the demo
data: two businesses, roughly two months of history and two weeks of upcoming
reservations, all relative to the day it first runs.

- API: <http://localhost:8080>
- OpenAPI / Swagger UI: <http://localhost:8080/swagger-ui.html>

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

The app runs on <http://localhost:5173>. The dev server proxies `/api` to the backend.

### Demo logins

```
owner@serenity.studio   / password123   → Serenity Studio (spa)
owner@harbor-bistro.com / password123   → Harbor Bistro (restaurant)
```

### Everything in Docker

```bash
docker compose --profile full up --build
```

The frontend is on <http://localhost:3000> and the backend on <http://localhost:8080>.

### Upgrading an existing development database

The schema moved from Flyway to Liquibase and was redesigned around resource
requirements. A database created by the old version must be emptied first
(drop its tables, or `docker compose down -v`). Liquibase then builds the new
schema on start-up.

---

## What's where

| URL                               | Who      | What                                             |
|-----------------------------------|----------|--------------------------------------------------|
| `/`                               | anyone   | Neutral start page. Lists no businesses          |
| `/b/:slug`                        | customer | The business's own page: cover photo, price list by category, optional photo gallery, about, map, opening hours |
| `/b/:slug/book`                   | customer | Booking wizard, one view per decision: service → guests & preferences (e.g. choose your therapist) → date & time → choose your table on the map (when there is a floor plan) → details → review & confirm |
| `/booking/:reference`             | customer | Reservation details, self-cancellation           |
| `/admin`                          | owner    | Dashboard: KPIs, revenue and reservation charts, popular services, utilization, upcoming |
| `/admin/reservations`             | admin    | List and week calendar; confirm, complete, cancel, move, change resources; new reservation |
| `/admin/services`, `/new`, `/:id` | owner    | Services with category, photo and resource requirements |
| `/admin/resources`                | owner    | Resources with photo, capacity, own hours, service restrictions |
| `/admin/resource-types`           | owner    | Business-defined resource types, each with an optional floor plan |
| `/admin/employees`                | owner    | Employees with photo, the services they perform, working hours |
| `/admin/customers`, `/:id`        | admin    | Customers with visits, spend and history         |
| `/admin/discounts`                | owner    | Promo codes                                      |
| `/admin/revenue`                  | owner    | Today, this week and this month, plus a custom range with gross, discounts, net and average |
| `/admin/analytics`                | owner    | Revenue, reservations, services, resource utilization, new vs returning customers |
| `/admin/settings`                 | owner    | Profile, **booking page** (logo, cover photo, gallery, tagline, colour, social links, map), opening hours, booking rules, closures |

Each business is reachable only through its own link. Nothing on its pages
leads to another business, and there is no public list of businesses.

API: customer-facing endpoints live under `/api/public/**` (no login), admin
endpoints under `/api/admin/**` (JWT) and auth under `/api/auth/**`. The full
reference is in Swagger UI.

---

## Domain model

```
business ─┬─ business_hours          opening hours (wall-clock, business timezone)
          ├─ resource_type           "Treatment Room", "Table", "Employee" …
          │    ├─ floor_plan         optional map ── floor_plan_item (resources) + floor_plan_decor (room)
          │    └─ resource           concrete items, with capacity and optional own hours
          │         └─ employee      people; every employee IS a resource of the employee type
          ├─ service
          │    └─ service_resource_requirement   (type, quantity, required, customer_can_choose)
          ├─ customer                recognised again by email or phone
          ├─ discount ── discount_service
          └─ reservation             participants, status, original/discount/final price
               └─ reservation_resource   the concrete resources held
```

Every business-owned row carries `business_id`. The tenant always comes from
the authenticated user, never from a URL, and every query is scoped by it.

**Images.** Logos, cover photos and service, employee and resource photos
are uploaded through `POST /api/admin/media`. They are stored in PostgreSQL
(the `media` table, 5 MB max) and served from `/api/public/media/{id}` with
long-lived caching. The file type is detected from the bytes, not from what
the browser claims, so only real JPEG, PNG, WebP or GIF images are accepted.
A replaced image is deleted automatically.

**Gallery.** Optional and up to 12 photos, with captions and an order you
set. It is managed in Settings → Booking page; changes save immediately,
and a switch hides the section. The section only appears when it has
photos. Desktop shows a grid sized to the photo count, phones a swipeable
row, and tapping opens a full-screen viewer.

**Floor plans.** Optional, with one plan per floor or area of a resource type. A co-working space can draw each of its floors, and a restaurant its dining room and rooftop. Restaurants, bars and
coworking spaces can use them to show where each table or desk is. Open
Resource types → *Add floor plan*. *Add floor* adds a tab with its own name and drawing; floors can be reordered, renamed and deleted, and a table moved to another floor with *Move here*. Everything is saved in one go. The plan is drawn, not photographed: walls
and a wooden floor, plus room pieces you place yourself (tinted areas such as
a terrace or kitchen, walls, the bar, plants and the entrance). Drag each
table into place and pick a round, square or long shape and a size. Each
table is drawn with one chair per seat. Positions are stored as percentages,
so the drawing scales to any screen.

When a service lets customers choose that type, the wizard adds a step after
the date and time. With several floors, a floor picker above the map shows how many spots are free on each floor. The map shows what is free at that time:
- available tables in green
- booked tables in red
- tables too small for the group faded

Live counts, zoom, "No preference" and a list view are always there.
Resources left off the map can still be booked. Without a plan, nothing
changes. The demo Harbor Bistro has one.

**Shared tables and minimum group size.** Each resource with a capacity
has a setting for when a smaller group books it (Resources → edit →
*When a smaller group books*):
- **Give them the whole resource.** This is the default. Optionally set a
  *smallest group*, so for example the 8-seat table is never given to 2
  people.
- **Share it.** The group holds only the seats it needs, and the rest stay
  bookable at the same time. A party of 2 at a shared 8-seat table leaves 6
  seats for others.

On the floor plan, a partly used shared table shows "6 of 8 free", with the
taken chairs in grey. Seats are counted at the busiest moment of the
booking. The database enforces the limit with a trigger under a
per-resource lock, the same way the exclusion constraint prevents double
booking of private resources. Two groups racing for the last seats cannot
both get in.

**Wording on the booking page follows your resource types.** The wizard says
"Choose your {type}". Rename the employee type to "Therapist" or "Barber" on
the Resource types page and the page reads "Choose your therapist".

**Employees are resources.** Each employee is backed by a resource of the
business's employee type. "2 × Employee" is scheduled by the same engine,
conflict check and database constraint as "1 × Treatment Room". There is no
second booking mechanism for people.

**Capacity and participants.** A resource's capacity is how many
participants it serves at once, for example a table's seats. `null` means
unlimited. The resources assigned to each requirement must together cover the
group. The engine picks the smallest adequate combination (two guests get the
two-seat table) and prefers the least busy resource among equals.

---

## Availability engine

`availability/AvailabilityEngine` is pure computation over a fully loaded
context: no I/O and no clock of its own. For each candidate start time:

1. Lead times: minimum notice and maximum advance (customer bookings only).
2. The business must be open for the whole appointment. Opening hours minus
   closures, in the business timezone.
3. For every requirement of the service, enough eligible resources of the
   type must be free:
   - active
   - allowed for this service
   - within their own hours
   - with no reservation or blocked time touching the block window (the
     appointment plus buffers)
   - with enough combined capacity for the group

   Optional requirements are assigned when possible but never block a slot.

The same engine lists slots for customers and admins, and validates the final
reservation inside its transaction. Customers can pick resources only for
requirements the business marked as customer-choosable. Everything else is
assigned by the server.

## Double booking

Double booking is prevented in three layers:

1. The engine never offers a taken slot.
2. The exact time and resources are re-checked inside the reservation transaction.
3. A PostgreSQL `EXCLUDE USING gist (resource_id WITH =, tstzrange(...) WITH &&)`
   constraint on `reservation_resource` rejects the second of two truly
   simultaneous inserts. The service turns that into
   `409 RESOURCE_NOT_AVAILABLE`.

Layer 3 is the guarantee. `ReservationIT` fires six concurrent requests at
one resource: exactly one succeeds.

## Pricing, discounts and revenue

- **Price:** the service price, times participants when the service is priced
  per person. It is snapshotted on the reservation as `original_price`,
  `discount_amount` and `final_price`.
- **Discounts:** percentage or fixed amount (capped at the price), with:
  - inclusive validity dates in the business timezone
  - an active flag
  - a maximum usage, enforced with a guarded `UPDATE`, so it holds under
    concurrency
  - a minimum booking value
  - optional service restrictions

  The price is always computed by the server, both for the summary (`/quote`)
  and again on submit. Cancelling a reservation gives its discount use back.
- **Revenue:** `sum(final_price)` of **CONFIRMED and COMPLETED**
  reservations, attributed to the day they take place. Pending and cancelled
  reservations never count. "Gross" is the same set over `original_price`.

## Errors

Every error has one shape:

```json
{ "timestamp": "…", "status": 409, "error": "RESOURCE_NOT_AVAILABLE",
  "message": "The selected resources are no longer available.", "path": "…" }
```

Statuses: 400 validation, 401/403 auth, 404 not found (including another
tenant's ids), 409 conflicts, 422 business-rule rejections (outside hours,
discount expired …) and 500 unexpected.

---

## Tests

```bash
cd backend
./mvnw test      # unit tests: availability engine, discount rules, pricing
./mvnw verify    # plus integration tests on real PostgreSQL (Testcontainers; needs Docker)
```

- **Unit tests:** resource available or booked, several requirements at once,
  participants and capacity, best fit, opening hours, closures, resource
  hours, buffers, service duration, overlapping reservations, customer
  choices, lead times; valid, expired, not-started, inactive and used-up
  discounts, minimum value, service eligibility, amount calculation.
- **Integration tests:**
  - reservations: assignment, capacity, price, discount and usage limit,
    conflicts, concurrent double booking, cancellation releasing resources,
    moving a reservation, customer recognition
  - revenue and analytics
  - multi-tenant isolation

---

## Deliberately not in the MVP

The platform does not yet have:
- online payments or deposits
- SMS notifications
- customer accounts
- loyalty programmes
- recurring reservations
- resource combinations (for example two tables joined for one party)
- dynamic pricing
- multiple locations
- forecasting

The model leaves room for all of them: prices and discounts are snapshotted
per reservation, and notifications go through a channel-agnostic outbox.
Email confirmations already exist, from the earlier version.
