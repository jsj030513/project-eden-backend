# Village World Phase 4-B1 — Region-aware Photo Ecology Placement

## Goal

Photo → Recognition → WorldChange → WorldPlacedObject 흐름을 유지하면서 신규 사진 표현을 서버가 발견된 compatible region의 안전한 spawn zone에 결정적으로 배치한다. 기존 좌표와 ID는 이동·교체하지 않는다.

## Current Placement Audit

- 이전 일반 recognition은 HUB 후보 좌표에 의존했고 chunk discovery 및 region template을 배치 입력으로 사용하지 않았다.
- targeted planting은 `POST /api/worlds/me/plant-memory`가 exact empty plot을 authoritative target으로 사용한다.
- seed animal과 사진 animal은 `WorldChange.recognition`으로 구분 가능하다.
- chunk projection의 `contentVersion`은 object projection을 포함하므로 신규 object가 cache version에 반영된다.

## Ecology Profile Contract

`world/photo-ecology-profiles-v1.yml`은 version 1 registry다. 모든 `RecognizedObject` 48개가 정확히 하나의 profile에 매핑된다. Validator는 duplicate type, enum/asset, preferred subset, 양수 capacity, fallback, version 및 전체 coverage를 검사하고 raw resource SHA-256을 제공한다.

- DOG: MEADOW 우선, HUB fallback, LAND, `DEFAULT_DOG`
- CAT: FOREST/MEADOW 우선, HUB fallback, LAND, `DEFAULT_CAT`
- BIRD: POND/FOREST/MEADOW 우선, WATER 제외, AIR, `DEFAULT_BIRD`
- FLOWER/PLANT: MEADOW/FOREST 우선, `FLOWER_CLUSTER`
- crop/food 일반 사진: 농장 plot을 소비하지 않는 `BAKERY_DETAIL` memory expression
- UNKNOWN: `NON_PLACEABLE`, object를 만들지 않음

## Region Compatibility and Spawn Zones

MEADOW, FOREST, POND template의 zone은 tag, local rectangle, category/asset allowlist, terrain, capacity, spacing, access, movement 및 priority를 가진다. HUB는 기존 template을 변경하지 않는 bounded safe-zone adapter를 사용한다.

- HUB: HOUSE_EDGE, FARM_EDGE, FLOWER_GARDEN, POND_EDGE, SAFE_MEMORY_ZONE
- MEADOW: OPEN_GRASS, FLOWER_PATCH
- FOREST: CLEARING, FOREST_ENTRANCE
- POND: SHORE, REED_EDGE

Validator는 bounds, coordinate overlap, connector/protected conflict, terrain, accessible cardinal neighbor와 capacity ≤ coordinate count를 확인한다.

## Candidate and Placement Scoring

후보 chunk는 현재 world 소유이며 `GENERATED`이고 `discoveredAt != null`인 compatible chunk만 사용한다. preload-only/undiscovered/failed chunk는 후보가 아니며 placement가 chunk generation을 유발하지 않는다.

Candidate query는 zone 좌표와 cardinal neighbor key만 한 번에 조회한다. 전체 1,280 terrain을 읽거나 tile별 query를 수행하지 않는다. 점수는 profile priority, preferred region/terrain, zone priority, player distance와 world seed + recognition/photo + asset + chunk/zone/tile FNV-1a tie-break로 구성된다. 시간, random, repository iteration order는 사용하지 않는다.

## Occupancy, Capacity, and Fallback

서버는 terrain, object occupancy, player, NPC runtime tile, same-species spacing, cardinal access와 region contract를 검사한다. WATER, BUILDING, SOIL, ROAD, BRIDGE는 ecology spawn에서 제외한다.

- ecology object per chunk: profile limit과 hard cap 12 중 작은 값
- animal profile cap: 8/chunk
- zone: profile maxPerZone, zone capacity 중 작은 값
- full/no-safe/not-placeable: 기존 object를 삭제하거나 undiscovered chunk를 사용하지 않고 `placementApplied=false`
- reasons: `NO_COMPATIBLE_REGION`, `CAPACITY_REACHED`, `NO_SAFE_SPAWN_TILE`, `PROFILE_NOT_PLACEABLE`

Lock order는 World pessimistic lock → post-lock recognition recheck → candidate read → WorldChange → object다. 일반 좌표 unique constraint는 targeted replacement가 같은 pixel을 의도적으로 공유하므로 추가하지 않았다. World lock과 transaction recheck가 신규 일반 placement의 DB 최종 방어다.

## Persistence and Migration

V20은 WorldChange에 profile/category/applied/region/chunk/zone/version/reason/time audit column과 lookup indexes를 additive로 추가한다. Legacy row는 `LEGACY`/`LEGACY_POSITION` 또는 `TARGETED_PLANTING`으로만 표시하며 과거 선택 근거를 추정하거나 object 좌표를 변경하지 않는다.

Flyway PostgreSQL integration은 empty schema에서 21 migrations를 검증하고 V20까지 적용한 뒤 second migrate 0, Hibernate validate, ephemeral Tomcat startup을 통과한다.

## Targeted Planting Separation

`plant-memory`는 exact `FARM_PLOT_EMPTY` 좌표, crop projection, conflict/idempotency 계약을 그대로 유지한다. Targeted result는 `TARGETED_PLANTING_V1`/`EXACT_FARM_PLOT` audit을 기록한다. 일반 사진은 target context를 신뢰하거나 빈 밭을 소비하지 않는다.

## Animal Habitat Movement

Seed/legacy animals는 기존 HUB movement를 유지한다. 사진 animal은 profile allowed region이며 generated/discovered인 chunk 안에서만 이동할 수 있다. 기존 terrain/occupancy 이동 검사로 WATER/BUILDING/SOIL 및 player/NPC/object 충돌을 계속 차단한다. 동일 object row를 이동하므로 ID가 유지된다.

## API and Frontend

Recognition world result에 nullable `ecologyPlacement`를 additive로 제공한다. Chunk/state object projection에는 ecology category, source recognition, region 및 zone metadata를 추가했다. 파일 경로나 private media 정보는 없다.

HUB/인접 placement는 기존 reveal을 사용할 수 있다. 원거리 region은 camera teleport 없이 지역 notification을 표시한다. `placementApplied=false`는 Recognition을 보존하고 사용자 친화 안내 후 false mutation reveal을 만들지 않는다. 기존 targeted capture response는 유지된다.

## Quest and Affinity Compatibility

TAKE_PHOTO는 recognition source event이며 ecology placement 성공과 분리된다. 사진 animal은 canonical placed-object ID를 사용하므로 기존 ANIMAL_INTERACTION event key와 affinity/quest 처리를 재사용한다. V19 deferred replay schema와 처리 순서는 변경하지 않았다.

## Test Evidence

- Registry/profile/zone: 3 tests PASS
- Placement/capacity/movement/data safety: 6 tests PASS
- Concurrency: same photo 5 calls 및 different photo 5 calls, 2 tests PASS; duplicate object/tile 0, deadlock 0
- Migration/chunk compatibility targeted set: 23 tests PASS
- Capture/reveal Playwright: 12 passed (desktop behavior + 375/390/430 touch capture)
- Backend full regression: 615 tests, failures 0, errors 0, skipped 4, BUILD SUCCESS
- Backend package: BUILD SUCCESS (`-DskipTests`, full tests immediately before package)
- Frontend lint: PASS, warnings 0; production build: PASS
- Capture/reveal Playwright: 12 passed (desktop behavior + 375/390/430 touch capture)
- NPC schedule-boundary recovery scenarios: 6/6 repeated runs PASS
- Community panel transition scenario: 2/2 repeated runs PASS
- Full Village Playwright: 마지막 완주 결과는 61 passed / 2 failed였다. 두 실패는 장기 실행 fixture의 NPC checkpoint 위치 변화였고 이후 해당 시나리오는 targeted PASS했으나, 수정 후 63개 전체 성공 결과는 확보하지 못했다.

## Operational Debugging

WorldChange의 `ecologyProfileKey`, `ecologyCategory`, `placementApplied`, `placementRegionType`, chunk coordinates, `spawnZoneTag`, `placementVersion`, `placementReason`, `placedAt`을 사용한다. Recognition 및 object ID와 함께 조회하면 선택 profile과 terminal placement 결과를 재구성할 수 있다.

## Known Limitations

- 성장, 번식, 먹이, 수확, 경제, rarity, 사용자 직접 배치는 범위 밖이다.
- 기존 object 자동 relocation은 하지 않는다.
- 로컬 persistent PostgreSQL runtime은 `.env.local`의 DB password가 비어 있어 이번 실행에서 기동되지 않았다. PostgreSQL 증거는 Testcontainers migration/validation으로 확보했다.
- Desktop/mobile ecology species별 screenshot matrix와 30초 실제 persistent-DB browser stability는 별도 runtime credential이 필요하다.
- 전체 Village Playwright 63개 suite는 동적 NPC checkpoint와 장거리 test route의 경계 조건 때문에 최종 green 완주 증거가 없다. Ecology 전용 capture/reveal suite는 12/12 PASS다.

## Next Phase

운영 자격증명을 노출하지 않는 local runtime launcher를 마련한 뒤 실제 모델 사진 fixture, region journey screenshot, 20 discovered chunks/500 objects PostgreSQL query profile을 수행한다. 성장·번식·경제는 별도 phase로 유지한다.
