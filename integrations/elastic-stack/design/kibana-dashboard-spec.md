
# Kibana Dashboard Specification

> **Project:** NeuroAssist AI
> **Kibana version:** 9.4.1
> **Kibana URL (local):** http://localhost:5601
> **D-273:** Fill one copy of this document per dashboard.

---

## Artifact Identity

```yaml
artifact_name:     kibana-dashboard-<DASHBOARD_SLUG>
artifact_category: design
artifact_role:     Kibana dashboard panel and visualization specification
artifact_dependencies:
  - index-design-<INDEX_SLUG>.md    # indices powering this dashboard
  - data-contract.md                # field availability and types
artifact_consumers:
  - Kibana                          # runtime dashboard
  - stakeholder reports             # exported visualizations
```

---

## Execution Contract

Use this template **once per dashboard** in `NeuroAssist AI`. A dashboard
is a collection of panels (visualizations + controls) that answers a coherent set of
operational or analytical questions. Fill every section before creating the dashboard
in Kibana. Save the completed spec in `docs/architecture/<module>/design/`.

---

## Input Schema

```yaml
dashboard_name:          # human-readable title shown in Kibana
dashboard_slug:          # kebab-case identifier (for file naming)
domain_context:          # module or bounded context this dashboard serves
target_audience:         # who uses this dashboard (ops team, analyst, exec, ...)
primary_questions:       # list: what operational questions must this answer?
indices:                 # list: index patterns used (e.g. neuroassist-ai_*)
refresh_interval:        # auto-refresh (or "manual")
default_time_range:      # e.g. "Last 7 days", "Last 30 days", "This year"
```

---

## Output Schema

```yaml
dashboard_title:         # final title (may differ from name)
data_views:              # list of Kibana data views required
panels:                  # list: each panel has type, title, config, and position
global_filters:          # filters applied to all panels
drilldowns:              # interactive links between panels
export_format:           # how this dashboard is exported/shared (screenshot, PDF, CSV)
```

---

## Generation Rules

1. **Answer the primary questions.** Every panel must be traceable to at least one
   question in the Input Schema. Remove panels that do not answer a question.

2. **One data view per index pattern.** Do not mix multiple unrelated indices in
   one data view. Create separate data views for dimension and fact indices.

3. **Use Lens for metrics and charts.** Prefer Lens over TSVB or Vega unless a
   specific visualization type requires it.

4. **Geo panels use Maps app.** For geographic data (participants, locations), use
   the Kibana Maps application with a `geo_point` field as the layer source.

5. **Controls before detail.** Place filter controls (Input controls: dropdown,
   date range) at the top. Detail panels (data tables, time series) below.

6. **Avoid high-cardinality terms.** Limit `terms` aggregation top-N to ≤ 100.
   High-cardinality fields (raw identifiers, UUIDs) are not suitable for pie charts.

7. **Export decisions.** Decide whether this dashboard is exported as:
   - Screenshot/PDF (Kibana Reporting) for static reports
   - CSV (Discover) for data extracts
   - Saved Objects export (`.ndjson`) for version control and environment promotion

---

## §1 — Dashboard Overview

**Name:** _(fill in)_

**Slug:** _(fill in)_

**Domain context:** _(fill in: which module or business process this serves)_

**Target audience:** _(fill in: ops team / analyst / executive / developer)_

**Primary questions this dashboard must answer:**
1. _(question 1)_
2. _(question 2)_
3. _(question 3)_

---

## §2 — Data Views

List the Kibana data views required. Create them before building panels.

| Data view name | Index pattern | Time field | Notes |
|---|---|---|---|
| _(name)_ | `neuroassist-ai_*` | `ingested_at` | _(notes)_ |

---

## §3 — Panel Specifications

Describe each panel. Copy the block below for each panel.

---

### Panel: _(Panel Title)_

| Attribute | Value |
|---|---|
| **Type** | _(Lens / Maps / Metric / Data table / TSVB / Markdown)_ |
| **Data view** | _(data view name from §2)_ |
| **Time range** | _(inherit dashboard / custom)_ |
| **Grid position** | Row _(N)_, Column _(N)_, Width _(N/12)_, Height _(N)_ |

**Visualization config:**

```yaml
visualization_type:   # bar / line / pie / donut / metric / table / map / treemap
x_axis:               # field (for bar/line) or "time"
y_axis:               # metric (count / sum / avg / cardinality)
breakdown_by:         # field for color/series split (or "none")
top_n:                # max buckets (if terms aggregation)
sort:                 # field and direction
filters:              # panel-level filters (applied on top of global filters)
```

**Purpose:** _(one sentence: what question does this panel answer?)_

---

## §4 — Global Filters and Controls

Controls applied to all panels in the dashboard.

| Control | Type | Field | Options |
|---|---|---|---|
| Date range | Date picker | `ingested_at` | Default: Last 7 days |
| _(control name)_ | _(dropdown / range / text)_ | _(field)_ | _(options)_ |

---

## §5 — Drilldowns

Describe interactive drilldowns between panels (optional).

| From panel | Trigger | Target panel or URL | Notes |
|---|---|---|---|
| _(panel name)_ | Click on bar/bucket | _(target)_ | _(notes)_ |

---

## §6 — Export and Promotion

**Export format:** _(screenshot/PDF / CSV / Saved Objects .ndjson)_

**Environment promotion path:**
1. Build and validate in local (Docker stack)
2. Export as Saved Objects: `Stack Management → Saved Objects → Export`
3. Import in staging: `Stack Management → Saved Objects → Import`
4. Validate in staging before promoting to production

**Version control:** Save the exported `.ndjson` file in
`docs/architecture/<module>/design/kibana-<DASHBOARD_SLUG>.ndjson`.

---

## §7 — Known Limitations

_(List any known limitations of this dashboard: e.g., "does not support
multi-tenancy filtering", "refresh interval limited by ES scroll timeout",
"geo panel requires browser WebGL support".)_
