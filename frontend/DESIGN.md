# Design direction — Claims Management

> **Status:** established retroactively for **S-6 (Reports)**. There was no
> `DESIGN.md` before. This documents the direction the product already ships in
> `frontend/src/styles.css`, names its scales and tokens, adds the two things the
> base sheet lacks (**motion** and a **designed dark theme**), and specifies the
> **Reports page** component inventory with every interaction state.

## 0. Reference & direction

This is a **brownfield** design. The reference is the product itself — the
`styles.css` token set and the `DashboardPage` / `ClaimsListPage` patterns. The
Reports page must look like one more screen of the same application, not a new
product bolted on.

**Direction name: "Calm clinical ledger."** Neutral near-white surfaces, one
calm blue accent, dense-but-legible tables with tabular figures, semantic colour
used only where it carries meaning (claim status). Enterprise healthcare-billing
tone: trustworthy, quiet, figure-first. Closest external kin: Linear/Stripe
dashboard calm at a slightly higher data density.

Rule for everyone building Reports screens: **read a token, never a literal.**
A hard-coded colour, pixel size or radius in a component is a defect. All tokens
live in `styles.css` (`:root`) and `design/reports-tokens.css` (extensions).

## 1. Typeface & type scale

- **One family:** system UI sans — `ui-sans-serif, -apple-system, "Segoe UI",
  Roboto, "Helvetica Neue", Arial, sans-serif` (`--font`). Monospace
  (`--mono`) is reserved for claim numbers, MRNs and POS codes. **No serif.**
- **Scale** (px), size **and** weight **and** colour distinguish levels:

  | Token | Size | Weight | Use |
  |---|---|---|---|
  | `--fs-stat` | 24 | 650 | stat-card value |
  | `--fs-h1` | 20 | 650 | page title (`h1`) |
  | `--fs-h2` | 16 | 650 | card / section title (`h2`) |
  | `--fs-body` | 14 | 400 | body, table cells (base) |
  | `--fs-small` | 12 | 550 | field labels, notes |
  | `--fs-caption` | 11 | 600 | uppercase table headers, badges, stat labels |

- Body line-height 1.5; headings `letter-spacing: -.01em`; stat values `-.02em`.
- Secondary information is **muted** (`--text-muted` / `--text-faint`), not just
  smaller.

## 2. Spacing & layout

- **4px base scale**, named in `reports-tokens.css`: `--space-1..12` =
  4 / 8 / 12 / 16 / 24 / 32 / 48. Gaps come from the scale — never ad hoc.
- Page content: `.content` padding `22px 24px 48px`; cards group by proximity
  with 16px grid gaps (`.grid`, `.grid-2`, `.grid-4`).
- Tables are dense (`th` 9×14, `td` 11×14) with 1px dividers; numerics are
  right-aligned with `font-variant-numeric: tabular-nums` (`.num`).
- Responsive breakpoints already in the system: `grid-4` → 2-up at ≤1100px,
  everything → 1-up at ≤820px. The detail table scrolls horizontally inside
  `.table-scroll` on narrow screens rather than squashing.

## 3. Colour — light & dark

**One accent** (`--accent #1f5fd6`) for primary actions, links and focus.
Neutral surfaces; borders are subtle 1px lines. Semantic colours carry meaning
only (claim status badges, alerts). At most a handful of hues per screen.

| Role | Light | Dark (`data-theme="dark"`) |
|---|---|---|
| `--bg` | `#f6f7f9` | `#0b1017` |
| `--surface` | `#ffffff` | `#131b26` |
| `--surface-2` | `#f1f3f6` | `#1b2531` |
| `--border` | `#dfe3e9` | `#26313f` |
| `--text` | `#131a24` | `#e7ecf3` |
| `--text-muted` | `#5c6879` | `#9aa7b8` |
| `--accent` | `#1f5fd6` | `#5b8cff` |
| `--ok` / `--warn` / `--danger` / `--info` | `#13795b` / `#8a5a06` / `#b32020` / `#2a5c9a` | `#4cc79a` / `#e0a94a` / `#f0776f` / `#6fa2e0` |

**Dark mode is designed, not inverted** (`reports-tokens.css`): depth comes from
lighter surfaces, the accent is lifted for contrast on dark, shadows soften and
`*-soft` badge fills are re-mixed as dim tints. It engages via
`<html data-theme="dark">` or the OS `prefers-color-scheme`. Body text contrast
≥ 4.5:1 in both themes; no pure black on pure white.

## 4. Radii, elevation, motion

- **Radii (two):** `--radius 8px` (cards, panels, modals), `--radius-s 5px`
  (buttons, inputs, badges use a full pill `999px`).
- **Elevation (three):** `--elev-0` none (flat rows), `--elev-1` = `--shadow`
  (cards), `--elev-2` = `--shadow-lg` (modals, login). Cards and panels share
  the same language.
- **Motion (new):** one duration set — `--motion-fast 120ms`,
  `--motion-base 180ms`, `--motion-enter 220ms` — one easing `--ease-out`.
  Hover/press/focus transition on colour and shadow only; the skeleton shimmer
  and export spinner are the only continuous motions. **`prefers-reduced-motion`
  is honoured** globally.
- **Focus (new):** one designed ring — `--focus-ring` = `0 0 0 3px
  var(--accent-soft)` plus accent border — applied on `:focus-visible` to every
  control. No browser default outline ships.

## 5. Component inventory — Reports page

The page (`pages/ReportsPage.tsx`) is assembled entirely from existing system
components; **no new component library, no bespoke CSS per element.** Layout,
top to bottom: **PageHeader (title + export group) → period picker card →
summary stat cards → two summary tables (By status / By place of service) →
claim detail table.**

### 5.1 Period picker
`.filters` row inside a `.card`, reusing `.field` + native `<select>` (36px
control height).
- **Period toggle** — `<select>`: `Quarter` | `Full year`.
- **Year selector** — `<select>`, last 8 years, current year default.
- **Quarter selector** — `<select>` `Q1–Q4`, **shown only when period=Quarter**
  (hidden for full year, matching the backend contract).
- **Period caption** — inline `.reports-period-note`: "Covering **Q3 2026**
  (1 Jul 2026 – 30 Sep 2026)", from the server-derived window.
- Reflects to the URL query (`period`, `year`, `quarter`) so a report is
  shareable/bookmarkable.

States: default; `:hover` border `--border-strong`; `:focus-visible` accent
ring; `:disabled` muted while a report loads. Selecting any control re-runs the
report (no separate "Run" button — the report is the page).

### 5.2 Summary stat cards
`.grid.grid-4` of `.card.stat`, identical to Dashboard: **Total claims**,
**Charged**, **Collected** (with collection-rate note), **Outstanding AR**.
Label = `--fs-caption` uppercase muted; value = `--fs-stat`; note = `--fs-small`
muted. Empty period → all values read `0` / `$0.00`, still valid.

### 5.3 Summary tables
Two `Card`s side by side (`.grid.grid-2`): **By status** (StatusBadge, Claims,
Charged, Paid) and **By place of service** (POS code mono, Claims, Charged,
Paid). `.num` tabular right-aligned money. Zero rows → inline `EmptyState`
("No claims in this period"), not a blank card.

### 5.4 Detail table
`Card` titled "Claim detail" with a row-count caption action, wrapped in
`.table-scroll`. Columns: Claim number (mono), Patient (+ MRN faint), Payer,
Provider (muted), Service dates (`DateText`, range collapses when from=to),
Charged / Paid / Outstanding (`.num` `Money`), Status (`StatusBadge`). Sticky
`th`, 1px dividers, no per-row elevation.

### 5.5 Export buttons (CSV · Excel · PDF)
A `.reports-export` group in the PageHeader `actions` slot — three
`.btn.btn-secondary.btn-sm`, `min-width 108px`, `role="group"
aria-label="Export report"`. They are the **secondary** actions; the picker is
the primary interaction, so they are quiet outline buttons, not filled.

| State | Appearance |
|---|---|
| **Default** | outline secondary button, label "Export CSV / Excel / PDF" |
| **Hover** | `--surface-2` fill, 180ms ease-out |
| **Focus** | `--focus-ring` (3px accent ring), keyboard-reachable |
| **Loading** | `aria-busy="true"`: label → "Exporting…", 12px accent spinner via `::before`, `cursor: progress`, pointer-events off |
| **Disabled** | `opacity .5`, `not-allowed` — when there is no detail to export, **or** while any sibling export is running (only one download at a time) |

Filename encodes the period: `claims-report-Q3-2026.csv` / `-FY2026.xlsx`.
A failed export surfaces an inline `ErrorAlert` above the card (specific,
recoverable), never a silent failure.

## 6. States — every screen state is designed

- **Loading:** skeleton in the final layout (`.skeleton` shimmer on stat cards
  and table rows), not a spinner in a void. `Loading` label "Building report".
- **Empty period:** valid zeroed report — stat cards show `0`, summary tables
  and detail show `EmptyState` with the next action ("Pick another quarter or
  year, or switch to a full-year report"). This is a **200, not an error.**
- **Error:** inline `ErrorAlert` (RFC 9457 `detail` from the API) above the
  content — e.g. invalid input returns 400 problem+json and is shown as a
  specific, recoverable message.
- **Success (export):** the browser download is the confirmation; the button
  returns from "Exporting…" to its label.

## 7. Accessibility & copy

- Every control keyboard-reachable with a **visible focus ring**; export group
  is a labelled `role="group"`; select controls have associated `<label>`s.
- Touch targets ≥ 36px (control height set), ≥ 40px spacing on the export row.
- **Copy:** labels say what they do ("Period", "Full year", "Export Excel");
  empty states say what goes here and the next action; errors say what to fix.
  No placeholder/lorem text ships.

## 8. Files

| File | Role |
|---|---|
| `frontend/src/styles.css` | base token set + all shared components (light) — the system of record |
| `frontend/src/design/reports-tokens.css` | **this direction's extensions** — motion, focus ring, named type/space tokens, **designed dark theme**, Reports export/skeleton states. Additive; loaded after `styles.css`. |
| `frontend/DESIGN.md` | this document |
