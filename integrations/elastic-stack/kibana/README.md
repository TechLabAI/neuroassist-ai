# Kibana dashboards — NeuroAssist AI (D-425)

Remote, scripted provisioning of this project's Kibana dashboards. No clicking in
the UI: data views and dashboards are created over Kibana's HTTP APIs.

| File | Role |
|---|---|
| `kibana_bootstrap.py` | Provisioning engine (stdlib only). Waits for Kibana, creates data views, builds + imports dashboards. |
| `dashboards-spec.json` | What to provision — derived at generation time from this project's Elasticsearch indices. Edit freely; it is yours now. |

## Run it

```bash
# bring the elastic stack up (from infra/)
docker compose -f infra/compose-elastic.yml up -d

# provision (host needs Python 3.10+)
py kibana/kibana_bootstrap.py up        # wait for Kibana, then import

# or let the optional sidecar do it
docker compose -f infra/compose-elastic.yml --profile bootstrap up
```

Browse at <http://localhost:5601/app/dashboards>
→ **NeuroAssist AI — Overview**.

Idempotent: data views use `override:true`, dashboards use the Saved Objects
*create* API with `overwrite=true`. Re-running reconciles, never duplicates.

## Adding panels

Each entry under `dashboards[].indices` yields one data view + two Lens panels
(total count + volume over time on `time_field`). To add an index or a new
dashboard, edit `dashboards-spec.json` and re-run `up`. The generated spec was
seeded from `stack.elastic.indices[]` (or the `neuroassist-ai_*`
prefix when no indices were declared at `/new-project`).

For the design-level contract of each dashboard (panels, audience, questions),
see the `kibana-dashboard-spec` mold under `docs/architecture/.../design/`.
