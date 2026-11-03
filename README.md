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
- OpenAPI / Swagger UI: <http://localhost:8080/swagger-ui.html> (dev profile only;
  elsewhere set `API_DOCS_ENABLED=true` to turn it on)

Without the `dev` profile the backend does not start unless `JWT_SECRET` is
set to a real secret (`openssl rand -base64 48`); the example values in this
repository are refused.

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
| `/admin/reservations`             | owner, staff | List and week calendar; confirm, complete, mark a no-show, cancel, move, change resources; new reservation. Staff see only theirs and can confirm, complete or mark a no-show |
| `/admin/services`, `/new`, `/:id` | owner    | Services with category, photo and resource requirements |
| `/admin/resources`                | owner    | Resources with photo, capacity, own hours, service restrictions |
| `/admin/resource-types`           | owner    | Business-defined resource types, each with an optional floor plan |
| `/admin/employees`                | owner    | Employees with photo, the services they perform, working hours, and their login (invite, remove access) |
| `/invite/:token`                  | employee | Accepting an invitation: set a password, or sign in with an existing account |
| `/admin/customers`, `/:id`        | admin    | Customers with visits, spend and history         |
| `/admin/discounts`                | owner    | Promo codes                                      |
| `/admin/revenue`                  | owner    | Today, this week and this month, plus a custom range with gross, discounts, net and average |
| `/admin/analytics`                | owner    | Revenue, reservations, services, resource utilization, new vs returning customers |
| `/admin/settings`                 | owner    | Profile, **booking page** (logo, cover photo, gallery, tagline, colour, social links, map), opening hours, booking rules, closures, how bookings reach your calendar |

Each business is reachable only through its own link. Nothing on its pages
leads to another business, and there is no public list of businesses.

API: customer-facing endpoints live under `/api/public/**` (no login), admin
endpoints under `/api/admin/**` (signed in) and auth under `/api/auth/**`. The
full reference is in Swagger UI.

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

**One phone number is one person.** A customer is recognised by email, then
by phone, in every channel: the booking page, the admin and Instagram or
Facebook chats. Numbers are stored in one form (`+38970555444`). A number typed
without a country code is read in the business's own country, taken from the
business's phone number, so `070 555 444` in a Macedonian business is the same
customer. Migration 025 brought existing numbers into this form and merged
customers who turned out to share a number into the oldest record (bookings,
chat links and details moved over; a second email is kept in the notes).

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
photos. Up to three photos sit in a grid (a swipeable row on phones); more
become a carousel that scrolls one photo at a time. The photos are
decoration and are not clickable.

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

### Combining resources for larger groups

A requirement can have **Combine for larger groups** switched on. Its quantity
then becomes a minimum. When no single resource seats the group, the engine
puts free ones of that type together: Table 3 + Table 4 for eight, four desks
for a team of four. The admin only manages individual resources. Combinations
are never configured or stored as such. A reservation simply holds one
`reservation_resource` row per resource it was given, so history keeps exactly
what was allocated.

`availability/AllocationFinder` does the search, and it is pure and
unit-tested:

- It only uses resources that are eligible for the service and free at that
  time.
- A combination stays within one floor plan, so a group is never split between
  the dining room and the terrace. Resources on no plan form one area.
- Ranking (`AllocationFinder.RANKING`, one comparator) prefers, in order:
  1. fewer resources
  2. fewer spare seats
  3. the engine's usual preference (smallest, least busy)
- No set ever includes a resource the group could do without.
- It never tries all 2^N subsets:
  - Resources of equal capacity in an area are counted rather than enumerated.
  - Branches are pruned as soon as they cannot reach the group, already seat
    it, or cannot beat the options already kept.
  - Hard caps of 20 resources per allocation and 20,000 search steps.

  Five hundred identical desks are searched in milliseconds.

The availability response carries the best options per slot
(`requirements[].allocations`). Customers choose one of these, or "No
preference". Admins can also pick resources by hand, and the server validates
the set with the same rules.

## Double booking

Double booking is prevented in three layers:

1. The engine never offers a taken slot.
2. The exact time and resources are re-checked inside the reservation transaction.
3. A PostgreSQL `EXCLUDE USING gist (resource_id WITH =, tstzrange(...) WITH &&)`
   constraint on `reservation_resource` rejects the second of two truly
   simultaneous inserts. The service turns that into
   `409 RESOURCE_NOT_AVAILABLE`.

Layer 3 is the guarantee. `ReservationIT` fires six concurrent requests at
one resource: exactly one succeeds. It holds for combinations too, because
every resource of a combination is its own row under the same constraint.
`CombinedResourcesIT` races two groups for Table 1 + Table 3 and Table 2 +
Table 3: exactly one gets in, and nothing is half-written.

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

## Starting a new business

At sign-up the owner picks what kind of business it is. The business then
starts with a working setup, so its booking page takes bookings straight away.
Everything can be changed later.

| Type | Starts with |
|---|---|
| Restaurant / Bar / Café | Tables 1–6 (2–6 seats) and one free *Table reservation* (2 h, up to 12 guests, tables combined for big groups). No prices, and the wizard skips choosing a service. Open every day 12:00–23:00. |
| Hair salon / Barber | The owner as the first employee; Haircut, Haircut & beard, Colouring. Mon–Sat 09:00–19:00. |
| Nails / Beauty | The owner as the first employee; Manicure, Pedicure, Gel nails. Mon–Sat 09:00–19:00. |
| Spa / Massage | The owner as the first employee, Treatment room 1; three treatments that need both. Mon–Sat 10:00–20:00. |
| Fitness / Classes | A shared studio with 12 places; *Group class*, priced per person, starting on the hour. There is no class timetable yet: everyone who books the same hour joins the same class. |
| Co-working | Desks 1–8 and a meeting room; half-day and full-day desks (combined for teams) and the meeting room by the hour. Mon–Fri 08:00–20:00. |
| Something else | An empty setup open Mon–Fri 09:00–17:00, as before templates. |

- The employee type keeps its neutral name, *Employee*, because "Stylist" or
  "Therapist" doesn't fit every salon or spa. Renaming it on the Resource
  types page changes the booking page's "Choose your …" wording.
- Sign-up defaults to Europe/Skopje and MKD. Picking another timezone moves
  the currency with it (London → GBP) until the owner picks a currency
  themselves. Sample prices are converted roughly, e.g. a 15 EUR haircut
  becomes 900 MKD.
- **Events** (a band, a quiz night) are only for restaurants and bars. Other
  types have no Events page, the API refuses to create events for them, and
  their booking page and chat show none. Businesses created before types
  existed keep events.

The templates are in `onboarding/BusinessTemplates`. They run inside the
sign-up transaction and go through the same services as the admin.

**Getting started.** A new business's dashboard shows a checklist:
1. services and prices
2. tables, team or desks
3. opening hours
4. address and phone
5. a logo or cover photo
6. sharing the booking link

Saving one of those, or pressing *Looks good*, ticks its step. The address
and photo steps follow the business's details, and *Hide* puts the checklist
away. Businesses created before templates existed have no checklist.

## Roles

- **Owner:** everything, for their business.
- **Staff:** an employee who logs in. They see only the reservations they are
  assigned to and can confirm them, complete them or mark a no-show.
  Cancelling, moving and creating reservations, customers, analytics and
  settings stay with the owner.
- **Customer:** a booking account. Customers book and manage their own
  reservations, and never reach the admin panel.

Roles are per business: the same person can own one business and be staff at
another.

**Inviting staff.** Employees → *Invite to log in* emails the employee a link
that works for 7 days. Inviting again replaces the link, and *Withdraw* cancels
it. The link proves the address, so accepting it either creates an account
(name and password, no code) or, if the address already has one, asks for
that account's password. The person becomes staff, linked to that employee.
*Remove access*, or deleting the employee, takes the login away on their next
request.

**Staff hear about their appointments.** When a reservation is assigned to an
employee who can log in, they get an email at their employee address, and it
is also a calendar invitation:
- **New:** "New appointment: …". The event appears in their calendar.
- **Moved:** "Appointment moved: …". The event moves.
- **Cancelled:** "Appointment cancelled: …". The event is removed.
- **Given to someone else:** the old employee gets "Removed from your
  schedule", and the new one gets it as a new appointment. If only the employee
  changed, the customer and the owner get no email.

Employees without a login get no email. It needs email confirmations
switched on, like every other message.

**Where it is enforced.** `AdminAccessInterceptor` keeps a short list of the
admin endpoints staff may call. Everything else under `/api/admin` is
owner-only, so a new endpoint is closed to staff unless it is added to that
list on purpose. `ReservationAccess` then limits staff to their own rows.

## Security

**Sessions.** Signing in sets an httpOnly `SameSite=Lax` cookie
(`booking_session`, path `/api`). No script on the page can read it, and the
token never appears in a response body. A write made with the cookie must
also carry `X-Requested-With`, which a cross-site form cannot add. API
clients may still send `Authorization: Bearer …` instead. Changing the
password ends every older session. Set `SESSION_COOKIE_SECURE=true` wherever
the site is served over HTTPS.

**Sign-up.** Business owners and customers both confirm their email with a
6-digit code before an account exists.

**Rate limits.** Per client address, per minute:

| What | Limit |
|---|---|
| `POST /api/auth/**` (sign-in, sign-up, codes, reset) | 20 |
| Availability searches | 120 |
| Other public writes (quote, book, cancel) | 30 |

Per account: 10 failed sign-ins lock it for 15 minutes, and at most 5 codes
an hour are emailed to one address. Over the limit the API answers `429
TOO_MANY_REQUESTS` with `Retry-After`. The counters are kept in memory, so
each backend instance counts on its own; behind several instances, move them
to Redis or limit at the gateway. The client address comes from
`X-Forwarded-For`, trusted only from private-network proxies.

**Headers.** nginx sends a Content-Security-Policy (scripts only from the
site, the Google Maps embed as the only frame), `X-Frame-Options: DENY`,
`nosniff` and a strict referrer policy. Actuator is not proxied.

**Concurrent changes.** Reservations carry a version. When a customer and the
owner change the same reservation at the same moment, the second change gets
`409 CONFLICT` instead of overwriting the first (and, for example, giving a
discount use back twice).

**Background work on several instances.** The email and webhook workers lock
each batch with `FOR UPDATE SKIP LOCKED` and lease it for 10 minutes before
sending, with no database transaction open while they send. Two instances
never send the same message, and a crashed instance's batch is retried when
its lease runs out.

## No-shows

Once a reservation has started, the business can mark it as a **no-show**
(the customer did not come).
- Its resources are released, so a walk-in can take the rest of the slot.
- It earns nothing and is not a visit. It still appears in the customer's
  history and in analytics (count and rate).
- Pressing the wrong button is easy to fix: a no-show and a completed
  reservation can be swapped either way.

It is an admin-only record. Customers see nothing different and are never
stopped from booking.

## Calendars

**The owner's calendar.** The emails the business already gets about new,
moved and cancelled reservations are calendar invitations (`METHOD:REQUEST`,
or `METHOD:CANCEL` for a cancellation).
- Gmail and Outlook put each reservation in the calendar of the address the
  emails go to, with the customer's name, phone and what was booked. A moved
  reservation moves its event, and a cancelled one removes it.
- The owner is listed as having already accepted, so there is nothing to
  answer. In Google Calendar, Settings → Event settings → *Add invitations to
  my calendar* must be *From everyone* for this to happen without a click.
- There is nothing to connect, subscribe to or keep secret, and it works the
  same locally as deployed. It needs email confirmations switched on.
- Settings → Calendar explains this to the owner.

**Customers.** Nothing goes into a customer's calendar unless they add it:
- The confirmation, reminder and "moved" emails have **Add to calendar**
  buttons: Google, Outlook, and Apple / other (an `.ics` file).
- The confirmation and "moved" emails also attach the event as
  `reservation.ics`, so mail apps can offer to add it.
- The reservation page has the same buttons.

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

## Automation and social booking (n8n)

Customers can book and cancel by messaging the business on Instagram or
Facebook. **n8n** connects the platform to those channels, and **Claude**
(called from n8n) reads each message and writes the replies. The platform stays
the single source of truth:

```text
Customer ─▶ Instagram / Messenger ─▶ n8n ──REST──▶ /api/integrations ─▶ reservation engine ─▶ PostgreSQL
                                      ▲                                         │
                                      └──── signed webhooks (events) ◀──────────┘
```

| Who | Does |
|---|---|
| Claude (in n8n) | Turns a message into fields ("couples massage", "tomorrow", option 2, "yes"), and writes a natural reply from the facts it is given. It never decides what is free, what it costs, or whether something can be booked. |
| n8n | Receives messages and events, calls the API, sends replies. It holds no booking rules. |
| Platform | Owns the conversation state (`integration_conversation`), availability, resource assignment, price, discounts, cancellation policy and the reservation itself. |

### How a chat booking works

Each incoming message is one **turn**:

1. `GET …/conversations/current`: where the chat stands (last question, numbered options offered, today's date in the business timezone).
2. Claude extracts structured fields (JSON schema output, low effort).
3. `POST …/conversations/turn`: the platform merges the fields into the
   stored state, checks them against the real engines and answers with what to
   ask next. That is `ask` plus numbered `options`, `problems` (a taken time, a
   date in the past), and a `summary` with the server's price.
4. Only when the customer answers **yes to a summary they have already been
   shown**, unchanged, does the turn return `nextAction: CREATE_RESERVATION`
   with the exact request and an `Idempotency-Key`. A time that is only
   mentioned never books anything.
5. n8n calls `POST …/reservations`. If the slot was taken in the meantime it
   reports `…/action-failed`, and the platform steps back to fresh times.
6. Claude writes the reply from those facts only.

Cancelling works the same way: the customer's upcoming reservations, which one,
a confirmation, then `nextAction: CANCEL_RESERVATION`. The business's
cancellation policy applies exactly as on the website.

### Integration API

All endpoints are under `/api/integrations` and accept **only integration
tokens** (`Authorization: Bearer bkp_…`), created by the owner in **Admin →
Integrations**:
- Each token belongs to one business.
- Each token carries its own permissions (`READ_SERVICES`, `READ_AVAILABILITY`,
  `CREATE_RESERVATION`, `CANCEL_RESERVATION`, `READ_CUSTOMER`, `CREATE_CUSTOMER`,
  `MANAGE_CONVERSATIONS`, …).
- Only a SHA-256 of the token is stored.
- Tokens cannot open the admin API, and user sessions cannot open this one.

| Endpoint | |
|---|---|
| `GET /me` | The business and permissions behind the token |
| `GET /businesses/{id}` · `/services` · `/services/{sid}` | Business facts and the booking page's services |
| `GET /businesses/{id}/availability?serviceId&date&numberOfParticipants` | Free start times, from the website's engine |
| `POST /businesses/{id}/quote` | The server's price, validating a discount code |
| `POST /businesses/{id}/customers` · `GET …/customers?provider&externalUserId` | Find or create the customer behind a social user |
| `POST /businesses/{id}/reservations` | Create; **`Idempotency-Key` required** (a repeat returns the first reservation, 200 + `Idempotent-Replayed: true`) |
| `GET /businesses/{id}/reservations/{rid}` · `POST …/{rid}/cancel` | Read; cancel under the customer policy (repeat-safe) |
| `GET …/conversations/current` · `POST …/conversations/turn` · `POST …/conversations/{cid}/action-failed` | Chat conversations |

Full schemas are in Swagger (`/swagger-ui.html`, tag *Integrations*).

Social users are linked to customers through `customer_external_identity`.
A social user is linked only to a customer record created for them. Contact
details typed into a chat are not verified, so someone entering another
person's phone number cannot see or cancel that person's bookings.

Reservations record their **source**: `WEB`, `ADMIN`, `INSTAGRAM`, `FACEBOOK`
or `API`. They appear in the admin panel like any other. **Analytics →
Reservations by channel** shows the count and revenue for each source.

The platform logs every automated step with
`business=… token=… execution=… conversation=… externalUser=…`, taken from the
headers n8n sends, so you can follow one booking through the logs.

### Events to n8n

When a reservation is created, cancelled or rescheduled, the platform writes an
event to the `integration_event` table **in the same transaction**. A worker
then posts it to the n8n webhook:
- It retries with backoff for up to 8 attempts.
- It schedules `reservation.reminder_due` for the business's reminder lead time.
- Each body is small (`event`, `deliveryId`, `reservationId`), and n8n fetches
  the details with its token.
- Deliveries are signed as `X-Booking-Signature: sha256=HMAC(secret, "<X-Booking-Timestamp>.<body>")`.

Admin → Integrations shows recent deliveries.

### Running n8n locally

1. **Start it:**
   ```bash
   docker compose --profile n8n up -d n8n
   ```
   The editor is at <http://localhost:5678>. Create the owner account the first
   time. Workflows are mounted read-only at `/workflows` in the container.

2. **Platform side:** sign in as the business owner, open **Admin →
   Integrations** and:
   - Create an **API token** with all permissions, and copy it (it is shown once).
   - Under **n8n**, enter `http://localhost:5678/webhook/booking-events`,
     switch on *Send events to n8n* and save. Copy the **signing secret**.
     If the backend runs in Docker too (`--profile full`), use
     `http://n8n:5678/webhook/booking-events` instead.

3. **Configure n8n** with environment variables, in a `.env` file next to
   `docker-compose.yml`. Then recreate the container with
   `docker compose --profile n8n up -d n8n`.

   | Variable | Value |
   |---|---|
   | `BOOKING_API_URL` | `http://host.docker.internal:8080/api/integrations` (default; `http://backend:8080/api/integrations` with `--profile full`) |
   | `BOOKING_WEBHOOK_SECRET` | The signing secret from step 2 |
   | `META_VERIFY_TOKEN` | Any string; the same value goes into the Meta app's webhook settings |
   | `META_APP_SECRET` | The Meta app secret (checks the `X-Hub-Signature-256` signature on incoming messages) |
   | `ANTHROPIC_BASE_URL` | Optional; only for a proxy in front of the Claude API |
   | `ANTHROPIC_WORKSPACE_ID` | Only if your API key is not scoped to a workspace (the API then answers "must include the anthropic-workspace-id header") |

4. **Credentials** (n8n → *Credentials* → *Add credential*). Keep these exact
   names, because the workflows refer to them:
   - **Booking Platform API**, type *Header Auth*: name `Authorization`, value `Bearer bkp_…`.
   - **Anthropic account**, type *Anthropic*: your Claude API key.
   - **Meta Page Access Token**, type *Query Auth*: name `access_token`, value
     the Page access token (needed only for real Instagram/Messenger sending).

5. **Import the workflows:**
   ```bash
   docker exec booking-n8n n8n import:workflow --separate --input=/workflows
   ```
   (In Git Bash, prefix with `MSYS_NO_PATHCONV=1`.) Or use *Import from file*
   in the editor with the files in [n8n/workflows](n8n/workflows). Open each
   workflow and select the credentials if n8n asks. Then **publish (activate)
   all of them except the two inbox pollers**, which are only for channels
   whose webhook does not work (see the table). n8n 2.x runs only published
   sub-workflows.

   | Workflow | Role |
   |---|---|
   | BOOKING - Incoming Social Message | Meta webhook (`/webhook/social/meta`, GET verify + POST messages) and a test chat (`/webhook/social/test`) |
   | BOOKING - Conversation Turn | Context → Claude (understand) → turn → create/cancel → Claude (reply) |
   | BOOKING - Create Reservation / Cancel Reservation | One API call each, with idempotency and error mapping |
   | BOOKING - Send Message | Sends through the Graph API (`/me/messages`); TEST messages are only logged |
   | BOOKING - Reservation Events | Verifies the signature, drops duplicate deliveries, routes the event |
   | BOOKING - Confirmation / Reminder / Cancellation Notice | Message the customer on their chat channel. A booking or cancellation the customer made in that chat is not announced twice. |
   | BOOKING - Privacy Policy | Serves the privacy policy page Meta requires for the app (`/webhook/privacy`) |
   | BOOKING - Instagram Inbox Poller / Messenger Inbox Poller | For when Meta does not deliver the `messages` webhook: every 20 seconds each reads the Page's Instagram or Messenger conversations and answers new messages through **BOOKING - Instagram Reply** (despite its name, it replies on either channel). Publish only the pollers whose channel has no working webhook, or customers get two replies. Messenger needs the `pages_messaging` permission on the Page token. |

   Both Claude calls use `claude-opus-5-5` at low effort, with
   `fallbacks: "default"` (beta `server-side-fallback-2026-07-01`). If a model
   declines, the request is retried on another model. If Claude or the platform
   is unreachable, the customer gets a polite "please try again" message, never
   an error.

6. **Test without Instagram:**
   ```bash
   curl -X POST http://localhost:5678/webhook/social/test \
        -H "Content-Type: application/json" \
        -d '{"userId":"tester-1","message":"Hi, I want to book a couples massage tomorrow"}'
   ```
   The reply comes back in the response. Keep sending messages with the same
   `userId` ("2", "17:30", "Ana, +389 70 123 456", "yes"). The reservation then
   appears in the admin panel with source *API*. Use *Send test event* on the
   Integrations page to check the event webhook.

7. **Connect Instagram / Facebook:**
   - In a Meta developer app with Messenger and Instagram messaging, set the
     webhook callback to `https://<public n8n host>/webhook/social/meta`, using
     the verify token from step 3, and subscribe to `messages`.
   - Meta needs a public HTTPS URL. Locally, use a tunnel (for example
     `ngrok http 5678`) and set `N8N_WEBHOOK_URL` to it.
   - Enter the account or Page id in Admin → Integrations → Instagram/Facebook
     and switch on *Take bookings here*.
   - "Connect with Meta" (OAuth) is not built yet. The model stores the
     connection per provider so it can be added later.

**Meta's messaging window:** businesses can freely message a user within 24
hours of that user's last message. Reminders and notices usually fall outside
that window. On Messenger they are sent with the `CONFIRMED_EVENT_UPDATE` tag.
Instagram has no equivalent tag, so Instagram reminders only arrive while the
window is open.

**In production:**
- Set `INTEGRATIONS_BLOCK_PRIVATE_WEBHOOKS=true`, so a business cannot point
  webhooks at internal addresses.
- Set `INTEGRATIONS_PUBLIC_API_URL` to the public API address shown to admins.

---

## Tests

```bash
cd backend
./mvnw test      # unit tests: availability engine, discount rules, pricing
./mvnw verify    # plus integration tests on real PostgreSQL (Testcontainers; needs Docker)

cd frontend
npm run lint && npm run typecheck && npm test    # ESLint, TypeScript, Vitest
npm run test:e2e                                 # Playwright, against a running stack (below)
```

GitHub Actions ([.github/workflows/ci.yml](.github/workflows/ci.yml)) runs all
of it except the end-to-end tests on every push and pull request.

**End-to-end tests** drive a real browser through the app: signing in, the
session cookie, business sign-up with the emailed code, a customer booking,
every admin page, and the security headers. Start the stack with
`docker compose --profile full up --build` (it uses the dev profile, so the
demo businesses exist), then `npm run test:e2e`. `E2E_BASE_URL` and
`E2E_MAILPIT_URL` point them elsewhere.

**API types.** `npm run gen:api` regenerates `src/types/api.generated.ts` from
`backend/target/openapi.json`, which `OpenApiSpecIT` writes during
`./mvnw verify`. `src/types/api.contract.ts` checks at compile time that the
hand-written types in `api.ts` still match the API. CI fails if the generated
file is out of date.

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
  - integration API: token scope and permissions, idempotent creation
    (including simultaneous retries), social identities, cancellation policy,
    events, analytics by source
  - chat conversations: a full booking from the first message, no booking
    without a confirmed summary, cancelling in chat, a slot taken between the
    summary and the "yes"

---

## Deliberately not in the MVP

The platform does not yet have:
- online payments or deposits
- SMS notifications
- loyalty programmes
- recurring reservations
- dynamic pricing
- multiple locations
- forecasting

The model leaves room for all of them: prices and discounts are snapshotted
per reservation, and notifications go through a channel-agnostic outbox.
Email confirmations already exist, from the earlier version.
