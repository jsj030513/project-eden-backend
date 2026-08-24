# Village World Phase 4-A1

## Goal

Phase 4-A1 keeps the four template `WorldPlacedObject` rows as the canonical
NPC identities and adds persistent, server-authoritative runtime state,
schedules, movement, TALK interactions, and branching dialogue.

Legacy `Region/Npc` data is not used by this runtime and remains untouched.

## Canonical Identity

The existing placed-object ID remains the public `objectId`. Stable template
keys map the seeded assets as follows:

| NPC key | Existing asset |
|---|---|
| `NPC_MAYOR` | `DEFAULT_NPC_GUIDE` |
| `NPC_GARDENER` | `DEFAULT_NPC_GARDENER` |
| `NPC_RESEARCHER` | `DEFAULT_NPC_MEMORY_KEEPER` |
| `NPC_CARETAKER` | `DEFAULT_NPC_ANIMAL_CARETAKER` |

Runtime bootstrap is idempotent and repairs missing runtime rows without
replacing the placed object or its `WorldChange`.

## Time and Schedule

- Clock authority: backend `Clock`
- Timezone: UTC
- Eden day length: real 24 hours
- Date key: UTC ISO local date
- Checkpoint cadence: 5 seconds by default
- Catch-up: at most one tile per checkpoint

Schedules are versioned in `world/npc-schedules-v1.yml`. Each NPC has three
non-overlapping slots that cover the complete day and uses `NpcWorldAnchor`
named HUB anchors. Coordinates live in that registry rather than being
duplicated in schedule resources. Canonical identity descriptors also expose
their home anchor, cardinal interaction range, and enabled state.
Requests to `/state`, `/chunks`, and `/move` never advance the schedule.

## Runtime Persistence

`npc_runtime_states` stores one row per canonical NPC object:

- current global tile
- activity and schedule slot
- schedule date key
- last checkpoint
- monotonic state version

`WorldPlacedObject` remains the immutable identity/home anchor. Runtime state
is the authoritative coordinate used by world state, interactions, collision,
and chunk projection.

## Movement and Collision

The checkpoint worker moves toward the current named destination by at most
one Manhattan tile. Every candidate is checked against world bounds,
generated terrain, walkability, placed-object collision, player position, and
other NPC runtime positions. Failed movement leaves the last approved state
unchanged.

NPC tiles block player movement with `NPC_BLOCKED`. Active dialogue sessions
pin their NPC until completion, close, or expiry.

The scheduler is disabled under the Spring `test` profile; integration tests
invoke checkpoints explicitly with deterministic state.

## Chunk Contract

Static placed-object content and dynamic NPC movement use separate versions.
Canonical NPCs are excluded from static chunk objects and projected once from
runtime coordinates.

- `version`: static terrain/object fingerprint
- `npcStateVersion`: dynamic NPC state version

When an NPC crosses a chunk boundary, the old chunk contains no copy and the
new chunk contains exactly one projection. The frontend retains the highest
state version so a stale response cannot move an NPC backward.

## TALK and Dialogue

TALK is calculated from the current runtime coordinate and is cardinal-range
only. Dialogue definitions live in `world/npc-dialogues-v1.yml`; all four NPCs
have a start node, two selectable branches, a completion route, and no world
mutation effects.

Endpoints:

- `POST /api/worlds/me/npcs/{objectId}/dialogues/start`
- `POST /api/worlds/me/npcs/{objectId}/dialogues/{sessionId}/choices/{choiceId}`
- `POST /api/worlds/me/npcs/{objectId}/dialogues/{sessionId}/close`

The backend validates ownership, canonical identity, runtime range, enabled
interaction, session/NPC binding, expiry, node, and choice. Duplicate active
start returns the same session, while duplicate terminal choice returns the
same completed result.

## Conversation Persistence

`npc_conversation_states` is unique by character and NPC object. It stores
first/last talk timestamps, completed conversation count, and the last
completed dialogue key. Completion is idempotent and isolated by the
authenticated character.

Open sessions are persisted separately with an expiry and completion state.
An open UI session may be closed on refresh while completed conversation
history remains.

## Frontend

World state and chunks render runtime NPCs with the canonical object ID as
their React/DOM identity. Activity is displayed beside the NPC. Coordinate
changes use a short CSS transition and become immediate under
`prefers-reduced-motion`.

The dialogue panel provides the server node and choices, loading/error state,
Escape/close behavior, and 44-pixel touch targets. Opening dialogue disables
keyboard and joystick movement and cancels pending player movement work.
Closing restores movement.

Chunk cache entries carry runtime NPC projections. Highest state version wins,
old chunk copies are removed, and the active dialogue target chunk remains
pinned.

## Compatibility

The change is additive to `/state` and `/chunks`; `/move` gains the explicit
`NPC_BLOCKED` rejection. HUB layout, outer regions, bridge, community house,
farm, animal, capture, planting, photo, recognition, and memory persistence
remain on their existing contracts.

Existing Playwright navigation helpers treat runtime NPC coordinates as
blocking tiles, matching production movement authority.

## Checkpoint Performance Evidence

The closure test uses PostgreSQL 16 in Testcontainers, four persisted canonical
NPCs, a Hibernate `StatementInspector`, and five measured warm runs per
scenario.

| Scenario | Latency | SELECT | UPDATE | Result |
|---|---:|---:|---:|---|
| no-op cold | 8 ms | 4 | 0 | no movement |
| no-op warm | 6 / 7 / 10 ms min/median/max | 20 total | 0 | five no-op runs |
| one movable NPC cold | 18 ms | 8 | 4 | one NPC moved |
| one movable NPC warm | 11 / 13 / 20 ms | 40 total | 20 total | one movement per invocation |
| four movable NPCs cold | 20 ms | 8 | 4 | all four moved |
| four movable NPCs warm | 13 / 16 / 28 ms | 40 total | 20 total | all four moved per invocation |
| five concurrent callers | 34 / 47 / 61 ms | 120 total | 20 total | five trials, no failures |

Concurrent trials increased each NPC version exactly once
(`versionDeltas=[4,4,4,4,4]`), produced no duplicate runtime rows, and reported
zero deadlocks. JDBC batching is not configured and is not claimed.

## DB Query Evidence

The checkpoint reads world/runtime/terrain/dialogue state in bounded queries.
The active-dialogue pin check is a single bulk lookup for all four NPC object
IDs, rather than one `exists` query per NPC. Terrain is read as a set rather
than queried tile by tile.

- no-op checkpoint: 4 SELECT, 0 UPDATE
- one-NPC movement checkpoint: 8 SELECT, 4 UPDATE
- four-NPC movement checkpoint: 8 SELECT, 4 UPDATE
- five concurrent callers: 24 SELECT and 4 UPDATE per trial
- canonical NPC projection: 1 SELECT, 0 writes
- warm complete chunk request: 50 SELECT, 0 writes; the existing chunk
  hydration/generation path accounts for the non-NPC reads
- dialogue start: 9 SELECT, 1 INSERT, 16 ms
- dialogue choice: 6 SELECT, 1 UPDATE, 17 ms
- dialogue completion: 7 SELECT, 1 UPDATE, 1 INSERT, 27 ms

The NPC projection count is constant for four NPCs and contains no per-NPC
world/object reload. Conversation state is not duplicated.

## 30-Second Runtime Evidence

A real Chromium session ran for 33.051 seconds against an isolated PostgreSQL
database with the production five-second checkpoint cadence. Player movement,
NPC updates, chunk preload/cache, and one completed dialogue were active
together.

- player movement requests: 77; maximum in-flight: 1
- processed checkpoint versions: 7 for each of NPC object IDs 133-136
- duplicate cadence executions: 0
- NPC transitions: 2 starts / 2 ends; maximum active: 2 overall and 1 per NPC
- player RAF: 21 starts / 21 stops
- dialogue completion count: 1
- chunk requests: maximum in-flight 1; final in-flight 0
- duplicate NPC DOM identities: 0
- stale NPC rollback: 0
- console/React/network issues: 0

The 30-second outer-region journey also passed with 101 movement requests,
maximum one in-flight request, and a 30.066-second measured duration.

## Logout and Relogin Evidence

The browser completed a dialogue, opened another session, logged out with that
session active, logged the same user in again, and re-entered the Village.
Logout sent an authenticated close request and received HTTP 204 even though
the application auth listener was also clearing the access token.

After cleanup, movement schedulers, RAF loops, chunk requests, reveal timers,
and keyboard handlers were all zero. The dialogue UI and chunk pin were
removed. Relogin restored backend NPC runtime positions, and the persisted
conversation count remained one without duplicate completion.

## Regression Evidence

- performance/schedule/canonical NPC targeted group: 11 tests, 0 failures,
  0 errors, 0 skipped
- Phase 4-A1 Playwright: 5 passed, including the 33-second active runtime and
  logout/relogin flow
- Phase 3-C journey: 2 passed
- full backend regression: 591 tests, 0 failures, 0 errors, 4 skipped
- backend package: `BUILD SUCCESS`
- full Village Playwright: 61 passed
- frontend lint: passed with zero warnings
- frontend build: passed
- both repository `git diff --check`: passed

The full Village suite used an isolated regression database and a long
checkpoint interval so fixed historical test routes remained deterministic.
The separate runtime-stability suite used the unchanged production default
five-second cadence.

## Known Limitations

- Movement uses validated Manhattan steps, not A* pathfinding.
- Initial schedules remain inside the HUB and do not react to weather/season.
- Dialogue effects only persist conversation history; quests, affinity,
  rewards, shops, and LLM dialogue are out of scope.
- The global scheduler currently scans worlds sequentially. An accumulated
  local development database with 654 worlds showed cadence drift; the
  isolated four-NPC production contract remained stable at five seconds.
- The complete warm chunk endpoint performs 50 SELECTs. NPC projection itself
  is one bulk SELECT, but the broader chunk hydration path remains a future
  optimization opportunity.
