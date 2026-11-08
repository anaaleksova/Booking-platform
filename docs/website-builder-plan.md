# Website builder: implementation plan

Status: **approved 2026-11-03**. Phases 1 and 2 are done (uncommitted). Phase 2 is waiting for review. Phases 3–7 have not started.

### Phase 2: what was built

- **Document format version 2.** It adds `theme.typography` and `theme.components`. `SiteSchema` upgrades stored version 1 pages with the defaults, which are exactly how they looked before. This is the first real migration step.
- **Fonts.** 16 curated fonts plus "Device font" (the default, and what pages always used). They are self-hosted with `@fontsource` (one exact-version package per font) and loaded only when a theme uses them.
  - Lato, Poppins, Work Sans, DM Serif Display and Bebas Neue have no Cyrillic, and the picker says so.
  - Bebas Neue is capitals only, so the server refuses it as the text font.
- **Typography.** Heading font, text font, text size (14–20px), heading weight (300–900), heading style (normal, capitals, italic) and heading alignment.
- **Components.** Card corners (sharp, soft, round), button shape (square, rounded, pill), main button style (filled, outline, text only), cards (flat, border with 1–3px, shadow soft or strong), and photos (square, rounded, circle).
- **Applied everywhere.** These are applied through the MUI theme and CSS variables, so they reach the booking flow and the reservation page too. Each choice left at its default changes nothing, and a default page still renders pixel-identical.
- **Live preview, pulled forward from Phase 6.** The small mock-up next to the editor is replaced by the whole draft page, scaled down and updated on every edit, without saving. The desktop/phone switch stays in Phase 6.

### Phase 1: how it differs from this plan

- **No data-converting migration.** A business without a `site_config` row is served a default document built live from its profile (`SiteDefaults`), which is exactly the old page. The row is created the first time the owner opens the builder. Changeset 029 only creates the table, and `CreateSiteConfigs` (§5) was not needed.
- **Texts inherit.** Hero title, subtitle and image, and About title and text, are `null` by default and show the business's name, tagline, cover and description live. The existing "Images" and "Words" cards keep editing those directly.
- **References are pruned, not refused.** Ids of services, employees or images the business does not own (another tenant's, or since deleted) are dropped on save and publish, so a deleted service never makes the page unsaveable.
- **The theme reaches the booking flow** through a new `theme` field on `GET /api/public/businesses/{slug}`, so the wizard needs no extra request.
- **Section tones** (`style.tone`: page, card, main or footer colour) replace per-section backgrounds until Phase 4.
- **Verified:** before/after full-page screenshots of both demo businesses (desktop and phone) are pixel-identical.

## 1. What exists today

| Area | Today |
|---|---|
| Tenant model | `business` table / `Business` entity. All admin routes act on the caller's own business (`CurrentTenant`), never on an id from the client. The public API resolves the business from `/b/{slug}`. |
| Homepage data | Flat columns on `business`: `tagline`, `description`, `accent_color`, `logo_media_id`, `cover_media_id`, `instagram/facebook/website_url`, `show_map`, `show_gallery`, `dark_theme`. Gallery photos are in `gallery_image`. There is no draft: saving goes live immediately. |
| Editing | `features/business/BookingPageTab.tsx` (Settings → Booking page): one form plus a small static hero preview, saved through `PUT /api/admin/business`. |
| Rendering | `pages/public/BusinessPage.tsx` hard-codes the order: Hero → Events → Services → Gallery → About → Find us. `features/public/BusinessShell.tsx` draws the header and footer and applies `brandTheme(accent, dark)`, an MUI theme. The same shell wraps the **booking wizard** and the reservation details page. |
| Live data on the page | `GET /api/public/businesses/{slug}` (profile, hours, gallery), `/services`, `/events`. |
| Images | `POST /api/admin/media`: JPEG/PNG/WebP/GIF, 5 MB max, stored as bytes in Postgres, served from `/api/public/media/{id}`. `MediaService.requireOwned` stops a business from pointing at another business's images. No video. |
| Styling | MUI 5 + Emotion `sx`. No CSS modules or Tailwind. The `Inter` font is named in `theme.ts` but never loaded, so the system font is what renders. |
| Migrations | Liquibase: SQL changesets in `db/changelog/changes/NNN-*.sql`, plus a Java `customChange` for data migrations (`025` / `MergeCustomersByPhone`). The next number is **029**. |
| JSON in the DB | One precedent: `conversation.data` is a `text` column (de)serialised with Jackson. There are no `jsonb` columns. |
| Authorization | `AdminAccessInterceptor`: everything under `/api/admin/**` is owner-only unless allow-listed for staff. |
| Branding elsewhere | Emails use `business.accent_color` and `dark_theme` (`NotificationComposer.Branding`). |
| Security headers | `frontend/security-headers.conf` CSP: `font-src 'self' data:`, `frame-src https://www.google.com`, `frame-ancestors 'none'`, `X-Frame-Options: DENY`. |
| Tests | Backend: `*Test` unit tests and `*IT` integration tests on `AbstractIntegrationTest` (Testcontainers Postgres, MockMvc, `registerAndLogin`), plus `MultiTenancyIT` for cross-tenant checks. Frontend: Vitest + Testing Library with `fakeApi()`; Playwright e2e. `OpenApiSpecIT` + `api.contract.ts` keep the hand-written TS types in line with the API. |
| Libraries | No drag-and-drop, carousel, HTML-sanitiser or rich-text library on either side. |

## 2. Data model

### Table (changeset `029-site-config.sql`)

```sql
CREATE TABLE site_config (
    business_id     BIGINT PRIMARY KEY REFERENCES business(id) ON DELETE CASCADE,
    draft           JSONB  NOT NULL,
    published       JSONB  NOT NULL,
    draft_version   INT    NOT NULL DEFAULT 0,   -- optimistic lock for "save draft"
    published_at    TIMESTAMPTZ,
    created_at / updated_at                      -- as in Auditable
);
```

- One row per business. A row always has both documents, so the public page never falls back to anything.
- `jsonb` mapped with Hibernate 6's built-in `@JdbcTypeCode(SqlTypes.JSON)`, so no new library. Unlike `conversation.data`, `jsonb` lets a later migration query or patch documents in SQL.
- "Discard draft" copies `published` back into `draft`. "Publish" copies `draft` into `published`.
- `draft_version` is sent with every save. A save based on an old version is refused with `CONFLICT`, so two open tabs cannot silently overwrite each other.

### Document shape

This is the example from the brief, with these adaptations:

- **Profile data stays on `Business`.** Name, logo, phone, email, address, socials, currency and timezone are business facts that the booking wizard, emails and n8n also use. The page reads them live and does not copy them into the config.
- **Booking data is referenced by id.** A Services/Pricing section stores `{ "source": "ALL" }` or `{ "source": "SELECTED", "serviceIds": [..] }`. Team stores `employeeIds`, Gallery stores `mediaIds` (or `"source": "GALLERY"` to use the existing gallery), and Contact stores flags like `showHours` / `showMap`. Ids that no longer exist are dropped at render time, and the editor flags them.
- **Images are media ids, not file names**, e.g. `{ "mediaId": "uuid", "alt": "…" }`. They are checked with `MediaService.requireOwned`.
- Every section has a stable `id` (UUID, generated client-side, validated server-side) for React keys, anchors (`#section-id`) and nav links.
- `schemaVersion` sits at the root.

### Backend types (`com.bookingsaas.site`)

A new module next to `business`, using records like the rest of the DTOs:

```
site/
  SiteConfig.java              entity (business_id, draft, published, version)
  SiteConfigRepository.java
  SiteConfigService.java       get/save/publish/discard, plus public read
  SiteController.java          /api/admin/site/**
  model/SiteDocument.java      root record: schemaVersion, theme, header, footer, sections
  model/Theme.java, Header.java, Footer.java, Section.java, SectionStyle.java …
  model/SectionType.java       enum, each value listing its allowed layouts
  model/content/*.java         one content record per section type (sealed interface)
  validation/SiteValidator.java  cross-field rules (see §4)
  migration/SiteSchemaMigrator.java  v1 → v2 → … upgrades on read (see §5)
  migration/CreateSiteConfigs.java   Liquibase customChange: legacy columns → v1 doc
  presets/SitePresets.java     Elegant / Bold / Minimal / Creative (Phase 7)
```

`Section.content` is polymorphic on `type`, using Jackson `@JsonTypeInfo(include = EXISTING_PROPERTY, property = "type")` over a sealed `SectionContent` interface. Each type gets its own typed record, validated with Bean Validation like every other request in the codebase.

## 3. API

Admin routes are owner-only by default through `AdminAccessInterceptor`. Nothing is added to the staff allow-list.

| Method | Path | What it does |
|---|---|---|
| GET | `/api/admin/site` | `{ draft, published, draftVersion, publishedAt, hasUnpublishedChanges }` |
| PUT | `/api/admin/site/draft` | Body `{ draftVersion, document }`. Validates and saves, returns the same shape as GET. |
| POST | `/api/admin/site/publish` | draft → published. Also writes `theme.colors.primary` → `business.accent_color` and dark/light → `dark_theme`, so emails stay on-brand. |
| POST | `/api/admin/site/discard` | published → draft |
| GET | `/api/admin/site/presets` | Preset list: id, name, description, thumbnail colours, document (Phase 7) |
| GET | `/api/public/businesses/{slug}/site` | Published document only. Cacheable (ETag on `published_at`). |

Existing endpoints stay as they are. `PublicBusinessResponse` keeps its fields so the booking wizard is untouched.

Errors use the existing `ApiException` / `ErrorCode` format. Validation errors carry a field path (`sections[3].content.title`) so the editor can point at the field. One new code: `SITE_DRAFT_OUTDATED` (409).

## 4. Validation and safety (server)

`SiteValidator` runs after Bean Validation on every save, publish and preset apply:

- Known `type`, and a `layout` allowed for that type (enum table, not free strings)
- Enum values for every option (radius, button style, alignment, animation, …)
- Colours `^#[0-9a-fA-F]{6}$`; opacities 0–1; font size 12–24; weights 300–900; columns 1–4; ratio 30–70 %
- At most 30 sections; text length caps per field; at most 12 buttons/items per list section; unique section ids
- Every `mediaId`, `serviceId` and `employeeId` belongs to this business
- Fonts only from the curated list (Phase 2)
- URLs are `http(s)://`, `mailto:` or `tel:` only, so no `javascript:`
- Rich text (Phase 5): sanitised to a small allow-list of tags (`p, h2–h4, strong, em, a[href], ul, ol, li, br, blockquote`) with the **OWASP Java HTML Sanitizer**. This is the one new backend dependency (small, no transitive deps, the standard choice). Because it is sanitised on save, the stored HTML is always safe.
- Embeds (Phase 5): store `{ provider: YOUTUBE | VIMEO | GOOGLE_MAPS | SPOTIFY, id }`, never raw HTML or iframe URLs. The frontend builds the iframe URL from the provider template.

The frontend never renders anything as HTML except sanitised rich text, and it treats unknown values defensively anyway (§6).

## 5. Schema versioning and migrating existing homepages

**Versioning.** `SiteSchemaMigrator` holds an ordered list of `JsonNode → JsonNode` steps (`v1→v2`, …) and runs on every read before Jackson binds the document. The upgraded document is written back lazily on the next save. Old data keeps working, and a format change is one new step plus a unit test with a stored v1 fixture.

**Initial migration** (Liquibase `customChange` `CreateSiteConfigs`, changeset 030, same pattern as `MergeCustomersByPhone`). For every business it builds a v1 document that renders like today's page and stores it as both draft and published:

| Today | Becomes |
|---|---|
| `accent_color`, `dark_theme` | `theme.colors.primary`, plus background/text from the current light or dark palette |
| `cover_media_id`, `tagline` | HERO / `FULL_WIDTH_IMAGE` with the cover as background image, the business name as title and the tagline as subtitle. No cover means the current gradient. |
| (events exist) | EVENTS / `CARDS` (the existing events section, as a section type) |
| services | SERVICES / `GROUPED_LIST` (a layout that reproduces today's grouped price list exactly) |
| `show_gallery` + `gallery_image` | GALLERY / `GRID`, `source: GALLERY`, hidden when `show_gallery` was false |
| `description` | ABOUT / `CENTERED_TEXT`, title "Welcome to {name}" |
| `show_map` | CONTACT / `MAP_AND_INFO` or `INFO_ONLY`, with opening hours |
| header / footer | `LOGO_LEFT_NAV_RIGHT`, transparent over hero; footer `CENTERED` (today's footer) |

New businesses get the same default document at sign-up (`BusinessCreatedEvent` listener). In Phase 7 the document comes from a preset matched to the business type (e.g. salon → Elegant, restaurant → Bold).

The legacy columns stay for one release and are no longer written by the builder (except `accent_color`/`dark_theme` on publish, for emails). Dropping them is a separate, later changeset.

**Acceptance check for "looks the same":** a Playwright screenshot of `/b/{slug}` for both demo businesses before and after the change, compared with a small tolerance.

## 6. Frontend structure

```
src/features/site/
  api.ts                     useSite, useSaveDraft, usePublish, useDiscard, usePublicSite, usePresets
  types.ts                   SiteDocument, Section, Theme … (hand-written, checked in api.contract.ts)
  theme/
    themeVars.ts             theme → CSS custom properties (--site-color-primary, --site-radius, --site-font-heading …)
    siteMuiTheme.ts          theme → MUI theme (replaces brandTheme for the site; same cache trick)
    fonts.ts                 curated font list + loadFonts(theme): only the 1–2 families in use
  render/
    SitePage.tsx             root: sets CSS vars on a wrapper, renders header, sections, footer
    SectionRenderer.tsx      looks up the registry; unknown → null (+ console.warn in dev), error boundary per section
    registry.ts              { HERO: { FULL_WIDTH_IMAGE: lazy(HeroFullWidth), SPLIT: … }, … }
    useSiteData.ts           one place that loads live services/employees/hours/events/gallery for sections
  sections/
    hero/HeroFullWidth.tsx, HeroSplit.tsx, HeroCentered.tsx, HeroVideo.tsx
    services/ServicesGrid.tsx, ServicesList.tsx, ServicesCarousel.tsx, ServicesAlternating.tsx, ServicesGroupedList.tsx
    about/…  gallery/…  testimonials/…  team/…  pricing/…  contact/…  events/…
    shared/  SectionContainer (background/spacing/width), SiteButton, SiteImage (lazy, aspect, shape), Carousel (CSS scroll-snap, no library)
  chrome/
    headers/ HeaderLogoLeft.tsx, HeaderCentered.tsx, HeaderSidebar.tsx …
    footers/ FooterCentered.tsx, FooterColumns.tsx, FooterMinimal.tsx
  editor/                    (Phase 6; Phase 1 has a basic form, see §7)
    editorReducer.ts         pure state: add/remove/duplicate/move/update/applyPreset + dirty tracking
    SiteEditorPage.tsx, SectionList.tsx, SectionSettings.tsx, ThemePanel.tsx, LayoutPicker.tsx, PreviewFrame.tsx
```

- **Registry.** `registry[type][layout]` is a lazily imported component, so a visitor downloads only the layouts the page uses. Adding a layout means one component file plus one registry line (plus the enum value on the backend).
- **Theme.** Theme values become CSS custom properties on the `SitePage` root, and the section components use `var(--site-…)`. A matching MUI theme is built from the same values so MUI buttons, inputs and the existing `EventsSection`/`GallerySection` follow automatically.
- **Theme everywhere (decided).** The published theme (colours, fonts, button/corner/card/image style) applies to **every customer-facing page of the business**: the homepage `/b/{slug}`, the booking wizard `/b/{slug}/book` and the reservation page `/booking/{reference}`. Emails get the colours (§3, publish). `BusinessShell` builds its theme from the published site theme instead of `brandTheme(accent, dark)`. Section layouts and the header/footer variants are homepage-only: the wizard keeps its simple header so the booking flow stays focused. Wizard logic is not touched. The admin panel keeps the platform's own neutral look, as it does today.
- **Performance.** `loading="lazy"` and explicit aspect ratios on images, fonts loaded only for the families in use (`font-display: swap`), section chunks code-split, and the public site document cached with React Query + ETag.
- **Responsiveness.** Every layout is built mobile-first with MUI breakpoints. Per-section `visibility.mobile/desktop` maps to `display: { xs, md }`.
- **Accessibility.** Semantic `<header>/<main>/<section aria-labelledby>/<footer>`, alt text required on content images (an empty value counts as decorative when the user ticks it), keyboard-usable carousel (buttons, not swipe-only), and `prefers-reduced-motion` respected (already the pattern in `Reveal.tsx`).

## 7. Phase notes and decisions

- **The builder lives in Settings → Booking page (decided).** The existing tab is **extended, not replaced**. Its current cards stay where they are:
  - The booking link, logo, socials and the gallery manager still save to the business directly. They are business facts that emails and the footer use too.
  - Cover photo, tagline, about, colour, map and dark background become fields of the **draft** (cover and tagline in the Hero section, about in the About section, colour in the Theme).
  - New cards are added: **Theme** and **Sections**. Sections is a list with up/down buttons, a show/hide toggle, a layout picker and the content fields of each section.
  - A sticky bar at the bottom holds **Save draft / Publish / Discard changes**, with a "you have unpublished changes" note.
  - Phase 6 upgrades this tab: the small preview on the right becomes the live iframe preview, and drag-and-drop and layout thumbnails are added. No separate admin route.
- **Testimonials** have no data in the system, so they are entered in the section content (quote, name, optional photo, rating). **Team** reads employees live by id. Bios and roles are optional per-section overrides because `Employee` has no bio field.
- **Background video** (Hero): uploads accept images only (5 MB, stored in Postgres). The plan is to support **YouTube/Vimeo** (muted, looped embed) and a poster image, not video upload. Storing video in the DB would be a bad idea.
- **Motion without video** (Hero): image heroes can move too. There is a slow zoom on the photo (today's hero already does this), a **slideshow** layout that crossfades through several photos with the zoom, and an animated gradient when there is no photo. All of them stop under `prefers-reduced-motion`.
- **Team data in public.** No public endpoint lists employees, and that is deliberate. The public site response therefore includes only the employees a published Team section references (`id`, name, photo), so nobody the owner did not pick becomes public.
- **Drag and drop** (Phase 6): the floor-plan editor uses hand-rolled pointer dragging, and there is no DnD library. Proposed: **`@dnd-kit/core` + `@dnd-kit/sortable`** (~15 kB gz). Its keyboard sorting and screen-reader announcements cover the "keyboard-usable editor" requirement, which hand-rolled dragging would not. Up/down buttons stay as well.
- **Live preview** (Phase 6): a same-origin iframe (`/admin/website/preview`) that renders `SitePage` with the draft sent via `postMessage`. An iframe is the only way the mobile toggle triggers real mobile breakpoints. This needs `frame-ancestors 'self'` and `X-Frame-Options: SAMEORIGIN` instead of `'none'`/`DENY` (see Risks).
- **Fonts** (Phase 2, decided): only a **curated list of ~16 known fonts** can be chosen. The validator rejects any other name, and the editor offers only the list. The fonts are **self-hosted** from `@fontsource/*` packages (one small package per font, Latin + Latin Extended subsets, which cover Macedonian Latin and most European languages; Cyrillic where the font has it). They are served from our own origin, so there is no CSP change, no visitor data goes to Google, and the fonts are always available. Each page loads only the 1–2 families its theme uses. Planned list: serif headings Playfair Display, Lora, Merriweather, Cormorant Garamond, DM Serif Display; sans Inter, Roboto, Open Sans, Montserrat, Poppins, Lato, Nunito, Work Sans, Raleway; display Oswald, Bebas Neue.
- **Rich text editing** (Phase 5): a small toolbar over `contentEditable` (bold, italic, link, list, heading), no editor library. The server sanitiser is the safety net. If that proves too limited, TipTap is the fallback proposal.
- **Contrast warning**: WCAG contrast ratio computed in the editor for text/background and button text/primary, with a warning below 4.5:1. It does not block saving.
- **Presets** (Phase 7) are defined in the backend (`SitePresets`), so new businesses get one at sign-up and the editor lists them from the API. Applying one replaces theme + header/footer + layouts but **keeps section content and order**, after a confirmation dialog.

## 8. Testing per phase

- Backend unit: `SiteValidatorTest` (each rule), `SiteSchemaMigratorTest` (fixtures per version), `SiteConfigServiceTest` (save/publish/discard/version conflict), `CreateSiteConfigsTest` (legacy → v1 mapping).
- Backend IT: `SiteIT` (draft is invisible publicly until publish, discard, presets), `MultiTenancyIT` additions (business A cannot read/save/publish B's site; ids of B's services/media/employees are refused in A's draft), staff gets 403.
- Frontend: `SectionRenderer.test.tsx` (registry lookup, unknown type/layout skipped, a throwing section does not take the page down), `editorReducer.test.ts` (add/remove/duplicate/reorder/update/dirty), editor save/publish flows with `fakeApi`, `themeVars.test.ts`.
- e2e: existing `booking.spec.ts` must pass unchanged, plus one "edit → publish → visible on /b/slug" spec.
- After every phase: `mvn verify`, `npm run typecheck && npm run lint && npm test`, Playwright.

## 9. Risks

1. **The page must look the same after migration.** Today's page has hand-tuned details (hero animations, grouped price list). Mitigation: dedicated `GROUPED_LIST` / `FULL_WIDTH_IMAGE` layouts that are today's components moved, not rewritten, plus the screenshot check.
2. **CSP changes.** The preview iframe needs `frame-ancestors 'self'`. Embeds need YouTube/Vimeo/Spotify in `frame-src`. (Fonts are self-hosted, so they need no change.) Each widening is small but should be a conscious decision.
3. **OpenAPI polymorphism.** The sealed content types must generate a usable `oneOf` for `api.generated.ts`. If springdoc's output is poor, the contract check for `SiteDocument` covers the root only, and the section union is checked by a backend JSON-fixture test that the frontend tests also load.
4. **Scope.** Phase 1 alone is ~30 layout components. They share `SectionContainer`, `SiteButton`, `SiteImage` and `Carousel`, so most layouts are 60–120 lines. Still, Phase 1 is the largest chunk.
5. **Emails keep using `accent_color`/`dark_theme`.** Syncing on publish keeps them consistent. Fonts and other theme options are not applied to emails, because email clients ignore most web fonts.
6. **The wizard changes look with the theme.** Its logic is untouched, but a sharp-cornered or outline-button theme changes how it looks. The existing wizard tests and e2e run against the default and one non-default theme.
7. **Document size.** Capped by the section/item limits (≈ well under 100 kB), so it is fine to load whole on every public page view.

## 10. Open questions

All decided:
- Fonts are a curated, self-hosted list (§7).
- The theme applies to all of the business's customer pages (§6).
- The builder extends Settings → Booking page (§7).
- Hero video is a YouTube/Vimeo embed, plus image motion (§7).
- OWASP Java HTML Sanitizer and `@dnd-kit` are approved.
