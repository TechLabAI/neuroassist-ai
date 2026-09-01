#!/usr/bin/env python3
"""Remote Kibana dashboard provisioning — TechLab Framework (D-425).

Mounts Kibana dashboards *remotely*, without clicking the UI:

  1. waits until Kibana reports `available`;
  2. creates one data view per index via the stable Data Views API
     (`POST /api/data_views/data_view`, `override:true` → idempotent);
  3. builds a dashboard saved object (with Lens panels) in memory and
     creates it via the Saved Objects API
     (`POST /api/saved_objects/dashboard/<id>?overwrite=true`).

The create path (not `_import`) is deliberate: `_import` treats hand-authored
objects as version-zero and replays every historical Lens migration, which
crashes on by-value panels. Create applies the current schema directly.

Everything is driven by a `dashboards-spec.json` next to this script, so the
fragile part (Lens panel JSON) lives in ONE place. The same engine is rendered
per-project by `/new-project`; only the spec differs.

Stdlib only — no pip dependency (the framework runs the generator with zero
extra deps; this honours the same rule).

Usage:
    python kibana_bootstrap.py wait            # block until Kibana is available
    python kibana_bootstrap.py import          # create data views + import dashboards
    python kibana_bootstrap.py up              # wait, then import (used by the sidecar)
    python kibana_bootstrap.py export <id>     # dump a dashboard's saved objects as NDJSON

Environment:
    KIBANA_URL   overrides spec.kibana_url   (default http://localhost:5601)
    KIBANA_SPACE overrides spec.space        (default "default")

Exit codes: 0 success, 1 user error, 2 import/validation failure.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_SPEC = SCRIPT_DIR / "dashboards-spec.json"
GRID_COLUMNS = 48  # Kibana dashboard grid width


# --------------------------------------------------------------------------- #
# HTTP helpers (stdlib urllib)                                                 #
# --------------------------------------------------------------------------- #
def _space_prefix(space: str) -> str:
    """Kibana spaces are URL-segment-scoped; 'default' has no prefix."""
    return "" if space in ("", "default") else f"/s/{space}"


def _request(method: str, url: str, body: bytes | None = None,
             headers: dict[str, str] | None = None, timeout: int = 30):
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("kbn-xsrf", "true")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except urllib.error.URLError as e:
        return 0, str(e.reason)


# --------------------------------------------------------------------------- #
# Lens / dashboard builders (the one place the saved-object schema lives)      #
# --------------------------------------------------------------------------- #
# ---- formBased datasource columns ---------------------------------------- #
def _count_column(label: str = "Count", filter_kql: str | None = None) -> dict:
    col = {"label": label, "dataType": "number", "operationType": "count",
           "isBucketed": False, "sourceField": "___records___",
           "params": {"emptyAsNull": False}}
    if filter_kql:
        col["filter"] = {"query": filter_kql, "language": "kuery"}
    return col


def _unique_column(field: str, label: str | None = None) -> dict:
    return {"label": label or f"Unique {field}", "dataType": "number",
            "operationType": "unique_count", "sourceField": field,
            "isBucketed": False, "params": {"emptyAsNull": False}}


def _terms_column(field: str, size: int, order_col: str, label: str | None = None) -> dict:
    return {"label": label or f"Top {field}", "dataType": "string",
            "operationType": "terms", "sourceField": field, "isBucketed": True,
            "params": {"size": size, "orderBy": {"type": "column", "columnId": order_col},
                       "orderDirection": "desc", "otherBucket": True,
                       "missingBucket": False, "parentFormat": {"id": "terms"}}}


def _date_column(field: str, interval: str, label: str | None = None) -> dict:
    return {"label": label or field, "dataType": "date",
            "operationType": "date_histogram", "sourceField": field,
            "isBucketed": True, "scale": "interval",
            "params": {"interval": interval, "includeEmptyRows": True,
                       "dropPartials": False}}


def _lens(label: str, viz_type: str, visualization: dict, layer_id: str, dv_id: str,
          columns: dict, column_order: list[str]) -> dict:
    return {
        "title": label, "visualizationType": viz_type, "type": "lens",
        "references": [{"type": "index-pattern", "id": dv_id,
                        "name": f"indexpattern-datasource-layer-{layer_id}"}],
        "state": {
            "visualization": visualization,
            "query": {"query": "", "language": "kuery"}, "filters": [],
            "datasourceStates": {"formBased": {"layers": {layer_id: {
                "columns": columns, "columnOrder": column_order,
                "sampling": 1, "incompleteColumns": {},
            }}}},
            "internalReferences": [], "adHocDataViews": {},
        },
    }


# ---- per-viz Lens builders (panel spec → lens attributes) ----------------- #
def viz_metric(p, layer_id, dv_id):
    cols = {"m0": _count_column(p.get("label", "Count"))}
    order = ["m0"]
    vis = {"layerId": layer_id, "layerType": "data", "metricAccessor": "m0"}
    sec = p.get("secondary")
    if sec:
        cols["m1"] = (_unique_column(sec["field"], sec.get("label"))
                      if sec.get("op") == "unique_count"
                      else _count_column(sec.get("label", "Count")))
        order.append("m1")
        vis["secondaryMetricAccessor"] = "m1"
    return _lens(p.get("label", "Metric"), "lnsMetric", vis, layer_id, dv_id, cols, order)


def viz_partition(p, layer_id, dv_id, shape):
    fields = p.get("fields") or [p["field"]]
    cols = {"m0": _count_column("Count")}
    groups, order = [], []
    for i, f in enumerate(fields):
        cid = f"b{i}"
        cols[cid] = _terms_column(f, p.get("size", 10), "m0")
        groups.append(cid)
        order.append(cid)
    order.append("m0")
    vis = {"shape": shape, "layers": [{
        "layerId": layer_id, "layerType": "data", "primaryGroups": groups,
        "metrics": ["m0"], "numberDisplay": "percent", "categoryDisplay": "default",
        "legendDisplay": "default", "nestedLegend": False}]}
    return _lens(p.get("label", shape), "lnsPie", vis, layer_id, dv_id, cols, order)


def viz_xy(p, layer_id, dv_id, series_type):
    cols = {"m0": _count_column(p.get("metric_label", "Count"))}
    order = []
    if p.get("date_field"):
        cols["x0"] = _date_column(p["date_field"], p.get("interval", "1w"))
    else:
        cols["x0"] = _terms_column(p["field"], p.get("size", 15), "m0")
    order.append("x0")
    layer = {"layerId": layer_id, "layerType": "data", "seriesType": series_type,
             "xAccessor": "x0", "accessors": ["m0"]}
    if p.get("breakdown"):
        cols["s0"] = _terms_column(p["breakdown"], p.get("breakdown_size", 5), "m0")
        order.append("s0")
        layer["splitAccessor"] = "s0"
    order.append("m0")
    vis = {"preferredSeriesType": series_type, "valueLabels": "hide",
           "fittingFunction": "None",
           "legend": {"isVisible": True, "position": "right"},
           "axisTitlesVisibilitySettings": {"x": False, "yLeft": False, "yRight": True},
           "layers": [layer]}
    return _lens(p.get("label", "Chart"), "lnsXY", vis, layer_id, dv_id, cols, order)


def viz_heatmap(p, layer_id, dv_id):
    cols = {"m0": _count_column("Count"),
            "x0": _terms_column(p["x"], p.get("x_size", 20), "m0"),
            "y0": _terms_column(p["y"], p.get("y_size", 20), "m0")}
    order = ["x0", "y0", "m0"]
    vis = {"shape": "heatmap", "layerId": layer_id, "layerType": "data",
           "xAccessor": "x0", "yAccessor": "y0", "valueAccessor": "m0",
           "legend": {"isVisible": True, "position": "right", "type": "heatmap_legend"},
           "gridConfig": {"type": "heatmap_grid", "isCellLabelVisible": True,
                          "isYAxisLabelVisible": True, "isXAxisLabelVisible": True,
                          "isYAxisTitleVisible": False, "isXAxisTitleVisible": False}}
    return _lens(p.get("label", "Heatmap"), "lnsHeatmap", vis, layer_id, dv_id, cols, order)


def viz_tagcloud(p, layer_id, dv_id):
    cols = {"m0": _count_column("Count"),
            "t0": _terms_column(p["field"], p.get("size", 50), "m0")}
    order = ["t0", "m0"]
    vis = {"layerId": layer_id, "layerType": "data", "valueAccessor": "m0",
           "tagAccessor": "t0", "maxFontSize": 48, "minFontSize": 14,
           "orientation": "single", "showLabel": True}
    return _lens(p.get("label", "Tags"), "lnsTagcloud", vis, layer_id, dv_id, cols, order)


def viz_table(p, layer_id, dv_id):
    cols, order, tcols = {}, [], []
    if p.get("field"):
        cols["b0"] = _terms_column(p["field"], p.get("size", 20), "m0")
        order.append("b0")
        tcols.append({"columnId": "b0"})
    if p.get("filters"):
        for i, f in enumerate(p["filters"]):
            cid = f"m{i}"
            cols[cid] = _count_column(f["label"], f["kql"])
            order.append(cid)
            tcols.append({"columnId": cid})
    else:
        for i, m in enumerate(p.get("metrics", [{"op": "count"}])):
            cid = f"m{i}"
            cols[cid] = (_unique_column(m["field"], m.get("label"))
                         if m.get("op") == "unique_count"
                         else _count_column(m.get("label", "Count")))
            order.append(cid)
            tcols.append({"columnId": cid})
    vis = {"layerId": layer_id, "layerType": "data", "columns": tcols}
    return _lens(p.get("label", "Table"), "lnsDatatable", vis, layer_id, dv_id, cols, order)


VIZ_BUILDERS = {
    "metric":         lambda p, l, d: viz_metric(p, l, d),
    "pie":            lambda p, l, d: viz_partition(p, l, d, "pie"),
    "donut":          lambda p, l, d: viz_partition(p, l, d, "donut"),
    "treemap":        lambda p, l, d: viz_partition(p, l, d, "treemap"),
    "mosaic":         lambda p, l, d: viz_partition(p, l, d, "mosaic"),
    "bar":            lambda p, l, d: viz_xy(p, l, d, "bar_stacked"),
    "bar_stacked":    lambda p, l, d: viz_xy(p, l, d, "bar_stacked"),
    "bar_horizontal": lambda p, l, d: viz_xy(p, l, d, "bar_horizontal"),
    "line":           lambda p, l, d: viz_xy(p, l, d, "line"),
    "area":           lambda p, l, d: viz_xy(p, l, d, "area"),
    "heatmap":        viz_heatmap,
    "tagcloud":       viz_tagcloud,
    "table":          viz_table,
}

# Default panel heights in a full-width column (grid is 48 wide).
PANEL_HEIGHTS = {
    "metric": 8, "pie": 14, "donut": 14, "treemap": 15, "mosaic": 15,
    "bar": 14, "bar_stacked": 14, "bar_horizontal": 16, "line": 14, "area": 14,
    "heatmap": 17, "tagcloud": 12, "table": 16,
}


def _build_explicit_panels(dash, panels, references, data_views):
    """Differentiated dashboard: explicit `panels[]` over a single data view,
    laid out as a full-width column (D-426)."""
    dv = dash["data_view"]
    dv_id = dv["id"]
    data_views.append({"id": dv_id, "title": dv["title"],
                       "timeFieldName": dv.get("time_field"),
                       "name": f"{dash['title']} data view"})
    y = 0
    for n, p in enumerate(dash["panels"]):
        viz = p["viz"]
        builder = VIZ_BUILDERS.get(viz)
        if not builder:
            print(f"  warn: unknown viz '{viz}' (panel {n}) — skipped", file=sys.stderr)
            continue
        pi = f"{dash['id']}-p{n}"
        layer_id = f"{dash['id']}-l{n}"
        h = p.get("height", PANEL_HEIGHTS.get(viz, 14))
        panels.append({
            "type": "lens", "panelIndex": pi,
            "gridData": {"x": 0, "y": y, "w": 48, "h": h, "i": pi},
            "embeddableConfig": {"attributes": builder(p, layer_id, dv_id),
                                 "enhancements": {}},
            "title": p.get("label", ""),
        })
        references.append({"type": "index-pattern", "id": dv_id,
                          "name": f"{pi}:indexpattern-datasource-layer-{layer_id}"})
        y += h


def _build_index_overview(dash, panels, references, data_views):
    """Legacy/auto path: one row per index — [metric | date-histogram bar]."""
    y = 0
    for idx in dash["indices"]:
        dv_id = idx["id"]
        label = idx.get("label", idx.get("title"))
        data_views.append({"id": dv_id, "title": idx.get("title"),
                           "timeFieldName": idx.get("time_field"),
                           "name": f"{label} data view"})
        m_layer, b_layer = f"{dv_id}-m", f"{dv_id}-b"
        m_pi, b_pi = f"{dv_id}-metric", f"{dv_id}-bar"
        panels.append({
            "type": "lens", "panelIndex": m_pi,
            "gridData": {"x": 0, "y": y, "w": 12, "h": 12, "i": m_pi},
            "embeddableConfig": {
                "attributes": viz_metric({"label": f"{label} — total"}, m_layer, dv_id),
                "enhancements": {}},
            "title": f"{label} — total documents",
        })
        references.append({"type": "index-pattern", "id": dv_id,
                          "name": f"{m_pi}:indexpattern-datasource-layer-{m_layer}"})
        panels.append({
            "type": "lens", "panelIndex": b_pi,
            "gridData": {"x": 12, "y": y, "w": 36, "h": 12, "i": b_pi},
            "embeddableConfig": {
                "attributes": viz_xy({"label": f"{label} over time",
                                      "date_field": idx.get("time_field"),
                                      "interval": "auto"}, b_layer, dv_id, "bar_stacked"),
                "enhancements": {}},
            "title": f"{label} — volume over time",
        })
        references.append({"type": "index-pattern", "id": dv_id,
                          "name": f"{b_pi}:indexpattern-datasource-layer-{b_layer}"})
        y += 12


def build_dashboard_so(dash: dict) -> tuple[dict, list[dict]]:
    """Return (dashboard saved-object, [data view saved-objects to ensure]).

    Two modes per dashboard:
      - `panels[]` declared → differentiated, full-width column (D-426);
      - else `indices[]`    → auto metric+bar per index (D-425).
    """
    panels: list[dict] = []
    references: list[dict] = []
    data_views: list[dict] = []
    if dash.get("panels"):
        _build_explicit_panels(dash, panels, references, data_views)
    else:
        _build_index_overview(dash, panels, references, data_views)

    dashboard_so = {
        "id": dash["id"], "type": "dashboard",
        "managed": False, "coreMigrationVersion": "8.8.0",
        "attributes": {
            "title": dash["title"],
            "description": dash.get("description", ""),
            "panelsJSON": json.dumps(panels),
            "optionsJSON": json.dumps({"useMargins": True, "syncColors": False,
                                       "syncCursor": True, "syncTooltips": False,
                                       "hidePanelTitles": False}),
            "timeRestore": True,
            "timeFrom": dash.get("time_from", "now-30d"),
            "timeTo": dash.get("time_to", "now"),
            "refreshInterval": {"pause": True, "value": 60000},
            "kibanaSavedObjectMeta": {
                "searchSourceJSON": json.dumps({"query": {"query": "", "language": "kuery"},
                                                "filter": []}),
            },
        },
        "references": references,
    }
    return dashboard_so, data_views


# --------------------------------------------------------------------------- #
# Operations                                                                   #
# --------------------------------------------------------------------------- #
def load_spec(spec_path: Path) -> dict:
    if not spec_path.exists():
        print(f"error: spec not found: {spec_path}", file=sys.stderr)
        sys.exit(1)
    return json.loads(spec_path.read_text(encoding="utf-8"))


def resolve_target(spec: dict) -> tuple[str, str]:
    url = os.environ.get("KIBANA_URL") or spec.get("kibana_url") or "http://localhost:5601"
    space = os.environ.get("KIBANA_SPACE") or spec.get("space") or "default"
    return url.rstrip("/"), space


def wait_for_kibana(base: str, space: str, attempts: int = 60, delay: int = 5) -> bool:
    url = f"{base}{_space_prefix(space)}/api/status"
    for i in range(1, attempts + 1):
        status, text = _request("GET", url, timeout=10)
        if status == 200:
            try:
                level = json.loads(text).get("status", {}).get("overall", {}).get("level")
            except json.JSONDecodeError:
                level = None
            if level == "available":
                print(f"OK: Kibana available ({base})")
                return True
        print(f"  ... waiting for Kibana [{i}/{attempts}] (status={status})")
        time.sleep(delay)
    print(f"error: Kibana not available after {attempts * delay}s", file=sys.stderr)
    return False


def ensure_data_view(base: str, space: str, dv: dict) -> bool:
    url = f"{base}{_space_prefix(space)}/api/data_views/data_view"
    # Omit timeFieldName when the index has no usable date field: a null/absent
    # time field means the global time picker does NOT filter the data view
    # (otherwise every panel is empty when the field is unpopulated).
    dv = {k: v for k, v in dv.items() if not (k == "timeFieldName" and not v)}
    body = json.dumps({"data_view": dv, "override": True}).encode("utf-8")
    status, text = _request("POST", url, body, {"Content-Type": "application/json"})
    if status in (200, 201):
        print(f"OK: data view '{dv['id']}' -> {dv['title']}")
        return True
    print(f"error: data view '{dv['id']}' failed (status={status}): {text[:300]}",
          file=sys.stderr)
    return False


def import_dashboard(base: str, space: str, dashboard_so: dict) -> bool:
    """Create the dashboard via the Saved Objects *create* API (not _import).

    The create path applies the current saved-object schema and stamps the
    current model version, so it does NOT replay legacy migrations. `_import`
    treats hand-authored objects as version-zero and replays every historical
    Lens migration, which crashes on by-value panels ("reading 'layers'").
    """
    obj_id = dashboard_so["id"]
    url = (f"{base}{_space_prefix(space)}/api/saved_objects/dashboard/"
           f"{obj_id}?overwrite=true")
    body = json.dumps({
        "attributes": dashboard_so["attributes"],
        "references": dashboard_so["references"],
    }).encode("utf-8")
    status, text = _request("POST", url, body, {"Content-Type": "application/json"})
    if status in (200, 201):
        print(f"OK: dashboard '{obj_id}' created ({len(dashboard_so['references'])} panel ref(s))")
        return True
    print(f"error: dashboard '{obj_id}' create failed (status={status}): {text[:400]}",
          file=sys.stderr)
    return False


def do_import(spec_path: Path) -> int:
    spec = load_spec(spec_path)
    base, space = resolve_target(spec)
    ok = True
    for dash in spec.get("dashboards", []):
        dashboard_so, data_views = build_dashboard_so(dash)
        for dv in data_views:
            ok = ensure_data_view(base, space, dv) and ok
        ok = import_dashboard(base, space, dashboard_so) and ok
    if not ok:
        return 2
    kib = f"{base}{_space_prefix(space)}/app/dashboards"
    print(f"OK: provisioning complete — browse at {kib}")
    return 0


def do_export(spec_path: Path, dashboard_id: str) -> int:
    spec = load_spec(spec_path)
    base, space = resolve_target(spec)
    url = f"{base}{_space_prefix(space)}/api/saved_objects/_export"
    body = json.dumps({"objects": [{"type": "dashboard", "id": dashboard_id}],
                       "includeReferencesDeep": True}).encode("utf-8")
    status, text = _request("POST", url, body, {"Content-Type": "application/json"})
    if status != 200:
        print(f"error: export HTTP {status}: {text[:300]}", file=sys.stderr)
        return 2
    sys.stdout.write(text)
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Remote Kibana dashboard provisioning (D-425)")
    parser.add_argument("command", choices=["wait", "import", "up", "export"])
    parser.add_argument("dashboard_id", nargs="?", help="dashboard id (export only)")
    parser.add_argument("--spec", type=Path, default=DEFAULT_SPEC,
                        help=f"dashboards spec (default: {DEFAULT_SPEC.name})")
    args = parser.parse_args(argv)

    if args.command == "export":
        if not args.dashboard_id:
            print("error: export requires a dashboard id", file=sys.stderr)
            return 1
        return do_export(args.spec, args.dashboard_id)

    spec = load_spec(args.spec)
    base, space = resolve_target(spec)
    if args.command in ("wait", "up"):
        if not wait_for_kibana(base, space):
            return 2
        if args.command == "wait":
            return 0
    return do_import(args.spec)


if __name__ == "__main__":
    raise SystemExit(main())
