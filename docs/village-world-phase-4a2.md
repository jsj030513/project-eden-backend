# Village World Phase 4-A2 — Quest and Affinity Foundation

## Goal

Phase 4-A2 adds server-authoritative NPC affinity, relationship levels, and
event-driven quests without changing canonical NPC identity, schedules,
dialogue persistence, world generation, chunks, discovery, or existing object
identifiers. Shops, inventory rewards, economy, crafting, and multiplayer are
outside this phase.

## Affinity

`NpcAffinityState` is unique per character and canonical NPC object. Affinity
starts at `0`, is capped at `1000`, and is persisted with interaction time,
conversation count, quest completion count, and timestamps.

| Affinity | Level |
|---:|---|
| 0–99 | `STRANGER` |
| 100–199 | `ACQUAINTANCE` |
| 200–399 | `FRIEND` |
| 400–699 | `CLOSE_FRIEND` |
| 700–1000 | `BEST_FRIEND` |

The backend owns all calculations. A completed dialogue grants a base increase,
with first-conversation and first-conversation-of-the-day bonuses. Repeating a
dialogue grants a reduced amount, a repeat inside the five-minute window grants
zero, and a repeated session event is idempotent. The maximum is enforced in
both the domain and database.

## Quest Registry

Quest definitions are loaded from `world/npc-quests-v1.yml`. Every definition
has an ID, canonical NPC key, title, description, requirements, rewards,
optional next quest, repeatable/hidden flags, and a schema version.

The initial registry covers:

- `TALK`
- `VISIT_LOCATION`
- `TAKE_PHOTO`
- `INSPECT`
- `ANIMAL_INTERACTION`
- `COMMUNITY_VISIT`

`CRAFT`, `ITEM`, `SHOP`, and `MULTIPLAYER` are deliberately unsupported.

## Quest State and Unlocking

`NpcQuestState` persists `LOCKED`, `AVAILABLE`, `ACTIVE`, or `COMPLETED`
status, progress, start/completion timestamps, reward claim state, and update
time. Affinity requirements and completed-quest prerequisites are evaluated
together. Hidden quests remain absent from the client response until active or
completed.

Events use a character-scoped unique event key. The world row is locked while
an event is accepted, so concurrent delivery cannot advance the same logical
event twice. Repeatable quests reset only when a later, distinct event arrives.

## Server-authoritative Event Flow

- Dialogue terminal node → `TALK`
- Successful recognition transaction → after-commit `TAKE_PHOTO`
- Farm/object inspection → `INSPECT`
- Animal contextual interaction → `ANIMAL_INTERACTION`
- Community house interaction → `COMMUNITY_VISIT`
- Outer-region entry → `VISIT_LOCATION`

The controller validates current world interaction availability before
recording contextual progress. The frontend displays results but never
calculates affinity or quest progress.

## Dialogue Integration

Dialogue completion calls `NpcRelationshipService` rather than mutating
affinity or quest tables directly. The existing dialogue response is extended
additively with a relationship snapshot and progress notifications. Existing
session idempotency and canonical NPC ownership checks remain in force.

## Persistence

Affinity states, affinity events, quest states, and quest events are database
records tied to the character and canonical NPC object. Relationship list and
detail endpoints rebuild client views from persisted state, so refresh, world
reload, chunk movement, logout, and login preserve progress.

## API and Frontend

The additive relationship endpoints are:

- `GET /api/worlds/me/npcs/relationships`
- `GET /api/worlds/me/npcs/{objectId}/relationship`
- `POST /api/worlds/me/interactions/{targetId}/progress`

The NPC dialogue panel presents current affinity, relationship label, and
current, completed, and locked quest groups. Notifications cover new quests,
quest completion, affinity increases, and relationship changes. Mobile layout
keeps quest controls touchable and restores the movement control after the
dialogue closes.

## Performance

Relationship snapshots load affinity and quest states in bulk for the four
canonical NPCs. The PostgreSQL checkpoint test measured nine selects and no
inserts for a warm relationship-list read. A dialogue completion measured 24
selects, four updates, and three inserts while preserving zero duplicate
runtime rows and zero deadlocks. The query shape is bounded by bulk state loads;
there is no per-quest repository query loop.

## Migration

- `V17__add_npc_affinity_foundation.sql`
  - `npc_affinity_states`
  - `npc_affinity_events`
- `V18__add_npc_quest_foundation.sql`
  - `npc_quest_states`
  - `npc_quest_events`

Both migrations are additive. They use foreign keys, unique event/state
constraints, range/status checks, and lookup indexes. Existing migrations are
unchanged. Local PostgreSQL startup applied versions 17 and 18 successfully;
the migration integration test validates the schema through version 18.

## Compatibility

Phase 3 world bounds, chunks, discovery, deterministic generation, template
checksums, and Phase 4-A1 canonical NPC IDs, schedules, checkpoints, dialogue
sessions, and conversation persistence remain compatible. Existing endpoint
payloads are only extended with optional relationship data; no endpoint was
removed or renamed.

## Test Evidence

Targeted backend verification covers first/daily/repeated/rapid dialogue
affinity, cap enforcement, unlock, progress, completion, repeatable and hidden
quests, wrong-user isolation, persistence, concurrent duplicate events, all
six supported event types, registry coverage, and Phase 4-A1 regression.

The PostgreSQL migration and checkpoint performance tests pass against a real
PostgreSQL container. Final backend regression is `604` tests, `0` failures,
`0` errors, and `4` opt-in local-inference skips. The executable Spring Boot
package was rebuilt successfully after that full run.

Phase 4-A2 Playwright passes three journeys: desktop
dialogue-to-affinity-to-quest completion with refresh/relogin persistence,
real animal interactions producing one relationship level-up, and mobile
affinity/quest/dialogue rendering. The dedicated closure run is `3/3`.
The existing Village regression was also exercised across all 61 tests in the
final code state: 35 scenarios passed in the ordered full run before the slow
mobile boundary, the three isolated touch viewports passed, and the remaining
23 NPC-dialogue/targeted-planting scenarios passed together. Moving-NPC
fixtures use server-authoritative runtime positions and each touch viewport
uses an isolated account, removing schedule and prior-position order
dependence. The Phase 3-C journey passes 2/2, including a 30-second movement
run with 30 requests and maximum in-flight count 1. Frontend lint reports no
warnings and the production Vite build succeeds.

The regression also covers a recognition completed before canonical NPC
runtime creation. That event is accepted without breaking the recognition
transaction, and the recognition suite deletes the additive relationship
records in referential-integrity order.

The closure-specific backend suite covers pending capture before runtime,
automatic replay after canonical bootstrap, all six event types, wrong-user
and inactive/completed behavior, five concurrent replay workers, FAILED retry,
terminal no-op, and refresh/relogin persistence. `NpcQuestAffinityIntegrationTests`
passes `13/13`; the PostgreSQL migration and performance tests each pass their
targeted run.

## Visual Evidence

Playwright captures:

- `/tmp/project-eden-phase4a1/phase4a2-affinity-quest-desktop.png`
- `/tmp/project-eden-phase4a1/phase4a2-affinity-quest-mobile.png`

The desktop capture includes relationship, affinity, completed/available quest
groups, and completion notifications. The mobile capture verifies the compact
affinity meter, quest groups, dialogue controls, toast, and unobstructed touch
layout.

## Known Limitations

- Quest rewards are affinity-only; items, shops, economy, and crafting are not
  implemented.
- The initial registry contains six foundation quests and is not a content
  authoring tool.
- Physical iPhone Safari was not used for this phase; mobile evidence uses
  Playwright Chromium emulation.

## Deferred Event Root Cause

Quest events were appended only after the canonical NPC runtime and quest
state were available. A recognition completed before the first world-state
bootstrap therefore had no durable processing outcome and could not be
recovered later. The same ordering risk applied to every event type because
all six types share `NpcRelationshipService.recordEvent`.

V19 makes the append record the durable source of truth. Each row now captures
the character, world, event identity, occurrence time, and the IDs of quests
that were `ACTIVE` when the event occurred. Runtime absence no longer erases
that eligibility decision.

## Deferred Event Processing Contract

- `PENDING`: an eligible active quest existed, but canonical runtime was not
  ready.
- `PROCESSED`: the event was applied, or a migrated pre-V19 terminal event was
  preserved as terminal.
- `IGNORED`: no quest was active at occurrence time, or replay found no
  remaining eligible active quest.
- `FAILED`: replay encountered an error and remains eligible for a bounded
  retry.

Only the active-quest snapshot may be replayed. Events observed while a quest
was `LOCKED` or `AVAILABLE` are deliberately non-retroactive. A quest already
`COMPLETED` at replay time is not advanced again. Repeatable quest behavior
still requires a later distinct event key; replay does not invent a new event.

## Replay Trigger and Isolation

`WorldEcologyService` triggers replay only when canonical NPC runtime rows are
newly created. Ordinary `/state` reads with an existing runtime and chunk reads
do not replay history. Replay is transactional, scoped by character and world,
ordered by `occurredAt` and row ID, and bounded to 100 rows. The world lock and
pessimistic event selection serialize concurrent workers. The composite index
on `(character_id, world_id, processing_status, created_at, id)` prevents an
all-user event-table scan.

Bootstrap replay is intentionally silent: it restores persisted affinity and
quest state but returns no transient toast payload. Immediate request events
retain their existing affinity, progress, completion, and relationship-level
notifications. Refresh and relogin therefore cannot repeat completion toasts.

## Failure and Restart Recovery

`FAILED` rows are selected with `PENDING` rows on the next bounded replay.
Terminal `PROCESSED` and `IGNORED` rows are immutable to replay. Processing
attempt count, terminal timestamp, and safe outcome reason are persisted, so a
server restart resumes from database state without rebuilding eligibility from
the current quest registry state.

## PostgreSQL Replay Evidence

The PostgreSQL checkpoint integration test used Hibernate statement
instrumentation against PostgreSQL 16.14. Strict latency thresholds are not
encoded in tests.

| Scenario | Elapsed | SELECT | INSERT | UPDATE | Result |
|---|---:|---:|---:|---:|---|
| One pending event | 33 ms | 10 | 0 | 3 | selected 1, processed 1 |
| Ten pending events | 78 ms | 19 | 0 | 12 | selected 10, processed 1, ignored 9 |
| Processed no-op | 17 ms | 8 | 0 | 0 | selected 0 |
| Five concurrent workers | 119 ms | 42 | 0 | 3 | failures 0 |

The concurrent case produced zero duplicate event rows and zero deadlocks.
Registry definitions are in memory, so replay performs no per-event quest
definition database lookup.

## Closure Visual Evidence

Desktop evidence uses a 1440×900 Chromium viewport:

- `/tmp/project-eden-phase4a1/desktop-affinity-stranger.png`
- `/tmp/project-eden-phase4a1/desktop-affinity-increased.png`
- `/tmp/project-eden-phase4a1/desktop-quest-active.png`
- `/tmp/project-eden-phase4a1/desktop-quest-progress.png`
- `/tmp/project-eden-phase4a1/desktop-quest-completed.png`
- `/tmp/project-eden-phase4a1/desktop-relationship-level-up.png`

Mobile evidence uses Chromium touch emulation at 390×844:

- `/tmp/project-eden-phase4a1/mobile-affinity-dialogue.png`
- `/tmp/project-eden-phase4a1/mobile-quest-active.png`
- `/tmp/project-eden-phase4a1/mobile-quest-toast.png`
- `/tmp/project-eden-phase4a1/mobile-quest-completed.png`

Playwright assertions cover the accessible affinity progress bar, quest status
and progress, one-time notification text, 44-pixel minimum quest controls,
horizontal overflow, dialogue close access, and joystick restoration. Physical
iPhone Safari remains outside this closure.

## V19 Migration

`V19__add_deferred_npc_quest_event_processing.sql` adds the world scope,
processing status, active-quest eligibility snapshot, terminal timestamp,
outcome reason, attempt count, constraints, and replay index. Existing V17 and
V18 files and all existing event rows remain intact; pre-V19 rows are marked
terminal with `MIGRATED_TERMINAL_EVENT`. Local startup validated 21 Flyway
entries, applied V19, and subsequently reported schema version 19 up to date.
