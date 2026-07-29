# GEST-FE-012 -- PreviewFrameComponent — webcam <video> wrapper, aspect-ratio frame, no stream lifecycle

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`PreviewFrameComponent` in `libs/gest/ui` accepts `@Input() stream: MediaStream | null` and `@Input() state: GestureTestState`, binds `stream` to `<video>.srcObject`, and manages no stream lifecycle — display only.

## Context / Problem statement
- Purely presentational. The component owns the `<video>` element and aspect-ratio frame CSS. Stream ownership stays in the facade.
- When `stream` is `null`, shows a placeholder (camera icon / "sem câmera" text) per design.
- Source: SPEC-02 §1, §2.

## Approach (execution path)
1. Create `preview-frame.component.ts` with `@Input() stream: MediaStream | null` and `@Input() state: GestureTestState`.
2. Use `afterNextRender` or `ngOnChanges` to bind `videoRef.nativeElement.srcObject = stream`.
3. Template: `<video #videoRef autoplay playsinline muted>` + placeholder overlay when `stream === null`.
4. SCSS: aspect-ratio 4:3, overflow hidden, design tokens only.
5. If also needed by other modules, keep in `libs/gest/ui` (can be promoted later).

## Non-goals
- Not responsible for acquiring or releasing the MediaStream.

## Layer(s)
frontend / ui lib

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1, §2)

## Architecture alignment
- `standalone: true`, `changeDetection: OnPush`
- Pure `@Input`/`@Output`
- Tag Nx: `scope:gest`, `type:ui`

## Implementation checklist (by layer/file)

**UI lib**
- [ ] `libs/gest/ui/src/lib/preview-frame/preview-frame.component.ts`
- [ ] `libs/gest/ui/src/lib/preview-frame/preview-frame.component.html`
- [ ] `libs/gest/ui/src/lib/preview-frame/preview-frame.component.scss`

**Tests**
- [ ] `npx nx test gest-ui` PASS (input null → placeholder; input stream → <video> srcObject)
- [ ] `npx nx lint gest-ui` PASS
- [ ] `npx nx build gest-ui` PASS

## Acceptance criteria
- `stream !== null` → `<video>` has `srcObject` set
- `stream === null` → placeholder visible
- No `getTrack()`, `stop()`, or `getUserMedia()` calls inside this component
- `npx nx build gest-ui` PASS

## Dependencies
- GEST-FE-011 (consumed by GestureLiveTestComponent)

## Empresa-context impact
Nenhum.

## Milestone
Wave 3 / Feature libs

## Labels
type:feature
layer:frontend
scope:gest
priority:P3

## Risks / Notes
- `muted` attribute is required on `<video>` to allow autoplay without user gesture in most browsers
- LGPD: no video/frame/landmark data must ever leave the browser — this component is display only

## Prioridade
P3
