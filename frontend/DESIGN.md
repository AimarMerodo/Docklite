## Overview

Docklite's app canvas is a deep dark surface — `{colors.canvas}` is #010204, near-pure black with a faint blue tint that nods to the Docker brand. On top sits a four-step surface ladder (`{colors.surface-1}` through `{colors.surface-4}`) for cards, panels, lifted tiles, and the sidebar, with hairline borders running from `{colors.hairline}` (#23252a) up through `{colors.hairline-strong}` and `{colors.hairline-tertiary}`. Light gray text (`{colors.ink}` #f7f8f8) carries the body and headlines.

The single chromatic accent is **Docker Blue** `{colors.primary}` (#2496ED) — used on the brand mark, focus rings, the primary CTA button, and active sidebar items. A lighter hover state (`{colors.primary-hover}` #4DAEF1) and a focus-tinted variant (`{colors.primary-focus}` #2391E8) extend the same hue. Docklite avoids saturated greens, oranges, reds, etc. on the chrome — the only semantic color is `{colors.semantic-success}` (#27a644) for the "running" status pill on container rows.

Display type runs **Inter** at weight 500–700 with negative letter-spacing scaling from -3.0px at 80px down to 0 at body. Body type is also **Inter** at 400. **JetBrains Mono** is reserved for log output, container IDs, image digests, and any code-like token.

The page rhythm is **dense data-driven panels** — Docklite's app leads with high-fidelity tables and cards of live container state (container list, image registry, log viewer, stats dashboard) framed in `{colors.surface-1}` panels with `{rounded.lg}` 12px corners. The chrome is intentionally minimal so the operational data does the heavy lifting.

**Key Characteristics:**
- **Dark-canvas app system** — `{colors.canvas}` (#010204) is the anchor surface; no light theme.
- **Docker blue brand accent** (`{colors.primary}` #2496ED) — used scarcely on brand mark, focus, primary CTA, and active nav state.
- Four-step surface ladder (canvas → surface-1 → surface-2 → surface-3 → surface-4) carries hierarchy without shadow.
- Display tracking pulls aggressively negative (-3.0px at 80px); body holds at -0.05px.
- Cards use `{rounded.lg}` 12px corners with 1px hairline borders — never pill, rarely 16px.
- **Live operational data** dominates the page. The chrome is a dark frame for the container state.
- No second chromatic color. No atmospheric gradients. No spotlight cards.

## Colors

> Source pages (planned): /login, /dashboard, /containers, /images, /networks, /volumes, /stacks, /settings.

### Brand & Accent
- **Docker Blue** ({colors.primary}): The signature Docklite accent — primary CTA, brand mark, link emphasis, active sidebar item.
- **Docker Blue Hover** ({colors.primary-hover}): Lighter blue (#4DAEF1) — hovered state of the primary CTA.
- **Docker Blue Focus** ({colors.primary-focus}): Focus-ring tint (#2391E8) — focused inputs, focused buttons.
- **Brand Secure** ({colors.brand-secure}): Muted blue-gray (#5A7B96) — used in security / auth surfaces.

### Surface
- **Canvas** ({colors.canvas}): Default page background — #010204, near-pure black with a faint blue tint.
- **Surface 1** ({colors.surface-1}): One step above canvas — container cards, image cards, log panels, sidebar background.
- **Surface 2** ({colors.surface-2}): Two steps above — featured cards, hovered rows, modal background.
- **Surface 3** ({colors.surface-3}): Three steps above — sub-nav, dropdown menus, command palette.
- **Surface 4** ({colors.surface-4}): Four steps above — deepest lifted surface, tooltips.
- **Hairline** ({colors.hairline}): 1px borders on cards and dividers.
- **Hairline Strong** ({colors.hairline-strong}): Stronger 1px borders — input focus rings, selected row outline.
- **Hairline Tertiary** ({colors.hairline-tertiary}): Tertiary borders for nested surfaces.
- **Inverse Canvas** ({colors.inverse-canvas}): Pure white — surface of the inverse pill CTA on the rare empty-state hero.
- **Inverse Surface 1** ({colors.inverse-surface-1}): One step above inverse canvas.
- **Inverse Surface 2** ({colors.inverse-surface-2}): Two steps above inverse canvas.

### Text
- **Ink** ({colors.ink}): All headlines and emphasized body type — light gray #f7f8f8.
- **Ink Muted** ({colors.ink-muted}): Secondary type at #d0d6e0 — meta info, container metadata.
- **Ink Subtle** ({colors.ink-subtle}): Tertiary type at #8a8f98 — deselected tabs, sidebar inactive items, footer columns.
- **Ink Tertiary** ({colors.ink-tertiary}): Quaternary at #62666d — disabled, footnotes, timestamps.

### Semantic
- **Success Green** ({colors.semantic-success}): Used on the "running" status pill and successful action toasts. The only semantic color on the chrome.
- **Overlay** ({colors.semantic-overlay}): Pure black overlay scrim for modals and command palette backdrop.

## Typography

### Font Family

- **Inter** — The display and text family. Carries everything from `{typography.display-xl}` down to `{typography.caption}`. Loaded with weights 400 / 500 / 600 / 700. Fallback `-apple-system, system-ui, Segoe UI, Roboto`.
- **JetBrains Mono** — The mono family. Used for log output, container IDs, image digests, env vars, shell commands, and any code-like token. Fallback `ui-monospace, SF Mono, Menlo`.

The app surface treats Inter as one continuous voice across display and body — no family switch, only weight and tracking shifts.

### Hierarchy

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| `{typography.display-xl}` | 80px | 600 | 1.05 | -3.0px | Empty-state hero headline |
| `{typography.display-lg}` | 56px | 600 | 1.10 | -1.8px | Auth screen / onboarding headline |
| `{typography.display-md}` | 40px | 600 | 1.15 | -1.0px | Section opener (Dashboard "Overview", "Containers") |
| `{typography.headline}` | 28px | 600 | 1.20 | -0.6px | Page title, modal heading |
| `{typography.card-title}` | 22px | 500 | 1.25 | -0.4px | Container card title, stats card title |
| `{typography.subhead}` | 20px | 400 | 1.40 | -0.2px | Section intro, modal subtitle |
| `{typography.body-lg}` | 18px | 400 | 1.50 | -0.1px | Lead paragraphs, empty-state body |
| `{typography.body}` | 16px | 400 | 1.50 | -0.05px | Default body |
| `{typography.body-sm}` | 14px | 400 | 1.50 | 0 | Table row body, card body, sidebar items |
| `{typography.caption}` | 12px | 400 | 1.40 | 0 | Captions, meta, status, timestamps |
| `{typography.button}` | 14px | 500 | 1.20 | 0 | All button labels |
| `{typography.eyebrow}` | 13px | 500 | 1.30 | 0.4px | Section eyebrow / table column header |
| `{typography.mono}` | 13px | 400 | 1.50 | 0 | JetBrains Mono for logs, IDs, commands |

### Principles

- **Aggressive negative tracking on display** (-3.0px at 80px ≈ 4% of size).
- **Single voice from display to body.** Display-xl at 600 → body at 400 — same family, narrower weights.
- **Eyebrow uses positive tracking** (+0.4px) — contrast against the negative-tracked display marks the eyebrow as taxonomy.
- **Mono only in operational contexts.** JetBrains Mono lives inside log viewers, container IDs, env tables — not on chrome.

### Note on Font Loading

Inter is loaded as a variable font (Inter var, weights 100–900) from a self-hosted woff2 to avoid third-party CDN dependency on the self-hosted Docklite instance. JetBrains Mono is loaded the same way.

## Layout

### Spacing System

- **Base unit**: 4px.
- **Tokens (front matter)**: `{spacing.xxs}` 4px · `{spacing.xs}` 8px · `{spacing.sm}` 12px · `{spacing.md}` 16px · `{spacing.lg}` 24px · `{spacing.xl}` 32px · `{spacing.xxl}` 48px · `{spacing.section}` 96px.
- Card interior padding: `{spacing.lg}` 24px on container/image/stats cards; `{spacing.xl}` 32px on modal panels; `{spacing.xxl}` 48px on empty-state heroes.
- Pill button padding: 8px vertical · 14px horizontal — Docklite's compact button spec.
- Form input padding: 8px vertical · 12px horizontal.
- Table row padding: 12px vertical · 16px horizontal.

### Grid & Container

- Max content width sits around 1440px with a fluid sidebar at 240px left.
- Card grids are 3-up at desktop, 2-up at tablet, 1-up at mobile.
- Stats dashboard is 4-up (CPU, Memory, Network, Disk) collapsing to 2-up at tablet.
- Container list is a full-width table — it's the protagonist on `/containers`.
- Log viewer spans full content width with mono type and a fixed height with internal scroll.

### Whitespace Philosophy

The dark canvas IS the whitespace. Sections separate by lift onto surface-1 panels, not by gaps in white. Within a panel, generous `{spacing.lg}` 24px gaps between content blocks; `{spacing.section}` 96px between full sections on long pages.

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 (flat) | No shadow, no border | Default for body type, page background, log lines |
| 1 (charcoal lift) | `{colors.surface-1}` background on canvas, 1px `{colors.hairline}` | Default cards, sidebar, container rows |
| 2 (surface-2 lift) | `{colors.surface-2}` background, 1px `{colors.hairline-strong}` | Hovered/selected rows, modal panel, featured stats card |
| 3 (surface-3 lift) | `{colors.surface-3}` background | Sub-nav, dropdown menus, command palette |
| 4 (focus ring) | 2px `{colors.primary-focus}` outline at 50% opacity | Focused input, focused button |

Docklite's depth is carried by surface ladder + hairline borders. The brand resists drop shadows on dark almost entirely.

### Decorative Depth

- **Live container state and log streams** dominate as visual weight.
- **No atmospheric gradients, no spotlight cards.**
- **Subtle white edge highlight** on the top edge of lifted panels — gives the dark surface a faint "pixel rendered" feel.

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.xs}` | 4px | Status pills, small chips |
| `{rounded.sm}` | 6px | Inline tags, port badges |
| `{rounded.md}` | 8px | All buttons, form inputs, table row hover area |
| `{rounded.lg}` | 12px | Container cards, stats cards, modal panels, sidebar group |
| `{rounded.xl}` | 16px | Log viewer container, terminal/exec panel |
| `{rounded.xxl}` | 24px | Oversized empty-state heroes (rare) |
| `{rounded.pill}` | 9999px | Tab toggles, status pills |
| `{rounded.full}` | 9999px | Avatar circles, action icon backgrounds |

### Iconography & Glyphs

- Icons use **Lucide** at 16px / 20px / 24px on `{colors.ink-muted}` by default.
- Active sidebar item swaps icon color to `{colors.primary}`.
- Container engine icons (Docker, Compose) render at 24px on `{colors.canvas}` with no border.
- Avatar circles for user accounts use `{rounded.full}` at 32–40px.

## Components

### Buttons

**`button-primary`** — Docker-blue CTA. The default primary CTA across all pages.
- Background `{colors.primary}`, text `{colors.on-primary}`, type `{typography.button}`, padding 8px 14px, rounded `{rounded.md}`.
- Pressed state lives in `button-primary-pressed` (background shifts to `{colors.primary-focus}`).
- Hover state lives in `button-primary-hover` (background shifts to `{colors.primary-hover}` lighter blue).

**`button-secondary`** — Charcoal button. Used for secondary CTAs ("Cancel", "View logs").
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.button}`, padding 8px 14px, rounded `{rounded.md}`. 1px `{colors.hairline}` border.

**`button-tertiary`** — Plain text button.
- Background `{colors.canvas}`, text `{colors.ink}`, type `{typography.button}`, rounded `{rounded.md}`, padding 8px 14px.

**`button-icon`** — Square icon-only action button (start, stop, restart, delete).
- Background `{colors.surface-1}`, icon `{colors.ink-muted}`, rounded `{rounded.md}`, padding 8px. Hover lifts to `{colors.surface-2}` with icon to `{colors.ink}`.

**`button-destructive`** — Used for "Remove container", "Delete image", confirmation dialogs.
- Background `{colors.surface-1}`, text `{colors.ink}`, rounded `{rounded.md}`, padding 8px 14px. 1px `{colors.hairline}` border. Text shifts to a muted red only on the confirmation modal — no red on chrome.

### Tabs & Navigation

**`tab-default`** + **`tab-selected`** — Pill-toggle on resource detail pages (Logs / Inspect / Stats / Console).
- Default: `{colors.canvas}` background, `{colors.ink-subtle}` text, rounded `{rounded.pill}`, padding 6px 14px.
- Selected: `{colors.surface-2}` background, `{colors.ink}` text — selected = surface lift.

**`sidebar-item-default`** + **`sidebar-item-active`** — Left sidebar navigation entry.
- Default: transparent background, `{colors.ink-subtle}` text + icon, type `{typography.body-sm}`, padding 8px 12px, rounded `{rounded.md}`.
- Active: `{colors.surface-2}` background, `{colors.ink}` text, `{colors.primary}` icon.

### Cards & Containers

**`container-card`** — Each container on `/containers` (grid view).
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.body}`, rounded `{rounded.lg}`, padding 24px. 1px `{colors.hairline}` border.
- Hovered state lifts to `{colors.surface-2}` with `{colors.hairline-strong}` border.

**`container-row`** — Each container in the dense table view.
- Background `{colors.canvas}`, text `{colors.ink}`, type `{typography.body-sm}`, rounded `{rounded.md}`, padding 12px 16px. 1px `{colors.hairline}` bottom rule.
- Hovered state lifts to `{colors.surface-1}`.
- Selected state uses `{colors.surface-2}` background.

**`stats-card`** — CPU / Memory / Network / Disk tile on `/dashboard`.
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.body}`, rounded `{rounded.lg}`, padding 24px.
- Numeric value uses `{typography.display-md}` weight 600.

**`image-card`** — Each image on `/images`.
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.body}`, rounded `{rounded.lg}`, padding 24px. Image digest renders in `{typography.mono}`.

**`log-viewer-panel`** — Streaming log output.
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.mono}`, rounded `{rounded.xl}`, padding 24px. Scrollable region with sticky header showing follow-tail toggle.

**`exec-terminal-panel`** — Interactive shell into a container.
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.mono}`, rounded `{rounded.xl}`, padding 24px.

**`empty-state-card`** — Shown when a resource list has zero items.
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.headline}` for the title and `{typography.body}` for the description, rounded `{rounded.lg}`, padding 48px.

**`modal-panel`** — Confirmation dialogs, "Create container", "Pull image".
- Background `{colors.surface-2}`, text `{colors.ink}`, type `{typography.body}`, rounded `{rounded.lg}`, padding 32px. 1px `{colors.hairline-strong}` border.

### Inputs & Forms

**`text-input`** + **`text-input-focused`** — Form fields on `/login`, "Create container", "Pull image" modals.
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.body}`, rounded `{rounded.md}`, padding 8px 12px. 1px `{colors.hairline}` border.
- Focused state retains the same surface; the focus ring is a 2px `{colors.primary-focus}` outline at 50% opacity.

**`select-input`** — Dropdown for image tag, network mode, restart policy.
- Same spec as `text-input`. Dropdown panel uses `{colors.surface-3}` with `{rounded.md}` 8px corners.

**`checkbox`** — Used in compose options, table multi-select.
- Default: `{colors.canvas}` background, 1px `{colors.hairline-strong}` border, rounded `{rounded.xs}`.
- Checked: `{colors.primary}` background, white check glyph.

### Status & Live Data

**`status-pill-running`** — Green pill on running containers.
- Background tinted `{colors.semantic-success}` at ~15% opacity, text `{colors.semantic-success}`, type `{typography.caption}`, rounded `{rounded.pill}`, padding 2px 8px. A 6px solid dot precedes the label.

**`status-pill-stopped`** + **`status-pill-paused`** + **`status-pill-restarting`** + **`status-pill-exited`** — Non-running states.
- Background `{colors.surface-2}`, text `{colors.ink-muted}`, type `{typography.caption}`, rounded `{rounded.pill}`, padding 2px 8px. The leading dot color shifts: gray for stopped, ink-muted for paused, ink-muted with pulsing animation for restarting.
- No saturated red/orange — the absence of green communicates not-running.

**`port-badge`** — Inline port mapping pill (e.g. `8080 → 80/tcp`).
- Background `{colors.surface-2}`, text `{colors.ink-muted}`, type `{typography.mono}`, rounded `{rounded.sm}`, padding 2px 6px.

**`log-row`** — A single line in the log viewer.
- Background `{colors.surface-1}`, text `{colors.ink}`, type `{typography.mono}`, rounded `{rounded.xs}`, padding 4px 0. Timestamp prefix uses `{colors.ink-tertiary}`.

### Top Navigation

**`top-nav`** — Sticky dark bar with the Docklite wordmark left, breadcrumb center, and a global search trigger + user avatar pair right.
- Background `{colors.canvas}`, text `{colors.ink}`, type `{typography.body-sm}`, height 56px. 1px `{colors.hairline}` bottom rule.

**`sidebar`** — Persistent left rail with section groups (Compute, Network, Storage, Settings).
- Background `{colors.surface-1}`, text `{colors.ink-subtle}`, type `{typography.body-sm}`, width 240px. Group header in `{typography.eyebrow}` `{colors.ink-tertiary}`.

### Toasts

**`toast`** — Action confirmation ("Container started", "Image pulled").
- Background `{colors.surface-3}`, text `{colors.ink}`, type `{typography.body-sm}`, rounded `{rounded.lg}`, padding 12px 16px. 1px `{colors.hairline-strong}` border. Lives in the bottom-right corner.

## Do's and Don'ts

### Do

- Reserve `{colors.canvas}` (#010204) as the system's anchor surface — the faint blue tint is intentional.
- Use `{colors.primary}` Docker blue ONLY for: brand mark, primary CTA, focus ring, link emphasis, active sidebar item.
- Use the four-step surface ladder for hierarchy. Avoid skipping levels.
- Pair display weight 600 with body weight 400 — Docklite resists 700+ display weights.
- Apply negative letter-spacing aggressively on display.
- Use live container state (status pills, stats, logs) as the protagonist of every section.
- Compose CTAs as `{rounded.md}` 8px corners.
- Render every container ID, image digest, env var, and log line in JetBrains Mono.

### Don't

- Don't ship a light-mode app.
- Don't use Docker blue as a section background or card fill.
- Don't introduce a second chromatic accent (orange, pink, purple for chrome).
- Don't add atmospheric gradients or spotlight cards.
- Don't pill-round CTAs.
- Don't use `#000000` true black as the canvas.
- Don't use saturated red/orange/yellow on container status pills — only the running pill is colored; non-running states communicate by absence of green, not by alarm color.
- Don't mix Inter and a second sans family.

## Responsive Behavior

### Breakpoints

| Name | Width | Key Changes |
|---|---|---|
| Desktop-XL | 1440px | Default desktop layout, 240px sidebar |
| Desktop | 1280px | Sidebar maintained, card grid 3-up |
| Tablet | 1024px | Sidebar collapses to icon-only rail (64px), card grid 3-up → 2-up |
| Mobile-Lg | 768px | Sidebar becomes a slide-in drawer; stats grid 4-up → 2-up |
| Mobile | 480px | Single-column; container table becomes vertical cards; display-xl scales 80px → ~36px |

### Touch Targets

- CTAs hold ≥40px tap height across viewports.
- Tab pills hold ≥36px tap height; touch viewports grow to ≥44px.
- Form inputs hold ≥44px tap target on touch.
- Sidebar items hold ≥40px row height.

### Collapsing Strategy

- **Sidebar**: 240px full → 64px icon-only at 1024px → drawer below 768px.
- **Container table**: dense rows → vertical cards below 768px.
- **Stats grid**: 4-up → 2-up at 1024px → 1-up below 480px.
- **Tabs on detail pages**: stay horizontal but enable horizontal scroll below 480px.
- **Display type**: `{typography.display-xl}` 80px scales toward `{typography.display-md}` 40px on mobile.

### Live Data Behavior

- Log viewer keeps its full height on desktop; on mobile it caps at ~50vh with full-screen expand toggle.
- Stats charts re-render on resize without losing the current sample window.

## Iteration Guide

1. Focus on ONE component at a time and reference it by its `components:` token name.
2. When introducing a section, decide first which surface lift it lives on.
3. Default body to `{typography.body}` at weight 400.
4. Run `npx @google/design.md lint DESIGN.md` after edits.
5. Add new variants as separate component entries.
6. Treat Docker blue as scarce: brand mark, primary CTA, focus, link emphasis, active sidebar item.
7. Lead every screen with live container state — not decorative chrome.

## Known Gaps

- The four-step surface ladder values are inherited from Linear's spec as a starting point; a final pass on `surface-2` through `surface-4` against real Docklite mockups is pending.
- Form-field error and validation styling is not yet documented.
- Light mode is intentionally not documented because Docklite ships dark-only.
- The "destructive action" pattern uses muted red only inside confirmation modals; full token for the destructive state is not yet defined here.
- Compose stack visualization (graph view of services and links) is not yet specced.
- The command palette (⌘K) shape is anticipated but its full component spec is pending.