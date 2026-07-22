# Village World Phase 2 — Template Version 2

## Village template version 2

New worlds bootstrap a deterministic 24×16 terrain map and an additive version-2 village template. The version is stored on `worlds.village_template_version`; once it reaches `2`, a later bootstrap creates no additional template changes or objects. Template keys are per-character and therefore idempotent.

The layout contains one plaza, one empty SOIL plot, eight carrots, eight flowers, four tomatoes, four cabbages, one community house, four default NPCs, one dog, one cat, and two birds. SOIL is land-walkable. The base building area is deliberately outside the flower coordinates so every requested crop coordinate remains valid.

## Migrations

Local PostgreSQL has recorded successful Flyway migrations V5 (template `WorldChange` may have a null recognition), V6 (village template version), V7 (SOIL support), and V8 (the active PostgreSQL `CHECK` constraints aligned to the template v2 asset and terrain domain). No applied migration was rewritten.

### V8 root cause and scope

The prior active PostgreSQL constraints did not admit the v2 bootstrap domain: template assets such as `PLAZA`, `COMMUNITY_HOUSE`, the `FARM_*` assets, default NPC assets, and default animals were absent from the asset checks; `SOIL` was absent from the terrain checks. V8 safely drops the named checks and recreates them with the existing domain plus every current template asset and `SOIL`. It does not remove the checks or rename an enum value.

## Interaction and UI policy

`WorldStateResponse.npcPositions` is now a typed NPC list. Adjacent default NPC tiles return an authoritative `TALK` interaction with target id, asset type, and display name; ordinary adjacent tiles retain `INSPECT`.

The client resolves the four fixed Korean dialogue lines locally from that identity. The talk action is screen-space HUD UI, not a camera-transformed world button. Inspect and template dialogue share a small active-panel coordinator; opening inspect clears dialogue, the empty-plot CTA opens the existing capture flow, and Escape closes only the currently active local panel.

No planting API, user-photo animal NPC, thumbnail endpoint, quest system, or NPC movement was added.

## Evidence

`VillageTemplateIntegrationTests` asserts exact crop counts, coordinate sets, bounds, SOIL walkability, second-bootstrap idempotency, and preservation of an existing Photo → Recognition → WorldChange → WorldPlacedObject chain. `WorldTileInteractionIntegrationTests` asserts typed TALK availability next to the guide and absence out of range. Frontend browser evidence is provided by the repository-local Playwright suite using the existing local fixture account without resetting or recreating database data.

## PostgreSQL fixture evidence

The local PostgreSQL fixture `world=4, character=6` contains an existing memory chain (`photo=39`, `recognition=28`, `world_change=14`, `world_placed_object=14`). Its first template bootstrap recorded `village_template_version=2`, 384 terrain tiles, 32 SOIL tiles, 36 world changes, and 36 placed objects.

A second real `GET /api/worlds/me/state` bootstrap request left all retained values byte-for-byte and count-for-count stable. The asset deltas were zero for `PLAZA` (1), `FARM_PLOT_EMPTY` (1), `FARM_CARROT` (8), `FARM_FLOWER` (8), `FARM_TOMATO` (4), `FARM_CABBAGE` (4), `COMMUNITY_HOUSE` (1), each of the four default NPC assets (1 each), `DEFAULT_DOG` (1), `DEFAULT_CAT` (1), and `DEFAULT_BIRD` (2). No `FARM_VEGETABLE` or `BUSH` rows existed before or after (0 to 0). The existing memory-chain IDs remained `39`, `28`, `14`, and `14` respectively.

## Fresh runtime evidence

The evidence run restarted the backend from `/Users/jangseongju/Project_Eden/project-eden-backend 3` as PID 58233 on port 8080, with the `local` Spring profile and PostgreSQL database `project_eden` at `jdbc:postgresql://localhost:5432/project_eden`. Flyway applied V8 and Hibernate/Tomcat started successfully.

The frontend was restarted from `/Users/jangseongju/Project_Eden/project-eden-frontend` as PID 63629 on `127.0.0.1:5173`; it used the local backend at `http://localhost:8080`.

## Browser Village E2E

With a fresh backend process and fixture login, the browser rendered 384 persistent terrain tiles and 36 persistent objects: one plaza, one community house, 32 SOIL tiles, eight carrots, eight flowers, four tomatoes, four cabbages, four default NPCs, one dog, one cat, and two birds. ROAD movement and a later move onto SOIL both received server-authoritative updates. A template NPC dialogue was opened and closed with Escape.

The panel transitions were exercised in the real client: INSPECT to DIALOGUE removed INSPECT; DIALOGUE to MEMORY_UPLOAD removed DIALOGUE; Escape from MEMORY_UPLOAD returned to the Village without restoring a prior local panel. A hard reload returned to the application start screen because the browser session does not retain the frontend authentication state; selecting the Village entry again reloaded the same persisted 384/36 world state. This is an authentication-session behavior, not a loss of world persistence.

## Responsive QA

Playwright Chromium touch emulation passed at 375×667, 390×844, and 430×932. Each independent context used `hasTouch=true`, `isMobile=true`, and device scale factor `2`; all reported `(pointer: coarse)=true`, `(hover: none)=true`, and `navigator.maxTouchPoints=1`. The touch joystick moved the persisted player, INSPECT/DIALOGUE/Capture panels remained inside each viewport, horizontal and vertical overflow were absent, and the HUD/capture controls retained an 18px bottom gap.

The final six-scenario Playwright run also verified a real rejected move with unchanged server/DOM position, a 10.229-second key hold with 23 requests and maximum concurrency `1`, and empty-farm CTA entry with zero planting, photo-upload, recognition, or world-change POST requests. Physical iPhone Safari was not tested, so this evidence is explicitly limited to Chromium mobile/touch emulation.

## Final regression evidence

The final backend run completed with 492 tests, 0 failures, 0 errors, and 4 opt-in skips. `./mvnw package` completed successfully. The frontend Playwright suite completed 6/6 tests in 39.2 seconds, `npm run lint` completed with zero warnings, and `npm run build` completed successfully. `git diff --check` passed in both worktrees. No generated dataset, model binary, or browser result artifact was added to Git.

## Final 2차-A evidence decision

Second-bootstrap, fresh runtime, existing-chain preservation, server-reject, actual key-hold, CTA network, touch joystick, responsive panel, and fresh regression evidence are complete. Village MVP Polish 2차-A is complete. Physical iPhone Safari remains outside this acceptance evidence and is not represented as tested.

## Village MVP Polish 2차-B1 interaction contract

The backend now exposes one additive contextual interaction type, `INTERACT`, alongside the existing `TALK` and `INSPECT` values. A contextual response retains the existing target fields and adds a stable `category` and `actionLabel`. The server resolves candidate priority as `TALK > INTERACT > INSPECT`; equal-priority candidates are ordered by tile `y`, tile `x`, target asset type, and target id. If multiple candidates occupy one tile, the target asset type and id provide the deterministic tie-break. Only one response is emitted for each adjacent tile.

The contextual asset contract is:

- `FARM`: `FARM_PLOT_EMPTY`
- `CROP`: `FARM_CARROT`, `FARM_FLOWER`, `FARM_VEGETABLE`, `FARM_TOMATO`, `FARM_CABBAGE`
- `ANIMAL`: `DEFAULT_DOG`, `DEFAULT_CAT`, `DEFAULT_BIRD`
- `COMMUNITY`: `COMMUNITY_HOUSE`

`PLAZA`, `BUSH`, generic decoration assets, and legacy memory assets continue to use the ordinary `INSPECT` contract. Default NPCs continue to use `TALK`; when an NPC and contextual object share a tile, only the NPC `TALK` response is returned.

Interaction range remains the four cardinal tiles exactly one step from the persisted player position. The current tile, diagonal tiles, distance-two tiles, and out-of-bounds coordinates are excluded. Candidate objects are obtained only from world changes owned by the authenticated user's character, preserving user isolation. Missing candidates serialize as an empty array rather than `null`, and repeated template bootstrap does not duplicate targets or interaction rows.

`WorldTileInteractionIntegrationTests` covers the empty farm, every supported crop asset, every default animal type, the community house, TALK priority, deterministic repeated ordering, user isolation, idempotent bootstrap, duplicate prevention, range, and JSON serialization of contextual metadata.

The 2-B1 validation run completed the targeted interaction suite with 15 tests and the full backend regression with 501 tests, 0 failures, 0 errors, and 4 existing opt-in skips. `./mvnw package` and `git diff --check` passed. A fresh local-profile process on port 18093 connected to PostgreSQL 16.14, validated all nine recorded Flyway entries through schema version 8, initialized Hibernate, and started Tomcat. An authenticated `GET /api/worlds/me/state` returned HTTP 200 with the persisted empty-farm payload: `type=INTERACT`, `targetId=16`, `targetAssetType=FARM_PLOT_EMPTY`, `category=FARM`, `displayName=비어 있는 밭`, and `actionLabel=살펴보기`. The fixture player position was restored to its original coordinate after this verification.

This step does not implement contextual action execution, planting persistence, dialogue progression, or frontend rendering. Village MVP Polish 2차-B2 frontend contextual interaction remains unimplemented.
