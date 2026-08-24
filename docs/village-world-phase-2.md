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

The evidence run restarted the backend from the backend repository root on port 8080, with the `local` Spring profile and PostgreSQL database `project_eden` at `jdbc:postgresql://localhost:5432/project_eden`. Flyway applied V8 and Hibernate/Tomcat started successfully. Host-specific paths and process IDs are intentionally omitted.

The frontend was restarted from the frontend repository root on `127.0.0.1:5173`; it used the local backend at `http://localhost:8080`.

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

## Village MVP Polish 2차-D1 template NPC TALK contract

The four official Village NPCs remain template `WorldPlacedObject` rows: `DEFAULT_NPC_GUIDE`, `DEFAULT_NPC_GARDENER`, `DEFAULT_NPC_MEMORY_KEEPER`, and `DEFAULT_NPC_ANIMAL_CARETAKER`. Their typed `npcPositions` identities and `availableInteractions` target ids are owned by the authenticated character. They are deliberately not linked to the separate legacy `Npc.id`, `/api/npcs/{id}/dialogue`, or `NpcMemory` persistence model.

TALK availability is derived only from the persisted `WorldPlayerPosition`. Every template NPC exposes exactly one TALK interaction from each cardinally adjacent tile, none from a diagonal or Manhattan distance greater than one, and no target belonging to another character. The response preserves target id, exact asset type, display name, and coordinates. Existing server ordering remains `TALK > INTERACT > INSPECT`; no production contract, endpoint, table, column, or migration changed for D1.

`WorldTileInteractionIntegrationTests` now verifies all four NPCs across all four cardinal directions, diagonal and out-of-range exclusion, exact target metadata, duplicate prevention, stored-position authority through the authenticated state endpoint, and cross-character ownership isolation. The targeted class completed 19 tests with zero failure, error, or skip. The complete backend regression completed 528 tests with zero failures, zero errors, and four existing opt-in skips in 1 minute 3 seconds.

## Village MVP Polish 2차-D2 COMMUNITY/ANIMAL contract

`COMMUNITY_HOUSE`, `DEFAULT_DOG`, `DEFAULT_CAT`, and `DEFAULT_BIRD` remain read-only template `WorldPlacedObject` interactions. The authenticated world-state response exposes them as `INTERACT` from exactly one cardinal tile: the community house uses category `COMMUNITY`, display name `마을 회관`, and action `둘러보기`; the three animals use category `ANIMAL`, their asset-specific Korean display names, and action `다가가기`.

No endpoint, DTO, persistence model, migration, animal entity, or community interior was added. Interaction ownership remains character-scoped, diagonals and distances greater than one remain unavailable, and the existing server order remains `TALK > INTERACT > INSPECT`. When the template contains more than one object with the same asset type, range assertions bind to the exact coordinate and target id rather than treating a different owned object as the same target.

`WorldTileInteractionIntegrationTests` now verifies the four read-only targets from all four cardinal directions, exact target id/asset/name/category/action metadata, diagonal and out-of-range exclusion, duplicate prevention, cross-character isolation, and unchanged priority. `VillageIntegrationTests` verifies that the existing authenticated history response never returns another character's category history. The targeted run completed 42 tests with zero failure, error, or skip.

The final D2 backend regression completed 532 tests, 0 failures, 0 errors, and 4 existing opt-in skips in 1 minute 10 seconds. `./mvnw package` repeated all 532 tests and produced the executable archive successfully in 1 minute 11 seconds. Backend production code remained unchanged.

## Village MVP Polish 2차-D final contract evidence

The D3 closure reran the combined `WorldTileInteractionIntegrationTests` and
`VillageIntegrationTests` contract as 42 tests with no failure, error, or skip.
The four template NPCs passed cardinal TALK, diagonal/distance exclusion,
persisted-player-position authority, ownership isolation, exact target
metadata, and duplicate prevention. `COMMUNITY_HOUSE`, `DEFAULT_DOG`,
`DEFAULT_CAT`, and `DEFAULT_BIRD` passed the equivalent read-only INTERACT
contract. Candidate order remained `TALK > INTERACT > INSPECT`, with equal
priority ordered deterministically by `y`, `x`, asset type, and target id.

The final complete Maven run passed 532 tests with 0 failures, 0 errors, and 4
existing opt-in skips in 1 minute 6 seconds. `./mvnw package` independently
repeated all 532 tests and produced the executable archive in 1 minute 20
seconds. No PostgreSQL-specific D interaction script exists; D1/D2 introduced
no database contract or migration, so the established test-profile integration
contract was the applicable backend evidence. Backend production code was not
changed during D3.

## Village MVP Polish 2차-D final status

Template NPC dialogue closure and Community/Animal read-only interaction
closure are complete. The browser evidence, responsive matrix, network
no-mutation checks, console checks, two consecutive aggregate runs, and
fixture-isolation audit are recorded in the frontend contextual-interaction
document. Physical iPhone Safari, persistent dialogue progress, animal
mutation, Community interior, and a full focus trap remain outside this
read-only MVP boundary.

## Compact server-authored village and persistent animals

The live village now uses the backend 24×16 grid as its exact visual contract
(1152×768 pixels at 48 pixels per tile). Desktop rendering centers the complete
board instead of following the player across a larger synthetic canvas. Mobile
continues to use the player camera. Decorative frontend-only house, pond,
bridge, tree, flower, lamp, cat, and bird instances were removed from the live
scene; terrain and visible village residents are rendered from
`terrainTiles` and `placedObjects` only.

Animal memories follow the existing persisted chain:

`Photo → Recognition → WorldChange → WorldPlacedObject`.

`DOG`, `CAT`, and `BIRD` Recognition results project to typed
`DEFAULT_DOG`, `DEFAULT_CAT`, and `DEFAULT_BIRD` placed objects. Each distinct
Recognition receives one database-generated object id. Reprocessing the same
Recognition returns its existing WorldChange and object ids. Seed animals are
distinguished by a null Recognition on their template WorldChange, while
photo-origin animals retain their source through
`WorldPlacedObject → WorldChange → Recognition → Photo`; no client-created
animal is part of the contract.

Animal placement is deterministic, tile-aligned, in bounds, walkable, and
excludes the player and occupied world-object tiles. Persisted animal positions
are the same coordinates used by `availableInteractions`. Movement is driven
by one world-state synchronization path: a thirty-second world-level
checkpoint permits a bounded batch of at most eight animal moves. There is no
timer, endpoint call, or scheduler per animal. Animal movement piggybacks on
existing world-state synchronization (initial load, movement-session end,
reveal, and explicit refresh), so it adds no polling request. The frontend
renders every instance with its persisted object id as the React key. Position
transitions interpolate server-approved coordinates without creating a second
authoritative client position.

## Phase 3-A finite-world foundation

Phase 3-A keeps the existing 24×16 hub intact while making its finite bounds
explicit. `World` owns `minTileX`, `maxTileX`, `minTileY`, `maxTileY`, and the
fixed-village `generationVersion`. Flyway V11 backfills existing rows to
`0..23 × 0..15` without changing ids or related data. New worlds receive the
same defaults. Movement, bootstrap, animal movement, and animal spawn now read
the owning world's bounds rather than static service constants.

The authoritative coordinate contract is global tile coordinates for terrain
and players, and global pixel coordinates for `WorldPlacedObject`. One
`WorldCoordinates` utility owns the 48-pixel conversion. Pixel-to-tile uses
mathematical floor, including negative coordinates. Indexed repository range
queries are prepared for a later range API, but `GET /api/worlds/me/state`
remains the compatible full-state endpoint. Its existing fields are unchanged;
`tileSize` and `generationVersion` are additive.

Official NPCs remain template NPC `WorldPlacedObject` rows. The separate legacy
Region/Npc model is not synchronized and is a deprecation candidate.

## Phase 3-A camera and render-window contract

Desktop and mobile use one camera scale (`1.1`) and one 48-pixel coordinate
module. Player interpolation writes the same camera variables consumed by the
scene transform. The camera follows the player and clamps at finite edges; a
world smaller than the safe viewport is centered. Reduced-motion clients
complete a server-approved step without animation.

The frontend still receives full state, but memoizes a viewport render window.
Terrain has a one-tile buffer, objects a three-tile footprint buffer, and
interaction highlights a one-tile buffer. Open TALK/context targets are pinned
by persisted target id; the selected INSPECT tile remains pinned while
available. The player is never part of a filtered collection. Hub art is
namespaced and grouped into road, farm, plaza, pond, and community features.

## Phase 3-A terrain and visual-footprint audit

The following findings describe the pre-Phase 3-B state. The bridge and
Community House mismatches are closed by the Phase 3-B contract and V13
migration documented below.

- ROAD: server movement uses the cross `x=11 || y=7`; curved hub paths are
  decorative and do not grant movement, so the footprints are not identical.
- POND: logical WATER is `x>=17 && y>=11`; the organic visual pond lies within
  that area and does not define collision.
- BRIDGE: the current bridge is decoration; the template seeds no BRIDGE tile.
  Traversable bridge reconciliation is deferred.
- COMMUNITY_HOUSE: its persisted interaction anchor is tile `(7,4)`, while
  server BUILDING collision is `x=13..15, y=3..5`. The large sprite and
  collision footprint are therefore not yet identical.

Logical tiles control movement, visual footprints control drawing, collision
comes from terrain, and interaction anchors come from persisted objects.
Phase 3-B should reconcile these audited footprints before outer discovery.

## Phase 3-A rollback and next boundary

Rollback can stop consuming additive metadata and restore full DOM rendering;
V11 defaults preserve the previous finite world. Phase 3-A adds no WorldChunk
table, discovery, procedural generation, outer decoration, minimap, or chunk
API.

## Phase 3-A verification evidence

The PostgreSQL Testcontainers migration test first migrated a database to V10,
inserted an existing World, and then applied V11. It verified the backfilled
`0:23:0:15:1` bounds/version tuple, all five columns, the bounds check, three
range indexes, Flyway validation, idempotent re-migration, and Hibernate schema
validation. The complete backend regression passed 556 tests with 0 failures,
0 errors, and 4 existing opt-in skips in 1 minute 2 seconds. The executable
package repeated 556 tests with the same result and completed successfully in
1 minute 5 seconds.

The Phase 3-A Playwright suite passed 5 tests. With 384 terrain rows and 35
objects in full state, measured DOM counts were:

- 1440×900: 384 terrain, 35 objects, one player.
- 390×844: 176 terrain, 32 objects, one player.
- 430×932: 208 terrain, 35 objects, one player (the object footprint buffer
  legitimately covers the compact hub at this viewport).

One hundred deterministic render-window calculations remained below 384
terrain nodes. The established ten-second real keyboard hold issued 23
serialized move requests with max in-flight 1, no rollback, no console error,
and matching server/screen position. The full existing Village Playwright
regression passed all 61 tests in 4 minutes 54 seconds. The test suite does not
expose a reliable browser-internal listener counter; stable single scheduler
and non-accumulating keyed DOM are the available evidence. A dedicated
30-second movement measurement was not executed; the verified ten-second
runtime hold and 100-window deterministic test are reported without inflating
them into a 30-second claim.

## Phase 3-B WorldChunk contract

Phase 3-B introduces additive `world_chunks` metadata without changing the
ownership of terrain, placed objects, memories, photos, or animals. A chunk is
8×8 global tiles. Both runtimes use floor division and floor modulus, so the
contract is also correct for future negative global coordinates. Persisted
placed objects keep global pixel anchors; their chunk is derived through the
single 48-pixel tile conversion helper and is not duplicated in storage.

Flyway V12 creates the chunk table, the unique
`(world_id, chunk_x, chunk_y)` constraint, coordinate/discovery indexes, and
backfills each existing 24×16 World with the six generated HUB chunks
`0..2 × 0..1`. Runtime bootstrap holds the owning World row lock and repairs
only missing HUB metadata, so partial metadata is recovered without moving or
deleting existing content. New worlds receive the same six rows when their
world state or chunk state is first synchronized.

## Phase 3-B chunk API and range queries

`GET /api/worlds/me/chunks?centerChunkX={x}&centerChunkY={y}&radius={0..2}`
returns clipped persisted chunks only. Radius defaults to one; negative values
and values greater than two are rejected. The response includes the finite
World bounds, tile/chunk sizes, authoritative player position, current
interactions, and each chunk's terrain, canonical placed objects, NPC
projection, and animal projection.

After the one world-level synchronization checkpoint, terrain and object
payloads are each loaded by one ownership-scoped rectangular query and grouped
in memory by the shared coordinate helper. There is no query per chunk.
Ordering is stable (`chunk y/x`, terrain `y/x`, object id). Canonical placed
object ids are unique; the NPC/animal lists are typed projections and are not
additional persisted identities.

Chunk `version` is a SHA-256 content fingerprint over ordered metadata,
terrain, and object identity/type/position. Terrain mutation, object creation,
movement, deletion, and discovery metadata therefore produce a different
version without requiring a duplicate chunk foreign key on every content row.
This contract also covers cross-chunk animal movement: both the old and new
chunk fingerprints change according to their resulting content.

## Phase 3-B animal checkpoint safety

`/state` and `/chunks` use the same persisted `last_animal_movement_at`
checkpoint. The owning World row is selected with a pessimistic write lock
before synchronization. Repeated preload requests and a `/state` then
`/chunks` sequence inside one 30-second bucket cannot execute a second animal
move. Movement remains bounded to eight animals. The current animal
synchronization still reads the finite hub when a checkpoint is due; replacing
that scan with region-local scheduling is deferred until worlds can expand.

## Phase 3-B frontend cache and preload

The frontend keeps a bounded `Map<"chunkX:chunkY", ChunkState>` with a maximum
of 25 entries. It merges identical in-flight range requests, tracks request
sequence per chunk, rejects late stale responses, compares content versions,
and synthesizes terrain and objects with coordinate/id deduplication. World or
authenticated-user reset clears the cache. The current player chunk and one
active interaction target chunk are protected from LRU eviction.

Initial world load fetches radius one around the authoritative player. Every
accepted movement starts a non-blocking radius-one preload around the approved
target chunk; failed preload leaves movement and the compatible `/state`
payload intact. Render DOM continues to use the Phase 3-A viewport window, so
cache retention does not imply DOM retention. The six-chunk finite hub fits
entirely in the current cache while preserving the expansion-ready policy.

## Phase 3-B compatibility and footprint gate

`GET /api/worlds/me/state`, movement, planting, template interactions,
Recognition, photos, and persisted coordinates remain compatible. Movement
across tile boundaries 7→8, 15→16, and y 7→8 is server-authoritative and does
not depend on frontend cache presence. A missing in-bounds chunk is repaired
under the World lock before movement; out-of-bounds continues to use the
existing reason.

The Phase 3-A visual/logical audit is now reconciled for the two fixed hub
landmarks:

- ROAD decorative curves do not grant movement beyond logical ROAD.
- POND rendering crosses chunk boundaries as one buffered HUB decoration;
  logical WATER remains authoritative.
- BRIDGE occupies the continuous authoritative row `(17..22, 13)`, connects
  entry `(16, 13)` to the eastern bank `(23, 13)`, and is walkable. Adjacent
  WATER remains blocked. The bridge artwork covers exactly those six tiles.
- COMMUNITY_HOUSE keeps its existing object and WorldChange identities while
  moving their shared door anchor to `(14, 6)`. Its visual three-by-three body
  covers the BUILDING footprint `(13..15, 3..5)`, its entrance is ROAD, and
  interaction is available only from the front approach `(14, 7)`.

The coordinate values are named in `WorldHubLayout` and the frontend
`worldHubLayout.js`; contract tests assert their equivalent footprints.
Runtime bootstrap applies the reconciliation idempotently to new and existing
worlds.

## Phase 3-B bridge and house migration

`V13__align_bridge_and_community_house_footprints.sql` updates only the named
hub terrain coordinates, the template house focus/object coordinates, and the
template version. It does not delete or recreate terrain, world changes,
placed objects, photos, recognitions, memories, animals, or player positions.
The house object ID and WorldChange relationship are preserved.

The PostgreSQL/Testcontainers migration path reached V13 from V11 by applying
V12 and V13, executed zero migrations on the idempotency retry, and completed
Hibernate schema validation. A runtime existing-world test also bootstrapped
twice and verified that the same house object and WorldChange IDs remained.

## Phase 3-B landmark closure evidence

The targeted template and interaction suite passed 32 tests with no failure,
error, or skip. The real Playwright landmark suite passed 4 tests: it crossed
the bridge entry, center, and exit; rejected adjacent WATER; opened the
community summary at the front door; rejected BUILDING body movement; verified
wrong-side interaction absence; and captured desktop/mobile screenshots.

## Phase 3-B verification evidence

The targeted backend contract completed 13 tests (9 WorldChunk integration and
4 coordinate tests) with no failure, error, or skip. Measured serialized
payloads for the same finite hub were 35,725 bytes for `/state`, 5,883 bytes
for radius 0 (64 terrain rows), and 30,251 bytes for radius 1 or 2 (384 terrain
rows after bounds clipping). Terrain and placed-object content use two
range queries per chunk request rather than one query per chunk; the surrounding
ownership/checkpoint queries were not instrumented as a fabricated fixed total.

The complete backend regression passed 571 tests with 0 failures, 0 errors,
and 4 existing opt-in skips in 1 minute 14 seconds. The executable package run
also passed all 571 tests and completed successfully in 1 minute 12 seconds.
The PostgreSQL Testcontainers path applied V12 and V13, verified six
backfilled HUB chunks, performed an idempotent second migrate, and started
Hibernate against the resulting schema. The active local PostgreSQL fixture
for character `406` retained 384 terrain rows and contained exactly six
walkable BRIDGE rows, five eastern-bank rows, nine blocked house-body rows,
and one walkable house-door row. Its Community House object `14525` retained
WorldChange `14221`, and both anchors were `(672, 288)`.

Frontend cache tests passed 8 cases covering floor coordinates, identical
in-flight request merging, stale response rejection, authoritative full-state
priority, deterministic merge, six-chunk state seeding, bounded LRU/pinning,
and world reset. The established Village Playwright regression passed all 61
tests in 6.6 minutes. The Phase 3-A viewport regression passed all 5 tests,
and the Phase 3-B landmark closure suite passed all 4 tests with the ten
required desktop/mobile screenshots saved under
`/private/tmp/project-eden-phase3b-closure`.
The real 10.2-second keyboard hold produced 23 serialized move requests with
maximum in-flight 1, no rollback, no console/page error, and equal server/screen
positions. Phase 3-B does not claim an unexecuted 30-second hold or an
unavailable browser-internal listener count. Lint and the production Vite
build passed with no warning.
# Phase 3-C Deterministic Outer Regions

## World expansion

Phase 3-C keeps the original HUB at tile coordinates `0..23 × 0..15` and expands
the finite world to `-8..31 × -8..23`. The result is 1,280 tiles in twenty 8×8
chunks. Existing HUB terrain, objects, memories, animals, and player coordinates
are not translated or recreated. Migration V14 updates the bounds and V15 advances
the connector-aligned generation version; the six HUB chunks remain eager and the fourteen outer chunks
remain lazy.

## Region templates and selection

`world/region-templates-v2.yml` is the versioned source for MEADOW, FOREST, and
POND. Startup validation enforces 8×8 patterns, canonical terrain tokens,
connectors, spawn zones, and region/template identity. Selection is a pure
function of world seed, chunk coordinate, and generation version. The first ring
guarantees accessible examples: MEADOW west, FOREST east, and POND south, with
additional fixed north examples. Templates expose ROAD connectors at offset 3
north/south and offset 7 east/west so the existing HUB roads connect without
moving a HUB tile. The POND template uses real WATER collision and a walkable BRIDGE.

## Lazy generation and discovery

Chunk preload and movement both call the same transactional generator. A locked
world row and the unique `(world_id, chunk_x, chunk_y)` key serialize concurrent
creation. Exactly 64 terrain rows are required; incomplete generated metadata is
repaired deterministically. Preload generates but does not discover. A successful
move into an outer chunk records its first `discoveredAt`; repeat visits preserve
that timestamp and do not replay the reveal.

## Frontend integration

The chunk cache distinguishes ungenerated/loading/generated/failed data, merges
discovery monotonically, and does not synthesize expanded bounds as loaded.
Negative global coordinates are translated by a dedicated coordinate layer while
the camera continues to follow authoritative global player coordinates. HUB-only
visual decoration is clipped to the original HUB. Outer templates render their
own deterministic meadow, forest, and pond decoration. First discovery shows a
short screen-space region title; reduced-motion users receive the same content
without animation.

## Phase 3-C validation

Automated integration evidence covers six eager HUB chunks, lazy 9/20 chunk
radius responses, deterministic repeated POND responses, guaranteed region
selection, 64-row completeness, WATER/BRIDGE presence, first discovery, repeat
visit suppression, ownership isolation, and serialized simultaneous requests.
The prior `/state`, `/chunks`, and `/move` contracts remain additive-compatible.

The complete backend regression passed 573 tests with 0 failures, 0 errors,
and 4 existing opt-in skips. The executable package build also completed
successfully. Flyway V15 applied to the local PostgreSQL runtime, followed by
successful Hibernate validation and application startup.

The frontend region contract suite passed 3 tests and the complete established
Village browser regression passed all 81 tests. The real region journey visited
HUB, MEADOW, FOREST, and POND, rejected ordinary WATER, crossed the POND BRIDGE,
returned to HUB, and preserved discovery on revisit. Its 30.007-second movement
measurement issued 183 serialized move requests with maximum in-flight 1.
Desktop and mobile-touch evidence produced 16 screenshots under
`/private/tmp/project-eden-phase3c`; the final mobile screenshot is explicitly a
region revisit, not a first-discovery banner claim. Frontend lint and the
production Vite build completed without warnings.

The regression exposed and fixed two movement synchronization defects: updating
movement callback dependencies could cancel a held scheduler, and a cancelled
RAF identifier could keep the movement-end state refresh pending indefinitely.
The final 10.226-second keyboard hold issued 31 serialized requests, reached the
finite eastern boundary, retained maximum in-flight 1, and ended with matching
server and screen positions.

## Phase 3-C closure evidence

The closure suite uses PostgreSQL 16 through Testcontainers, runs Flyway through
V15, and uses the production transaction and repository graph. Five requests are
released from a `CountDownLatch` barrier against the same ungenerated FOREST
chunk. They finish without deadlock as one `WorldChunk`, exactly 64 unique terrain
coordinates, zero required objects for the current template contract, identical
`FOREST_V3` metadata/version, and no cross-world changes. Five concurrent warm
requests preserve the same version, `generatedAt`, terrain count, and object
count. The measured five-request wall clock was 207 ms cold and 130 ms warm.

Order-independent fixtures cover A→B, B→A, and simultaneous A/B generation.
Region selection, connector offsets, edge terrain, decoration, versions, and
walkability remain identical. An outer transaction deliberately throws after
generation and leaves zero metadata, terrain, and objects; the next request
retries cleanly. Repair fixtures cover `FAILED + 10 terrain`,
`GENERATED + 63 terrain`, and `metadata missing + 10 terrain`. Every repair
returns to 64 canonical terrain rows, a second repair is a no-op, and an existing
`discoveredAt` is preserved. Required-object repair is not applicable in version
3 because all three templates currently declare an empty required-object list.

Five concurrent first-discovery calls produce exactly one
`newlyDiscovered=true`, four false results, and one preserved timestamp. Five
repeat calls are all false, and an independent world remains undiscovered.
The browser logout path clears the access token, cache, in-flight chunk work,
movement scheduler, RAF, reveal timer, and key handler. Logging the same isolated
user back in restores the same FOREST `discoveredAt` from the backend, and
re-entry does not display the first-discovery reveal again.

## Phase 3-C performance and query evidence

Hibernate Statistics measured five cold and five warm generations per region:

- MEADOW: cold 31/33/56 ms min/median/max; warm
  3,281/4,731/8,879 µs.
- FOREST: cold 26/28/30 ms; warm 2,843/3,260/3,809 µs.
- POND: cold 23/27/31 ms; warm 2,571/3,589/4,634 µs.

Each cold generation recorded 69 prepared statements, 65 entity inserts, and one
successful transaction. Thus terrain creation is not split into 64 transactions.
The current Hibernate configuration did record zero JDBC batches, so the 64
terrain rows remain individual insert statements within that one transaction;
this measurement is evidence, not a claim that JDBC batching is enabled.
Cold radius-zero/radius-one reads, including lazy generation, prepared 95/245
statements. After generation, the corresponding warm reads prepared 29/47
statements. A discovery move prepared 22 statements. No seconds-scale generation,
deadlock, or unbounded per-request growth was observed. The template checksum
remained `b8c39a4af860263444324a55a76592b9d2ac37eae3c28125f83fc363dbea7d54`.

## Phase 3-C runtime and visual evidence

A development-gated diagnostic counter—not a global browser monkey patch—tracks
only Eden movement schedulers, RAF loops, chunk requests, reveal timers, and key
handlers. A real 30,000 ms held keyboard input issued 39 move requests. It
recorded one scheduler start, maximum one active scheduler, 39 RAF starts and 39
stops, maximum one active RAF, maximum one in-flight chunk request, and one reveal
timer. All active counters were zero after logout. Relogin installed exactly one
key handler.

The isolated 390×844 touch fixture captured:

- `mobile-before-discovery.png`: player on the HUB/MEADOW boundary before entry.
- `mobile-first-reveal.png`: the accepted joystick move, MEADOW terrain, player,
  and “새로운 지역 발견 · 초원” visible together.
- `mobile-after-reveal.png`: the same region after the timer completed.
- `mobile-repeat-visit-no-reveal.png`: a repeat visit with
  `newlyDiscovered=false` and no reveal.

The files are stored only under
`/private/tmp/project-eden-phase3c-closure`. The test asserts the move response,
banner visibility/expiry, terrain/player visibility, and repeat suppression; it
does not rely on a previously consumed browser profile.

All twenty finite chunks were evaluated, including all fourteen generated outer
chunks. Each outer chunk has 64 terrain rows, connected walkable terrain, and
reachable connector cells. HUB paths reach the guaranteed MEADOW, FOREST, and
POND entrances and allow return travel. No connector mismatch or isolated
walkable region was found.

Final regression evidence: backend `./mvnw test` passed 580 tests with zero
failures, zero errors, and four existing opt-in skips; package creation succeeded.
The established Village Playwright regression passed 61/61. Phase 3 cache/region
contracts passed 11/11, the closure browser suite passed 3/3, the Phase 3-A
viewport suite passed 5/5, the bridge/house footprint suite passed 3/3, and the
region journey passed 2/2. Frontend lint and production build completed without
warnings. Both repository diff checks passed. No migration, add, commit, or push
was performed during closure.
