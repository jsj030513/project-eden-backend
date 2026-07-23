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

## Village MVP Polish 2차-C1 target-aware planting

The authenticated command `POST /api/worlds/me/plant-memory` accepts only `photoId`, `targetId`, `expectedX`, and `expectedY`. The server derives the character, target asset, target owner, and crop type. It locks the owned Photo and target object, requires an unchanged `FARM_PLOT_EMPTY` at the supplied tile coordinate, and requires the persisted player position to be exactly one cardinal tile away. Missing resources use the existing 404 contract, ownership violations use 403, and stale, occupied, already-expressed, or out-of-range requests use the existing 409 response shape with a stable message code.

Safe crop projection is deliberately narrow: `FLOWER` becomes `FARM_FLOWER`, `CARROT` becomes `FARM_CARROT`, `TOMATO` becomes `FARM_TOMATO`, and `VEGETABLE` or `PLANT` becomes `FARM_VEGETABLE`. No current Recognition signal safely identifies cabbage, so `FARM_CABBAGE` is not produced. `UNKNOWN`, broad FOOD, animals, scenes, and other objects never create a crop.

Non-plantable planting uses policy B. Recognition, Memory Classification, the legacy recognition event, evolution, and Village Memory are saved, and the initial target is retained on the Recognition as its terminal planting context. No WorldChange or WorldPlacedObject is created, and the empty plot remains available to a different Photo. Retrying the same Photo and target returns the same non-plantable result; using that Photo with another target returns `PHOTO_ALREADY_EXPRESSED`. This prevents a failed semantic match from consuming a plot while also preventing unbounded target retries with one Photo.

Plantable results use append-only persistence. The original empty `WorldPlacedObject` is neither deleted nor mutated. A canonical recognition-backed `WorldChange` references that object through `target_object_id`, and one crop object is appended at the same pixel coordinate. World-state projection suppresses only a referenced empty object and includes the appended crop, so the API and contextual interaction expose one crop at that coordinate while the database retains the original history. The existing general `/api/photos/{photoId}/recognize` path remains unchanged and creates a non-targeted WorldChange.

Migration V9 adds nullable `recognitions.planting_target_object_id`, nullable `world_changes.target_object_id`, restrictive foreign keys to `world_placed_objects`, an index for Recognition planting context, a unique target constraint, and a check that a targeted change uses a `FARM_*` crop asset. The target row is selected with a pessimistic write lock and the unique constraint remains the final concurrent-write defense. Same target/same Photo is idempotent; same Photo/different target and different Photo/already-planted target return 409.

`WorldPlantingIntegrationTests` covers the five safe mappings, non-plantable terminal behavior, ownership, stale coordinates, invalid target types, same/diagonal/distance range rejection, append-only projection replacement, retry idempotency, Photo/target conflicts, general Recognition compatibility, bootstrap stability, and HTTP serialization. `WorldPlantingConcurrencyIntegrationTests` issues two transactions against one target and verifies one winner, one `TARGET_ALREADY_PLANTED` loser, one Recognition, one Village Memory, one targeted WorldChange, and one crop object.

## 2-C1 PostgreSQL runtime evidence

The local-profile application started on port 18095 against PostgreSQL 16.14. Flyway validated ten history entries through schema version 9, Hibernate initialized, and Tomcat started successfully. Catalog inspection confirmed both nullable columns, both `ON DELETE RESTRICT` foreign keys, `uk_world_changes_target_object`, and `world_changes_target_crop_check`.

The authenticated fixture `planting-c1-1784741792@local.test` moved to persisted tile `(3,8)` and targeted empty object `647` at `(3,9)`. Photo `42` (`flower-memory.webp`) returned HTTP 200 with `plantingApplied=true`, `FARM_FLOWER`, Recognition `30`, targeted WorldChange `681`, and crop object `681`. Before planting, state projected object `647/FARM_PLOT_EMPTY`; afterward it projected only `681/FARM_FLOWER` at pixel `(144,432)`. PostgreSQL retained empty row `647` while recording exactly one Recognition row, one recognition-linked/target-linked WorldChange, and one crop object. An identical retry returned HTTP 200 with the same Recognition and WorldChange and no row delta. A second Photo against target `647` returned HTTP 409 with `TARGET_ALREADY_PLANTED`.

The final 2-C1 backend regression completed 521 tests with 0 failures, 0 errors, and 4 existing opt-in skips in 1 minute 32 seconds. The planting suites account for 20 new test invocations over the 501-test 2-B1 baseline, including explicit preservation of an unrelated Photo → Recognition → WorldChange → WorldPlacedObject chain, template object ids, and persisted player position. The concurrent fixture performs a real two-transaction race and then clears only committed mutable test data while retaining application reference seeds; the concurrency-to-signup/daily regression sequence and the complete Maven suite both passed. `./mvnw package` and `git diff --check` were also required as the final closure gates.

Frontend target-aware capture integration has not started in 2-C1. Crop growth, harvest, economy, inventory, quests, crop selection UI, new providers, and media/thumbnail work remain out of scope.

## 2-C3 concurrency evidence

`WorldPlantingConcurrencyIntegrationTests` now covers four terminal concurrency/idempotency contracts with real transactions:

- Different Photos racing for one target produce one crop and one `TARGET_ALREADY_PLANTED` loser.
- The same Photo and target return the same terminal identity to both callers while persisting one Recognition, Memory Classification, targeted WorldChange, and crop.
- The same Photo racing for two targets creates at most one crop; the loser receives `PHOTO_ALREADY_EXPRESSED` and its empty target remains unchanged.
- Retrying after a committed response loss returns the same identity with zero Recognition, Memory Classification, WorldChange, and placed-object deltas.

The conflict assertions also preserve the winner, the original append-only target row, and unrelated template rows. The pessimistic Photo lock serializes Photo reuse, the pessimistic target lock serializes target reuse, and V9's unique target constraint remains the database defense. No raw constraint exception is exposed as an HTTP/domain result.

## 2-C3 PostgreSQL verification

The concurrency suite was executed against dedicated local PostgreSQL database `project_eden_c3_concurrency`, restored from the project's pre-V1 application-schema baseline. Flyway validated ten successful history entries: baseline V0 and migrations V1 through V9. PostgreSQL 16.14 initialized Hibernate with `ddl-auto=validate`, and all four concurrency tests passed in 11.273 seconds with no failure, error, or skip.

The verification command supplied the dedicated PostgreSQL datasource and ran only `WorldPlantingConcurrencyIntegrationTests`. An empty database is not sufficient for this repository because V1 begins the additive Memory Taxonomy migrations and the older core application schema predates Flyway V1; therefore the dedicated database used the existing local baseline without reading from or mutating the development database during tests.

## 2-C3 transaction evidence

Target and Photo conflicts leave no partial losing Recognition, Memory Classification, WorldChange, or crop rows. A losing different-target attempt retains its empty target. A second committed retry cannot overwrite or duplicate the first terminal rows. No production failure flag, reset endpoint, migration change, or test-only production hook was added.

## Final 2-C backend status

The final backend regression completed 524 tests with 0 failures, 0 errors, and 4 existing opt-in skips in 1 minute 7 seconds. `./mvnw package` repeated the same 524-test result and produced the executable archive successfully in 1 minute 14 seconds. The standalone H2 concurrency run and the required PostgreSQL concurrency run both passed 4/4. `git diff --check` passed after the documentation update.

Village MVP Polish 2차-C backend planting persistence, concurrency, idempotent retry, and transaction evidence are complete. Crop growth, harvest, economy, inventory changes, NPC progression, and additional community features remain out of scope.
