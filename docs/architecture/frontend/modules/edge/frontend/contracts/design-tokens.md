# Design Tokens — EDGE (Frontend)

> EDGE consumes shared tokens from `@neuroassist/shared/ui-tokens`; it defines **no** ad-hoc colors
> (doc 02 §5). Tokens listed here are the ones the connectivity chip, pairing, and device-detail surfaces
> rely on. Values are owned by the shared token layer, not by EDGE. The chip reuses the prototype
> `topbar.component.ts` semantics (`bg-success-bg text-success-ink` / `bg-danger-bg text-danger-ink`,
> `wifi` / `wifi_off`).

## 1. Connectivity state → tone → token

| DerivedState | Tone | Token | Icon (material-symbols) | pt-BR label |
|---|---|---|---|---|
| `online` | `ok` | `--color-success` | `wifi` | "Dispositivo online" |
| `stale` | `warn` | `--color-warning` | `wifi` + "?" (`network_check`) | "Dispositivo sem sinal recente" |
| `offline` | `er` | `--color-danger` | `wifi_off` | "Dispositivo offline" |
| `unknown` | `warn` / `muted` | `--color-warning` (muted) | `signal_disconnected` | "Sem confirmação de conexão" |
| `not-paired` | `muted` | `--color-neutral-*` | `devices_off` | "Nenhum dispositivo pareado" |

## 2. Consumed tokens

| Token | Role in EDGE | Used by |
|---|---|---|
| `--color-success` | `online` chip / verdict badge | `ConnectivityChipComponent`, `ConnectivityStatusCardComponent` |
| `--color-warning` | `stale` / `unknown` (degraded) chip + banner | `ConnectivityChipComponent`, `PairingResultComponent` |
| `--color-danger` | `offline` chip / error banners / retire action | `ConnectivityChipComponent`, `DeviceActionsComponent` |
| `--color-neutral-*` | `not-paired` chip, card chrome, meta list, idle pairing | `ConnectivityChipComponent`, `DeviceMetaListComponent`, `PairingCodeCardComponent` |
| `--color-brand` | primary action ("Registrar" / "Parear") | `PairingCodeCardComponent`, `DeviceActionsComponent` |
| typography scale (`--font-*`) | human-language labels + relative time (never raw numbers) | all presentational |
| spacing / radius tokens | chip pill, code card, detail layout | feature layout |

## 3. Accessibility & UX constraints

- Connectivity is **never** conveyed by color alone — every state pairs a color token with a pt-BR text
  label and an icon (WCAG; non-technical caregiver persona, doc 06). The topbar chip is legible at a glance
  on desktop/laptop (doc 06 §3 assumption).
- The `stale`/`offline`/`unknown` states are visually distinct from `online` — a degraded state is never
  rendered with the `--color-success` tone (honest-connectivity, ADR-005).
- No credential secret, video, image, landmark, or raw confidence value is ever styled onto the screen
  (api-contract §8, privacy invariant doc 02).
- The enrollment token is displayed as a copyable code with a visible **TTL countdown** (never shown after
  expiry — code TTL respected, ADR-005).

## 4. No SCSS token layer

Styling uses Tailwind utilities bound to the CSS custom properties above (as in the prototype topbar);
there is no SCSS token layer in this project (doc 04 §1).
