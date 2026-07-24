# Sprint 10 — Extensible Memory Taxonomy

## 1. Audit Scope

[Confirmed] This audit inspected the backend repository only. Host-specific absolute paths are intentionally omitted.

[Confirmed] Included scope:

- `VillageCategory`
- `RecognizedObject`
- Recognition flow
- Village Memory
- Village Interpretation
- Theme
- Village Change
- NPC Memory and Dialogue
- API DTOs
- JPA enum persistence and DB configuration
- Tests coupled to categories, recognized objects, UNKNOWN fallback, STUDY, and WORK
- Collection, Achievement, Statistics, Resonance, and Evolution touchpoints

[Confirmed] Excluded scope:

- Actual multimodal AI implementation
- Frontend changes
- New taxonomy entity implementation
- DB migration implementation
- Controller, service, DTO, entity, repository, or test modification

[Confirmed] Commands used included `find src -type f | sort`, `rg -n "VillageCategory" src`, `rg -n "RecognizedObject" src`, `rg -n "@Enumerated|EnumType\\.STRING|EnumType\\.ORDINAL" src`, switch/fallback keyword searches, resource/config searches, and endpoint/DTO searches.

## 2. Current Memory Flow

[Confirmed] Current primary flow:

```text
PhotoController.uploadPhoto(...)
→ PhotoService.uploadPhoto(userId, plantId, file)
→ RecognitionController.recognizePhoto(user, photoId)
→ RecognitionApplicationService.recognizePhoto(userId, photoId)
→ RecognitionService.recognize(Photo)
→ MockRecognitionService.recognize(Photo)
→ RecognitionResult
→ Recognition Entity
→ EvolutionService.addEvolutionPoint(..., RECOGNITION) when recognized=true
→ VillageService.recordVillageMemory(characterId, RecognizedObject)
→ VillageMemory
→ VillageInterpretationService.interpretAndUpdateTheme(characterId)
→ VillageThemeSnapshot
→ VillageChange / VillageHistory
→ NpcContextProvider.buildContext(characterId, npcId)
→ NpcDialogueRule.selectDialogue(NpcContext)
→ NpcMemoryService.recordInteraction(...)
```

[Confirmed] `PhotoController.uploadPhoto` accepts `file` and optional `plantId`, returning `PhotoUploadResponse(photoId, plantId, imageUrl, uploadedAt)`.

[Confirmed] `RecognitionApplicationService.createRecognition` stores a single `RecognizedObject`, then exposes `RecognitionResponse(id, photoId, recognizedObject, category, confidence, recognized, fallback)`.

[Confirmed] `MockRecognitionService` is deterministic and uses `Photo.originalFileName` keyword rules, not image content.

[Confirmed] `RecognitionResult.recognized(...)` derives `VillageCategory` through `RecognizedObject.getCategory()`.

[Confirmed] `RecognitionResult.unknown()` returns `RecognizedObject.UNKNOWN`, `VillageCategory.UNKNOWN`, confidence `0`, `recognized=false`, and `fallback=true`.

[Confirmed] `RecognitionApplicationService.createRecognition` calls `VillageService.recordVillageMemory(...)` for any non-null recognized object, including UNKNOWN.

[Confirmed] `EvolutionService.addEvolutionPoint(..., RECOGNITION)` is called only when `Recognition.isRecognized()` is true.

[Confirmed] `VillageService.recordVillageMemory` converts `RecognizedObject` to `VillageCategory`, updates one `VillageMemory` row per `(character_id, category)`, records history, updates interpretation, and creates changes from category rules.

[Confirmed] `VillageInterpretationService` sorts memory rows by count and priority, then maps the primary category to one `VillageTheme`.

[Confirmed] `NpcContextProvider` reads the current `VillageThemeSnapshot`, latest `VillageHistory`, and existing `NpcMemory` to build `NpcContext`.

[Confirmed] `NpcDialogueRule` uses current theme and recent history to select one dialogue key/message and one remembered category.

## 3. VillageCategory Usage Map

| Layer | File | Class / Method | Usage | Dependency Type | Risk |
|---|---|---|---|---|---|
| Domain | `src/main/java/com/projecteden/village/domain/VillageCategory.java` | `VillageCategory` | Central enum values: `FOOD`, `NATURE`, `WALK`, `WATER`, `ANIMAL`, `STUDY`, `WORK`, `UNKNOWN` | Direct | High |
| Domain | `src/main/java/com/projecteden/ai/domain/RecognizedObject.java` | enum constants | Each recognized object embeds one `VillageCategory` | Mapping | High |
| DTO | `src/main/java/com/projecteden/ai/dto/RecognitionResult.java` | record field `category` | Internal recognition result exposes one category | Direct | High |
| DTO | `src/main/java/com/projecteden/ai/dto/RecognitionResponse.java` | record field `category` | API exposes one category | API Contract | High |
| Domain | `src/main/java/com/projecteden/village/domain/VillageMemory.java` | field `category` | Persisted enum; unique with character | Persistence | Critical |
| Repository | `src/main/java/com/projecteden/village/repository/VillageMemoryRepository.java` | `findByCharacterIdAndCategory` | Query assumes one category key | Persistence | High |
| Domain | `src/main/java/com/projecteden/village/domain/VillageChange.java` | field `category` | Persisted category for world change | Persistence / World Expression | High |
| Domain | `src/main/java/com/projecteden/village/domain/VillageHistory.java` | field `category` | Optional persisted category in history | Persistence | Medium |
| Domain | `src/main/java/com/projecteden/village/domain/VillageThemeSnapshot.java` | fields `primaryCategory`, `secondaryCategory` | Stores one primary and one secondary category | Persistence | High |
| DTO | `src/main/java/com/projecteden/village/dto/VillageMemoryResponse.java` | field `category` | API exposes memory category | API Contract | Medium |
| DTO | `src/main/java/com/projecteden/village/dto/VillageChangeResponse.java` | field `category` | API exposes change category | API Contract | Medium |
| DTO | `src/main/java/com/projecteden/village/dto/VillageHistoryResponse.java` | field `category` | API exposes history category | API Contract | Medium |
| DTO | `src/main/java/com/projecteden/village/dto/VillageResponse.java` | field `dominantCategory`, memories | API exposes dominant single category | API Contract | High |
| DTO | `src/main/java/com/projecteden/village/dto/VillageInterpretationResponse.java` | fields `primaryCategory`, `secondaryCategory` | API exposes two enum slots | API Contract | High |
| Service | `src/main/java/com/projecteden/village/service/VillageService.java` | `recordVillageMemory`, `categoryFor`, `rulesFor`, `reactionFor`, `village` | Mapping, switch rules, dominant category by enum ordinal | Mapping / World Expression | Critical |
| Service | `src/main/java/com/projecteden/village/service/VillageInterpretationService.java` | `interpret`, `themeFor`, `priority` | Primary category drives theme and priority | World Expression | Critical |
| Domain | `src/main/java/com/projecteden/npcmemory/domain/NpcMemory.java` | field `rememberedCategory` | Persisted single remembered category | Persistence / NPC Context | High |
| DTO | `src/main/java/com/projecteden/npcmemory/dto/NpcMemoryResponse.java` | field `rememberedCategory` | DTO exposes one remembered category | API Contract | Medium |
| DTO | `src/main/java/com/projecteden/npcmemory/dto/NpcDialogueResponse.java` | field `rememberedCategory` | NPC dialogue API exposes one remembered category | API Contract / NPC Context | High |
| Context | `src/main/java/com/projecteden/npcmemory/context/NpcContext.java` | fields `primaryCategory`, `secondaryCategory`, `recentHistoryCategory`, `rememberedCategory` | NPC context assumes single-slot categories | NPC Context | High |
| Component | `src/main/java/com/projecteden/npcmemory/context/NpcContextProvider.java` | `buildContext` | Pulls one primary, one secondary, one latest history category, one remembered category | NPC Context | High |
| Rule | `src/main/java/com/projecteden/npcmemory/dialogue/NpcDialogueRule.java` | `rememberedCategory` | Chooses one category priority: recent → primary → remembered | NPC Context | High |
| Result | `src/main/java/com/projecteden/npcmemory/dialogue/NpcDialogueResult.java` | field `rememberedCategory` | Dialogue result carries one category | NPC Context | Medium |
| Service | `src/main/java/com/projecteden/npcmemory/service/NpcMemoryService.java` | `recordInteraction` | Stores one current category | NPC Context / Persistence | High |
| Tests | `src/test/java/com/projecteden/village/VillageIntegrationTests.java` | multiple tests | Verifies category-specific memory/change behavior, including STUDY/WORK/UNKNOWN | Test Contract | Medium |
| Tests | `src/test/java/com/projecteden/village/VillageInterpretationIntegrationTests.java` | multiple tests | Verifies category-to-theme and priority behavior | Test Contract | High |
| Tests | `src/test/java/com/projecteden/npcmemory/*` | service, context, dialogue tests | Verifies NPC category slots and dialogue remembered category | Test Contract | Medium |

[Confirmed] `rg -l "VillageCategory" src` returned 30 files.

## 4. RecognizedObject Usage Map

| Layer | File | Class / Method | Usage | Dependency Type | Risk |
|---|---|---|---|---|---|
| Domain | `src/main/java/com/projecteden/ai/domain/RecognizedObject.java` | enum | Observation label and Eden category mapping live together | Direct / Mapping | Critical |
| Domain | `src/main/java/com/projecteden/ai/domain/Recognition.java` | field `recognizedObject` | Persisted single recognized object | Persistence | Critical |
| DTO | `src/main/java/com/projecteden/ai/dto/RecognitionResult.java` | record field `recognizedObject` | Provider result carries one object | Direct | High |
| DTO | `src/main/java/com/projecteden/ai/dto/RecognitionResponse.java` | field `recognizedObject` | API exposes one object | API Contract | High |
| Service | `src/main/java/com/projecteden/ai/service/MockRecognitionService.java` | `KEYWORD_RULES`, `confidenceFor` | Filename keyword maps to one object | Observation / Test Provider | High |
| Service | `src/main/java/com/projecteden/ai/service/RecognitionApplicationService.java` | `createRecognition`, `toResponse` | Stores object, sends to Evolution and Village, exposes category | Direct / World Expression | Critical |
| Service | `src/main/java/com/projecteden/village/service/VillageService.java` | `recordVillageMemory`, `categoryFor` | Recognized object directly becomes village category | Mapping / World Expression | Critical |
| Domain | `src/main/java/com/projecteden/resonance/domain/Resonance.java` | field `recognizedObject` | Persisted recognized object per resonance | Persistence | High |
| Repository | `src/main/java/com/projecteden/resonance/repository/ResonanceRepository.java` | `existsByCharacterIdAndRecognizedObjectAndResonanceDate` | Daily uniqueness by object | Persistence / Business Rule | High |
| DTO | `src/main/java/com/projecteden/resonance/dto/ResonanceResponse.java` | field `recognizedObject` | API exposes object | API Contract | Medium |
| Service | `src/main/java/com/projecteden/resonance/service/ResonanceService.java` | reward calculation and limits | Object controls reward and daily duplicate rule | Direct | Critical |
| Domain | `src/main/java/com/projecteden/collection/domain/Collection.java` | field `recognizedObject` | Persisted collection key | Persistence | Critical |
| Repository | `src/main/java/com/projecteden/collection/repository/CollectionRepository.java` | queries by object | Collection uniqueness by object | Persistence | High |
| DTO | `src/main/java/com/projecteden/collection/dto/CollectionResponse.java` | field `recognizedObject` | API exposes object | API Contract | Medium |
| Service | `src/main/java/com/projecteden/collection/service/CollectionService.java` | `registerDiscovery`, `getMyCollectionSummary`, `rarity` | Object controls collection, total count, rarity | Statistics / Test Contract | High |
| Tests | `src/test/java/com/projecteden/ai/RecognitionIntegrationTests.java` | mock recognition tests | Fixed filename→object/category expectations | Test Contract | High |
| Tests | `src/test/java/com/projecteden/resonance/ResonanceIntegrationTests.java` | reward/limit tests | Fixed FLOWER/UNKNOWN behavior | Test Contract | Medium |
| Tests | `src/test/java/com/projecteden/collection/CollectionAchievementIntegrationTests.java` | collection tests | Uses `RecognizedObject.values().length` and object-specific rarity | Test Contract | High |
| Tests | `src/test/java/com/projecteden/village/*` | interpretation tests | Records object to drive category/theme | Test Contract | Medium |

[Confirmed] `rg -l "RecognizedObject" src` returned 19 files.

[Confirmed] Observation role: `MockRecognitionService` uses file observations as keywords and returns `RecognizedObject`.

[Confirmed] Eden Classification role: `RecognizedObject` embeds `VillageCategory`.

[Confirmed] World Expression role: `RecognitionApplicationService` sends `RecognizedObject` to `VillageService`, and `VillageService` resolves category into changes/reactions/theme updates.

[Confirmed] Current `RecognizedObject` therefore mixes AI observation, Eden classification, collection identity, reward identity, and world-expression input.

## 5. Enum and Switch Inventory

| Enum / Switch | File | Values or Cases | Purpose | Addition Cost | Risk |
|---|---|---|---|---|---|
| `VillageCategory` enum | `VillageCategory.java` | `FOOD`, `NATURE`, `WALK`, `WATER`, `ANIMAL`, `STUDY`, `WORK`, `UNKNOWN` | Internal memory category | Requires switch and test updates | High |
| `RecognizedObject` enum | `RecognizedObject.java` | Object labels mapped to one category | Recognition object and classification mapping | Requires mock rules, tests, collection/resonance awareness | Critical |
| `VillageTheme` enum | `VillageTheme.java` | `BLOOMING_VILLAGE`, `WARM_VILLAGE`, `WALKING_VILLAGE`, `WATERSIDE_VILLAGE`, `ANIMAL_FRIENDLY_VILLAGE`, `QUIET_VILLAGE`, `UNDEFINED` | Current village atmosphere | Requires interpretation, expressions, NPC dialogue tests | High |
| `VillageChangeType` enum | `VillageChangeType.java` | Table/garden/path/water/animal/quiet objects | World change object | Requires rule mapping and UI compatibility | High |
| `VillageInterpretationService.themeFor` | `VillageInterpretationService.java` | all `VillageCategory` cases, no default | Category→Theme | New category must be handled at compile time | Critical |
| `VillageInterpretationService.priority` | `VillageInterpretationService.java` | all `VillageCategory` cases, no default | Tie-break priority | New category must be handled at compile time | High |
| `VillageInterpretationService.messageFor` | `VillageInterpretationService.java` | all `VillageTheme` cases, no default | Theme message | New theme must be handled at compile time | Medium |
| `VillageInterpretationService.expressionsFor` | `VillageInterpretationService.java` | all `VillageTheme` cases, no default | Theme expressions | New theme must be handled at compile time | Medium |
| `VillageService.rulesFor` | `VillageService.java` | all `VillageCategory` cases, no default | Category→VillageChange rules | New category must be handled at compile time | Critical |
| `VillageService.reactionFor` | `VillageService.java` | all `VillageCategory` cases, no default | Category→NPC reaction history message | New category must be handled at compile time | High |
| `NpcDialogueRule.selectDialogue` | `NpcDialogueRule.java` | all `VillageTheme` cases, no default | Theme→dialogue | New theme must be handled at compile time | High |
| `ResonanceService.calculateReward` | `ResonanceService.java` | `UNKNOWN` if, seed object allow-list | Object→reward | New seed reward object requires manual update | Critical |
| `CollectionService.rarity` | `CollectionService.java` | `UNKNOWN` ternary only | Object→rarity | New rarity rules require manual update | Medium |
| `CollectionService.getMyCollectionSummary` | `CollectionService.java` | `RecognizedObject.values().length` | Total collectable count | Enum additions change totals automatically | Medium |
| `EvolutionService.pointFor` | `EvolutionService.java` | source type switch, not category | Source→point | Not category-dependent | Low |

[Confirmed] Adding one new `VillageCategory` currently requires at least these production files to be considered: `RecognizedObject.java`, `VillageInterpretationService.java`, `VillageService.java`, plus relevant tests and README. If exposed to NPC behavior or API examples, NPC tests and docs also need review.

[Confirmed] Adding one new `RecognizedObject` currently requires at least these production files to be considered: `RecognizedObject.java`, `MockRecognitionService.java` keyword rules, `ResonanceService` if seed reward is expected, collection/statistics implications, and tests.

[Confirmed] The most likely omission points are `VillageService.rulesFor`, `VillageService.reactionFor`, `VillageInterpretationService.themeFor`, `VillageInterpretationService.priority`, and `ResonanceService.isSeedRewardObject`.

[Confirmed] Category/theme switch expressions do not use default branches, so Java compiler detects missing enum cases.

[Confirmed] `ResonanceService.isSeedRewardObject` is not a switch; compiler will not detect reward omission for a new object.

[Confirmed] UNKNOWN fallback exists in `RecognitionResult.unknown`, `VillageService.rulesFor(UNKNOWN)`, `VillageInterpretationService.themeFor(UNKNOWN)`, `CollectionService.rarity(UNKNOWN)`, and `ResonanceService.calculateReward(UNKNOWN)`.

[Inferred] UNKNOWN fallback is safe for current single-category flow, but not sufficient for multi-category observation because it collapses unclassified information into one bucket.

## 6. Persistence and Database Constraints

| Table / Entity | Column / Field | Storage Type | Constraint | Migration Location | Risk |
|---|---|---|---|---|---|
| `recognitions` / `Recognition` | `recognizedObject` | `EnumType.STRING`, inferred varchar | `nullable=false`; `photo_id` unique | No migration found; Hibernate DDL | Critical |
| `village_memories` / `VillageMemory` | `category` | `EnumType.STRING`, inferred varchar | `nullable=false`; unique `(character_id, category)` | No migration found; Hibernate DDL | Critical |
| `village_changes` / `VillageChange` | `category` | `EnumType.STRING`, inferred varchar | `nullable=false`; unique `(character_id, change_type)` | No migration found; Hibernate DDL | High |
| `village_changes` / `VillageChange` | `change_type` | `EnumType.STRING`, inferred varchar | `nullable=false` | No migration found; Hibernate DDL | Medium |
| `village_histories` / `VillageHistory` | `category` | `EnumType.STRING`, inferred varchar | nullable | No migration found; Hibernate DDL | Medium |
| `village_histories` / `VillageHistory` | `historyType`, `changeType` | `EnumType.STRING`, inferred varchar | `historyType nullable=false`, `changeType nullable` | No migration found; Hibernate DDL | Low |
| `village_theme_snapshots` / `VillageThemeSnapshot` | `theme` | `EnumType.STRING`, inferred varchar | `nullable=false` | No migration found; Hibernate DDL | High |
| `village_theme_snapshots` / `VillageThemeSnapshot` | `primaryCategory`, `secondaryCategory` | `EnumType.STRING`, inferred varchar | nullable | No migration found; Hibernate DDL | High |
| `npc_memories` / `NpcMemory` | `rememberedTheme` | `EnumType.STRING`, inferred varchar | nullable; unique `(character_id, npc_id)` | No migration found; Hibernate DDL | Medium |
| `npc_memories` / `NpcMemory` | `rememberedCategory` | `EnumType.STRING`, inferred varchar | nullable | No migration found; Hibernate DDL | High |
| `resonances` / `Resonance` | `recognized_object` | `EnumType.STRING`, inferred varchar | `nullable=false`; `recognition_id` unique | No migration found; Hibernate DDL | High |
| `resonances` / `Resonance` | `rewardType`, `rewardSeedType` | `EnumType.STRING`, inferred varchar | `rewardType nullable=false`, seed nullable | No migration found; Hibernate DDL | Medium |
| `collections` / `Collection` | `recognized_object` | `EnumType.STRING`, inferred varchar | `nullable=false`; unique `(character_id, recognized_object)` | No migration found; Hibernate DDL | Critical |

[Confirmed] No `src/main/resources/db`, `migration`, `schema.sql`, or `data.sql` file was found in the searched resource paths.

[Confirmed] `src/main/resources/application-local.yml` and `application-docker.yml` use PostgreSQL driver and `spring.jpa.hibernate.ddl-auto=update`.

[Confirmed] `src/test/resources/application-test.yml` uses H2 in PostgreSQL mode with `ddl-auto=create-drop`.

[Confirmed] No explicit `CHECK`, enum type, or hard-coded enum constraint was found in `src/main/resources`, `src/test/resources`, or `docker-compose.yml`.

[Unknown] The exact PostgreSQL column lengths generated by Hibernate are not declared in the Java annotations and were not verified against a running database in this audit.

[Unknown] Whether an existing deployed PostgreSQL database contains older generated constraints or manual changes cannot be confirmed from the repository alone.

## 7. Single-Category Assumptions

| File | Class / Method | Assumption | Future Problem | Risk |
|---|---|---|---|---|
| `Recognition.java` | field `recognizedObject` | One photo has one recognized object | Cannot store multiple observed subjects/objects | Critical |
| `RecognitionResult.java` | record fields | One result has one recognized object and one category | Cannot represent primary/secondary/tags | Critical |
| `RecognitionResponse.java` | API DTO | Client receives one category | API migration requires additive fields or compatibility layer | High |
| `RecognizedObject.java` | `getCategory()` | Each object maps to exactly one category | One object cannot belong to multiple taxonomy dimensions | High |
| `VillageMemory.java` | unique `(character_id, category)` | Memory aggregates by one category | Multi-category memories need separate association/event model | Critical |
| `VillageService.recordVillageMemory` | method input | Memory is recorded from one recognized object | Photo cannot contribute ANIMAL + WALK + PEOPLE at once | Critical |
| `VillageService.village` | dominant category | Dominance is one category sorted by count and enum ordinal | Multi-category scoring/mood/tag weighting impossible | High |
| `VillageThemeSnapshot.java` | `primaryCategory`, `secondaryCategory` | Only two category slots | Rich classification/tags cannot be stored | High |
| `VillageInterpretationService.interpret` | first two sorted memories | Primary/secondary are derived from aggregate counts only | Recent/contextual memories cannot influence theme richly | High |
| `VillageChange.java` | `category` | Each change has one source category | A change cannot be attributed to blended meaning | Medium |
| `VillageHistory.java` | `category` | Each history has one category | Multi-category history loses context | Medium |
| `NpcContext.java` | category fields | NPC context has single-slot categories | NPC cannot refer to multiple dimensions from the same memory | High |
| `NpcMemory.java` | `rememberedCategory` | NPC remembers one category | NPC memory cannot store tags or multi-category relationship | High |
| `Collection.java` | `recognizedObject` | Collection key is one recognized object | Multi-object photo requires multiple collection events or new discovery model | Critical |
| `Resonance.java` | `recognizedObject` | Resonance is one object | Multi-object recognition needs reward target selection | High |
| `ResonanceRepository` | same object/day limit | Daily duplicate limit keyed by one recognized object | Multi-tag/object reward limits become ambiguous | High |
| `StatisticsService` | collection counts | Statistics are based on collection object counts | Taxonomy/category stats unavailable | Medium |

## 8. World Expression Coupling

| Source | Target | File / Method | Coupling Type | Risk |
|---|---|---|---|---|
| `Photo.originalFileName` | `RecognizedObject` | `MockRecognitionService.recognize` | Observation provider returns Eden object directly | Critical |
| `RecognizedObject` | `VillageCategory` | `RecognizedObject.getCategory` | Observation label embeds Eden classification | Critical |
| `Recognition` | `EvolutionPoint(RECOGNITION)` | `RecognitionApplicationService.createRecognition` | Recognition success directly grants EP | High |
| `Recognition` | `VillageMemory` | `RecognitionApplicationService.createRecognition` | Recognition directly records village memory | Critical |
| `RecognizedObject` | `VillageCategory` | `VillageService.categoryFor` | Direct mapping | Critical |
| `VillageCategory` | `VillageMemory` | `VillageService.recordVillageMemory` | Persisted aggregate by category | Critical |
| `VillageCategory` | `VillageTheme` | `VillageInterpretationService.themeFor` | World theme from category | Critical |
| `VillageCategory` | `VillageChangeType` | `VillageService.rulesFor` | Change rules from category | Critical |
| `VillageCategory` | NPC reaction history | `VillageService.reactionFor` | Category-specific message | High |
| `VillageTheme` | NPC dialogue | `NpcDialogueRule.selectDialogue` | Theme-specific dialogue key/message | High |
| `VillageCategory` | NPC remembered category | `NpcDialogueRule.rememberedCategory` and `NpcMemoryService.recordInteraction` | Single category stored in NPC memory | High |
| `RecognizedObject` | Resonance reward | `ResonanceService.calculateReward` | Object-specific seed/gold reward | Critical |
| `RecognizedObject` | Collection | `CollectionService.registerDiscovery` | Object-specific collection entry | Critical |
| Collection counts | Statistics | `StatisticsService.refreshStatistics` | Statistics depend on object collection model | Medium |
| Recognition/Resonance/Achievement/Cheer | Evolution point | `EvolutionService.pointFor` | Source-based, not category-based | Low |

[Confirmed] AI Observation and World Expression are currently coupled through `RecognizedObject`: the provider returns it, it maps to category, and the category drives theme/change/NPC.

## 9. API Compatibility Surface

| Endpoint | Response DTO | Relevant Fields | Compatibility Risk |
|---|---|---|---|
| `POST /api/photos` | `PhotoUploadResponse` | `photoId`, `plantId`, `imageUrl`, `uploadedAt` | Low; no category/object |
| `GET /api/photos/me` | `List<PhotoResponse>` | `photoId`, `plantId`, `imageUrl`, `uploadedAt` | Low; no category/object |
| `POST /api/photos/{photoId}/recognize` | `RecognitionResponse` | `recognizedObject`, `category`, `confidence`, `recognized`, `fallback` | Critical; single object/category API |
| `GET /api/photos/recognitions` | `List<RecognitionResponse>` | same as above | Critical |
| `GET /api/village/me` | `VillageResponse` | `dominantCategory`, `totalMemoryCount`, `memories`, `changes`, `latestMessage` | High; dominant single category |
| `GET /api/village/history` | `List<VillageHistoryResponse>` | `historyType`, `category`, `changeType`, `message`, `createdAt` | Medium |
| `GET /api/village/changes` | `List<VillageChangeResponse>` | `category`, `changeType`, `appeared`, `appearedAt`, `message` | High; change has one category |
| `GET /api/village/interpretation` | `VillageInterpretationResponse` | `theme`, `primaryCategory`, `secondaryCategory`, `message`, `expressions`, `ruleVersion` | High; only two category slots |
| `GET /api/npcs/{npcId}/dialogue` | `NpcDialogueResponse` | `dialogueKey`, `message`, `currentTheme`, `rememberedCategory`, `memoryChanged` | High; single remembered category |
| `GET /api/statistics/me` | `StatisticsResponse` | totals only, no category/object | Medium; indirectly based on collection model |
| `GET /api/collections/me` | `CollectionSummaryResponse` / `CollectionResponse` | `totalCollectableCount`, `completionRate`, `recognizedObject`, `rarity` | High; object enum exposed |
| `POST /api/resonances` | `ResonanceResponse` | `recognizedObject`, `rewardType`, `rewardSeedType`, `rewardGold`, `message` | High; object enum exposed |
| `GET /api/resonances/me` | `List<ResonanceResponse>` | same as above | High |
| `GET /api/evolution/me` | `WorldEvolutionResponse` | `worldLevel`, `evolutionPoint`, `worldStage` | Low; no category/object |
| `GET /api/evolution/history` | `List<EvolutionHistoryResponse>` | `eventType`, `description`, `createdAt` | Low |
| `GET /api/evolution/decorations` | `List<WorldDecorationResponse>` | `decorationType`, `unlocked`, `unlockedAt` | Low |

[Confirmed] The most compatibility-sensitive DTO is `RecognitionResponse`, because clients see both `recognizedObject` and single `category`.

## 10. Test Coupling

| Test File | Test Name | Coupling | Expected Migration Impact |
|---|---|---|---|
| `RecognitionIntegrationTests` | `mockRecognitionMapsCatPhotoToAnimal` and related filename tests | Filename→single object/category | High; provider abstraction changes will need compatibility fixtures |
| `RecognitionIntegrationTests` | `unknownPhotoReturnsFallbackAndRecordsUnknownVillageMemory` | UNKNOWN fallback fields and VillageMemory side effect | Medium |
| `RecognitionIntegrationTests` | `mockRecognitionMapsStudyBookPhotoToStudy`, `mockRecognitionMapsCodingLaptopPhotoToWork` | STUDY/WORK exact mappings | Medium |
| `RecognitionIntegrationTests` | `myRecognitionsCanBeRetrieved` | JSON field `recognizedObject`, `category` | High |
| `VillageIntegrationTests` | `flowerRecognitionCreatesNatureMemory` | Recognition drives VillageMemory category | High |
| `VillageIntegrationTests` | `studyObjectCreatesStudyMemory`, `workObjectCreatesWorkMemory` | STUDY/WORK category storage | Medium |
| `VillageIntegrationTests` | `firstUnknownMemoryCreatesQuietPlace` | UNKNOWN→QUIET_PLACE | Medium |
| `VillageIntegrationTests` | `villageApiSucceeds` | JSON `dominantCategory=NATURE`, latest message text | Medium |
| `VillageInterpretationIntegrationTests` | `natureMemoryUsesBloomingTheme`, `foodMemoryUsesWarmTheme`, `studyMemoryUsesQuietTheme`, `workMemoryUsesWarmTheme`, `unknownMemoryUsesQuietTheme` | Category→Theme mapping | High |
| `VillageInterpretationIntegrationTests` | `tieUsesNatureBeforeFood` | Priority/ordering | Medium |
| `NpcContextProviderTests` | `context...` assertions | Current/primary/secondary/recent/remembered single categories | High |
| `NpcDialogueRuleTests` | theme and remembered category tests | Theme switch and remembered category priority | Medium |
| `NpcDialogueIntegrationTests` | dialogue JSON and memory assertions | Dialogue response category/theme fields | Medium |
| `NpcMemoryServiceTests` | `recordInteraction...` | One remembered category is stored | Medium |
| `CollectionAchievementIntegrationTests` | `collectionSummarySucceeds` | `RecognizedObject.values().length` total | High |
| `CollectionAchievementIntegrationTests` | `unknownIsCollectedAsUncommon` | UNKNOWN rarity | Low |
| `ResonanceIntegrationTests` | `FLOWER Recognition...`, `UNKNOWN...` | Object→reward and duplicate object/day behavior | High |
| `EvolutionIntegrationTests` | recognition/resonance point flows | Source-based evolution | Low |

[Confirmed] Several tests assert JSON enum string values directly.

[Confirmed] At least one test computes expected collection total from `RecognizedObject.values().length`; enum additions change API expected totals automatically.

## 11. Risk Matrix

| Risk | Area | Current Problem | Failure Scenario | Recommended Mitigation |
|---|---|---|---|---|
| Critical | Recognition persistence | `Recognition` stores one `RecognizedObject` | Multi-object recognition cannot be represented without API/entity changes | [Proposed] Add independent Observation and Classification tables while preserving legacy field |
| Critical | Village memory persistence | `VillageMemory` unique `(character_id, category)` | One photo with multiple categories cannot record source-level detail | [Proposed] Add `MemoryClassification` with primary/secondary/tags; keep aggregate `VillageMemory` as legacy projection |
| Critical | Object/category coupling | `RecognizedObject.getCategory()` embeds taxonomy | AI observation changes force world taxonomy changes | [Proposed] Move mapping into `MemoryClassificationService` |
| Critical | World expression coupling | `VillageCategory` directly maps to theme/change/reaction | Adding categories requires multiple switch edits and can mis-express new meanings | [Proposed] Introduce `WorldExpressionResolver` |
| Critical | Resonance reward | Reward keyed by `RecognizedObject` allow-list | New object may incorrectly get weak gold or crash if seed mapping expected | [Proposed] Add reward policy table/config independent from raw observation |
| Critical | Collection key | Collection unique by `recognized_object` | Multi-object photo needs multiple discoveries but no observation grouping | [Proposed] Introduce discovery/classification events and keep collection as derived projection |
| High | API compatibility | `RecognitionResponse` exposes one object/category | Frontend/client breakage if fields are replaced | [Proposed] Add new fields additively; keep legacy fields |
| High | NPC memory | NPC stores one remembered category | NPC cannot remember tags/context/mood | [Proposed] Add NPC memory context projection or JSON/detail table later |
| High | Theme snapshot | Only primary/secondary category slots | Multi-category interpretation loses tags/mood | [Proposed] Keep snapshot but link to classification version/snapshot |
| Medium | Tests | Tests assert exact enum strings | Refactor breaks tests despite behavior compatibility | [Proposed] Add compatibility tests before taxonomy tests |
| Medium | DB DDL | Hibernate `ddl-auto=update`, no migrations | Production schema drift cannot be audited from repo | [Proposed] Introduce explicit migrations before taxonomy persistence |
| Low | Evolution | Evolution point source not category-based | Less affected by taxonomy | [Proposed] Keep source-based points unchanged initially |

## 12. Recommended Target Architecture

[Proposed] Target flow:

```text
Photo
  ↓
ImageObservationProvider
  ↓
AI Observation
  ↓
MemoryClassificationService
  ↓
Eden Classification
  ↓
WorldExpressionResolver
  ↓
Theme / VillageChange / NPC Context
```

[Proposed] `ImageObservationProvider` responsibilities:

- Generate observable facts from an image or mock input.
- Return provider-neutral observation data.
- Never decide Project Eden category, theme, village change, resonance reward, or NPC dialogue.
- Support both current mock provider and future multimodal providers.

[Proposed] AI Observation data shape:

- `subjects`
- `objects`
- `scene`
- `activities`
- `relationships`
- `moodSignals`
- `rawProviderResponse`
- `provider`
- `modelVersion`

[Proposed] `MemoryClassificationService` responsibilities:

- Map observations into Eden taxonomy.
- Decide legacy-compatible primary category.
- Decide secondary categories.
- Generate tags.
- Derive mood/context.
- Record or return `taxonomyVersion`.
- Maintain a `LegacyVillageCategoryMapper` for existing systems.

[Proposed] Eden Classification data shape:

- `primaryCategory`
- `secondaryCategories`
- `tags`
- `mood`
- `confidence`
- `fallback`
- `taxonomyVersion`
- `legacyRecognizedObject`
- `legacyVillageCategory`

[Proposed] `WorldExpressionResolver` responsibilities:

- Convert Eden Classification to theme candidates, village changes, NPC context hints, and history messages.
- Own UNKNOWN/fallback expression policy.
- Avoid dependency on AI provider labels.

[Proposed] Legacy compatibility strategy:

- Keep existing `VillageCategory`.
- Keep existing `RecognizedObject`.
- Keep existing API fields.
- Keep existing DB data.
- Dual-write or derive new classification data later.
- Prefer new classification when present.
- Fall back to existing enum fields for older rows.

## 13. Migration Boundaries

[Confirmed] Keep:

- Existing `VillageCategory`
- Existing `RecognizedObject`
- Existing API response fields
- Existing UNKNOWN policy
- Existing `VillageTheme`
- Existing `VillageChange`
- Existing NPC Dialogue behavior
- Existing tests' behavioral meaning
- Existing DB data

[Proposed] Add later:

- `MemoryTaxonomyCategory`
- `MemoryTag`
- `MemoryClassification`
- Primary category
- Secondary categories
- Tags
- Provider
- Model version
- Taxonomy version
- `LegacyVillageCategoryMapper`

[Confirmed] This step does not add any of the above types in code.

[Proposed] Do not remove or rewrite the current enum columns in the next migration. They should remain as legacy compatibility/projection fields until all read paths support the new taxonomy model.

## 14. Recommended STEP 2 Boundary

[Proposed] STEP 2 can safely add only foundational taxonomy structures, without connecting them to Recognition/Village flows yet:

- `MemoryTaxonomyCategory`
- `MemoryTag`
- Repository interfaces
- Basic seed data

[Proposed] STEP 2 safety conditions:

1. Do not modify existing enum columns.
2. Do not change `RecognitionResponse`.
3. Do not connect new taxonomy tables to current recognition execution path yet.
4. New tables should be independent.
5. Seed data must be idempotent.
6. Existing tests must pass unchanged.
7. Do not change UNKNOWN behavior.
8. Do not change current theme/change/NPC dialogue behavior.
9. Additive migrations should not require existing data rewrites.

[Proposed] STEP 2 should define stable codes for taxonomy categories/tags as strings, not as Java enum-only values, if the product expects ongoing taxonomy changes.

## 15. Open Questions

### Q1. Should taxonomy category/tag definitions live in DB, code, or both?

[Unknown] Current code does not define a future taxonomy source of truth.

[Confirmed] Current `VillageCategory` and `RecognizedObject` are code enums.

[Proposed] Recommended direction: DB-backed taxonomy definitions with code-level constants only for stable legacy compatibility.

### Q2. Should one photo create one classification row or multiple observation/classification rows?

[Unknown] The future data model is not implemented.

[Proposed] Recommended direction: one observation result per photo/provider attempt, one classification snapshot per taxonomy version, and multiple classification items/tags.

### Q3. Should UNKNOWN be a category, a fallback state, or both?

[Confirmed] Current system uses `RecognizedObject.UNKNOWN` and `VillageCategory.UNKNOWN`.

[Proposed] Recommended direction: keep legacy UNKNOWN values, but model future fallback separately as classification status/reason.

### Q4. Should Resonance reward use observed object, taxonomy tag, or world-expression result?

[Confirmed] Current reward uses `RecognizedObject`.

[Proposed] Recommended direction: reward policy should target Eden classification tags/categories, not raw provider observation.

### Q5. Should Collection count raw observed objects or Eden taxonomy tags?

[Confirmed] Current collection is keyed by `RecognizedObject`.

[Proposed] Recommended direction: keep current collection as legacy discovery projection, and introduce taxonomy-based discovery separately.

### Q6. Should NPC Memory remember categories, tags, mood, or full summaries?

[Confirmed] Current NPC Memory stores one `rememberedCategory`, one theme, one dialogue key, and count.

[Proposed] Recommended direction: keep simple category slot for compatibility, then add a separate NPC memory context projection for tags/mood/recent classification summaries.

### Q7. Is production DB schema generated only by Hibernate?

[Unknown] Repository has no migration files, but deployed DB state cannot be verified without DB access.

[Proposed] Recommended direction: introduce explicit migrations before adding taxonomy persistence.

## 16. Audit Summary

[Confirmed] VillageCategory 사용 파일 수: 30 files under `src`.

[Confirmed] RecognizedObject 사용 파일 수: 19 files under `src`.

[Confirmed] 관련 switch 수: 8 relevant switch expressions in the audited memory/world-expression area:

- `VillageInterpretationService.themeFor`
- `VillageInterpretationService.priority`
- `VillageInterpretationService.messageFor`
- `VillageInterpretationService.expressionsFor`
- `VillageService.rulesFor`
- `VillageService.reactionFor`
- `NpcDialogueRule.selectDialogue`
- `EvolutionService.pointFor` is related to Recognition/Resonance source points but not category-specific

[Confirmed] 관련 DB column 수: 16 enum columns directly related to memory taxonomy/recognition/world expression:

- `Recognition.recognizedObject`
- `VillageMemory.category`
- `VillageChange.category`
- `VillageChange.changeType`
- `VillageHistory.historyType`
- `VillageHistory.category`
- `VillageHistory.changeType`
- `VillageThemeSnapshot.theme`
- `VillageThemeSnapshot.primaryCategory`
- `VillageThemeSnapshot.secondaryCategory`
- `NpcMemory.rememberedTheme`
- `NpcMemory.rememberedCategory`
- `Resonance.recognizedObject`
- `Resonance.rewardType`
- `Resonance.rewardSeedType`
- `Collection.recognizedObject`

[Inferred] If counting only direct category/object fields and excluding theme/change/reward enum columns, there are 8.

[Confirmed] 관련 API endpoint 수: 16 audited endpoints:

- `POST /api/photos`
- `GET /api/photos/me`
- `POST /api/photos/{photoId}/recognize`
- `GET /api/photos/recognitions`
- `GET /api/village/me`
- `GET /api/village/history`
- `GET /api/village/changes`
- `GET /api/village/interpretation`
- `GET /api/npcs/{npcId}/dialogue`
- `GET /api/statistics/me`
- `GET /api/collections/me`
- `POST /api/resonances`
- `GET /api/resonances/me`
- `GET /api/evolution/me`
- `GET /api/evolution/history`
- `GET /api/evolution/decorations`

[Confirmed] This count includes the three audited Evolution endpoints because Recognition and Resonance can add evolution points.

[Confirmed] 관련 test file 수: 10 strongly coupled files:

- `RecognitionIntegrationTests`
- `VillageIntegrationTests`
- `VillageInterpretationIntegrationTests`
- `NpcContextProviderTests`
- `NpcDialogueRuleTests`
- `NpcDialogueServiceTests`
- `NpcDialogueIntegrationTests`
- `NpcMemoryServiceTests`
- `CollectionAchievementIntegrationTests`
- `ResonanceIntegrationTests`

[Confirmed] Critical Risk:

- Single recognized object/category persistence.
- `RecognizedObject` mixing observation, classification, collection, reward, and world-expression input.
- `VillageMemory` aggregate unique by one category.
- `Resonance` and `Collection` keyed by `RecognizedObject`.

[Confirmed] High Risk:

- `RecognitionResponse` single object/category API.
- `VillageInterpretationResponse` primary/secondary-only API.
- NPC Memory/context single remembered category.
- Category/theme/change switch maintenance.

[Confirmed] Medium Risk:

- Tests and docs tightly assert enum strings.
- Statistics depend indirectly on collection model.

[Confirmed] Low Risk:

- Photo upload DTOs.
- Evolution source-based point model.

[Proposed] STEP 2 진행 가능 여부: 가능. 단, 독립 taxonomy seed/table foundation only로 제한해야 한다.

[Proposed] 선행 조치:

1. Existing enum columns and existing API fields must remain unchanged.
2. New taxonomy structures should be additive and disconnected from live Recognition/Village execution path at first.
3. A legacy mapper and compatibility test plan should be defined before connecting new classification to world expression.

---

## STEP 2 Implementation Record — Taxonomy Entity Foundation

[Confirmed] Sprint 10 STEP 2 added an independent taxonomy foundation without connecting it to the existing Recognition, Village Memory, Village Interpretation, Village Change, NPC Memory, Resonance, Collection, Achievement, or Statistics flows.

### Added Domain Model

[Confirmed] `MemoryTaxonomyCategory` was added as an independent JPA entity.

- Table: `memory_taxonomy_categories`
- Purpose: stable Eden taxonomy category foundation for future classification work
- Key fields:
  - `code`
  - `displayName`
  - `parent`
  - `categoryType`
  - `active`
  - `sortOrder`
  - `taxonomyVersion`
  - `createdAt`
  - `updatedAt`

[Confirmed] `MemoryTag` was added as an independent JPA entity.

- Table: `memory_tags`
- Purpose: stable tag foundation for future multi-tag memory classification
- Key fields:
  - `code`
  - `displayName`
  - `tagType`
  - `active`
  - `taxonomyVersion`
  - `createdAt`
  - `updatedAt`

### Added Enums

[Confirmed] `MemoryTaxonomyCategoryType` was added with the following values:

- `DOMAIN`
- `ACTIVITY`
- `RELATIONSHIP`
- `PLACE`
- `MOOD`

[Confirmed] `MemoryTagType` was added with the following values:

- `SUBJECT`
- `SCENE`
- `ACTIVITY`
- `MOOD`
- `RELATIONSHIP`
- `OBJECT`

### Added Repositories

[Confirmed] `MemoryTaxonomyCategoryRepository` was added.

- `findByCode(String code)`
- `existsByCode(String code)`
- `findAllByActiveTrueOrderBySortOrderAscIdAsc()`
- `findAllByTaxonomyVersionAndActiveTrueOrderBySortOrderAscIdAsc(String taxonomyVersion)`

[Confirmed] `MemoryTagRepository` was added.

- `findByCode(String code)`
- `existsByCode(String code)`
- `findAllByActiveTrueOrderByCodeAsc()`
- `findAllByTaxonomyVersionAndActiveTrueOrderByCodeAsc(String taxonomyVersion)`

### Added Seed Initialization

[Confirmed] `MemoryTaxonomySeeder` and `MemoryTaxonomySeedInitializer` were added.

[Confirmed] The seeder is idempotent and only inserts missing codes. It does not overwrite existing `displayName`, `active`, or other existing record state.

[Confirmed] Default taxonomy version is `v1`.

[Confirmed] Default category seed count: 21.

[Confirmed] Default tag seed count: 34.

### Added Migration

[Confirmed] Flyway was introduced for database migration management.

[Confirmed] Migration file added:

- `src/main/resources/db/migration/V1__create_memory_taxonomy_tables.sql`

[Confirmed] The migration creates only the independent taxonomy tables:

- `memory_taxonomy_categories`
- `memory_tags`

[Confirmed] No existing enum columns, existing application tables, or existing feature tables were modified by this migration.

### Compatibility Boundary

[Confirmed] STEP 2 did not modify:

- `VillageCategory`
- `RecognizedObject`
- `Recognition`
- `VillageMemory`
- `VillageInterpretation`
- `VillageChange`
- `NpcMemory`
- `Resonance`
- `Collection`
- `Achievement`
- `Statistics`
- Existing API DTOs
- Existing Controllers
- Frontend

[Proposed] The next safe step is to add a classification model or mapper in isolation, still without changing existing API responses or world-expression behavior.

---

## STEP 3 Implementation Record

### Added

[Confirmed] `MemoryClassification` was added as an independent JPA entity for Eden Classification history.

[Confirmed] `MemoryClassificationCategory` was added to persist secondary category links.

[Confirmed] `MemoryClassificationTag` was added to persist tag links with confidence.

[Confirmed] `MemoryClassificationCategoryRole` was added with:

- `PRIMARY`
- `SECONDARY`

[Confirmed] Repository interfaces were added for:

- `MemoryClassification`
- `MemoryClassificationCategory`
- `MemoryClassificationTag`

[Confirmed] AI Observation JSON storage was added to `MemoryClassification.observation`.

[Confirmed] Provider and model metadata were added:

- `provider`
- `modelVersion`
- `taxonomyVersion`

[Confirmed] V2 migration was added:

- `src/main/resources/db/migration/V2__create_memory_classification_tables.sql`

### Storage Decision

[Confirmed] Primary Category:

- Stored directly on `memory_classifications.primary_category_id`.
- Nullable to support fallback, pending classification, and UNKNOWN-like cases.
- This follows the STEP 3 option A decision: exactly one primary category is simple to query and reason about.

[Confirmed] Secondary Categories:

- Stored in `memory_classification_categories`.
- `role` is retained for future compatibility, but current persistence tests use `SECONDARY`.
- Unique constraint prevents duplicate `(classification_id, category_id)` rows.

[Confirmed] Tags:

- Stored in `memory_classification_tags`.
- Unique constraint prevents duplicate `(classification_id, tag_id)` rows.
- A join entity is used instead of `@ManyToMany` because confidence and future provenance fields are required.

[Confirmed] Observation JSON:

- Java type: `Map<String, Object>`.
- Hibernate mapping: `@JdbcTypeCode(SqlTypes.JSON)`.
- PostgreSQL migration type: `JSONB`.
- Test profile uses Hibernate-generated schema because legacy tables are not Flyway-managed in H2.

[Confirmed] Observation storage policy:

- Does not store authorization headers.
- Does not store API keys.
- Does not store JWTs.
- Does not store image binary or base64.
- Does not store full EXIF or exact GPS.
- Stores only meaning-oriented observation fields such as subjects, objects, scene, activities, relationships, and mood signals.

[Confirmed] Provider:

- Stored as `VARCHAR`, not a Java enum.
- This avoids tight coupling to future AI provider names.

[Confirmed] Confidence:

- Java type: `BigDecimal`.
- DB type: `NUMERIC(5, 4)`.
- Check constraint: nullable or `0 <= confidence <= 1`.
- Entity-level Hibernate `@Check` mirrors the migration constraint for H2 test schema generation.

[Confirmed] Delete Policy:

- Photo → Classification: cascade delete is allowed.
- Recognition → Classification: migration uses `ON DELETE SET NULL`.
- Taxonomy Category / Tag → Classification links: delete is restricted by FK.
- Classification → Category/Tag link rows: cascade delete is allowed.
- Taxonomy Category and Tag themselves are not cascade-deleted from Classification entities.

[Confirmed] Classification history policy:

- Multiple `MemoryClassification` rows are allowed for one Photo.
- `recognition` is nullable.
- No unique constraint exists on `photo_id` or `recognition_id`.

### Compatibility

[Confirmed] Legacy Recognition fields unchanged.

[Confirmed] Existing photo upload flow unchanged.

[Confirmed] Existing Recognition flow unchanged.

[Confirmed] Existing Village Memory, Village Interpretation, Village Change, NPC Memory, Resonance, Collection, Achievement, and Statistics flows unchanged.

[Confirmed] Existing API unchanged.

[Confirmed] No dual-write was added.

[Confirmed] No world expression integration was added.

[Confirmed] No Service or Controller was added for Memory Classification in STEP 3.

### Test Environment Note

[Confirmed] Production/local PostgreSQL uses Flyway V1 and V2 migrations.

[Confirmed] The test profile uses H2 with Hibernate `ddl-auto=create-drop` and has Flyway disabled.

[Inferred] This is necessary because existing legacy tables such as `photos` and `recognitions` are still Hibernate-managed rather than Flyway-managed. Running V2 before Hibernate creates those tables causes H2 migration failure on FK creation.

[Confirmed] PostgreSQL startup verification applied V2 and started successfully on an alternate local port because port 8080 was already in use.

### Next Boundary

[Proposed] STEP 4 should introduce legacy compatibility mapping and controlled dual-write.

[Proposed] STEP 4 must keep existing API responses stable while deciding how `RecognizedObject`, legacy `VillageCategory`, and new taxonomy categories map to each other.

---

## STEP 4 Implementation Record — Legacy Compatibility & Controlled Dual-Write

### Summary

[Confirmed] STEP 4 added a legacy compatibility bridge from existing `Recognition` rows to the new `MemoryClassification` structure.

[Confirmed] The legacy bridge does not change the existing Recognition API response.

[Confirmed] The legacy bridge does not make `MemoryClassification` the source of truth for world expression.

[Confirmed] Existing flows remain unchanged:

- Recognition
- VillageMemory
- VillageInterpretation
- VillageChange
- NPC Memory / Dialogue
- Resonance
- Collection
- Achievement
- Statistics

### Added Components

[Confirmed] Added `LegacyVillageCategoryMapper`.

- Maps legacy `RecognizedObject` / `VillageCategory` to `MemoryTaxonomyCategory.code`.
- `NATURE`, `ANIMAL`, `FOOD`, `WATER`, `WALK`, `STUDY`, and `WORK` map to the same taxonomy codes.
- `UNKNOWN`, unrecognized results, and legacy fallback categories produce no primary category.

[Confirmed] Added `LegacyMemoryTagMapper`.

- Maps only clear, existing legacy signals to seeded `MemoryTag.code` values.
- Does not infer mood, relationship, indoor/outdoor, family, friend, or warmth tags.
- `UNKNOWN` and legacy fallback objects return no tags.

[Confirmed] Added `LegacyMemoryClassificationWriter`.

- Loads `Photo` and `Recognition` by ID.
- Creates a `MemoryClassification`.
- Stores primary category on `MemoryClassification.primaryCategory`.
- Stores tags through `MemoryClassificationTag`.
- Does not create secondary category rows.
- Does not create a `PRIMARY` join row.
- Uses a separate `REQUIRES_NEW` transaction so AFTER_COMMIT writes are committed independently.

[Confirmed] Added `LegacyRecognitionCompletedEvent`.

- Event payload contains only:
  - `photoId`
  - `recognitionId`
- Entity instances are not stored in the event.

[Confirmed] Added `LegacyRecognitionClassificationListener`.

- Uses `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- Runs synchronously.
- Catches dual-write failures and logs them without breaking the existing Recognition flow.
- Does not use `@Async`, queues, or external brokers.

### Connection Point

[Confirmed] The event is published in `RecognitionApplicationService#createRecognition()` after the new `Recognition` entity is saved.

[Confirmed] Existing work still happens through the legacy flow:

```text
Recognition saved
↓
LegacyRecognitionCompletedEvent published
↓
Evolution / VillageMemory / VillageInterpretation / VillageChange remain unchanged
↓
Transaction commits
↓
LegacyRecognitionClassificationListener writes MemoryClassification in REQUIRES_NEW transaction
```

[Confirmed] Reused existing recognitions do not publish a new event because the existing `findByPhotoId(photoId)` branch returns the previous recognition response.

### Transaction Policy

[Confirmed] Existing Recognition transaction is not coupled to the new classification write.

[Confirmed] Classification write runs after commit.

[Confirmed] Classification write uses `Propagation.REQUIRES_NEW`.

[Confirmed] If classification write fails, the listener logs the failure and does not propagate it to the caller.

[Confirmed] If the original transaction rolls back, the AFTER_COMMIT listener does not create a classification.

### Mapping Policy

[Confirmed] `RecognizedObject` to primary taxonomy category uses its existing legacy `VillageCategory`.

[Confirmed] `VillageCategory.UNKNOWN` and `RecognizedObject.UNKNOWN` map to no primary category.

[Confirmed] `recognized=false` also results in fallback classification.

[Confirmed] `UNKNOWN` policy:

- `primaryCategory = null`
- `fallback = true`
- no tag rows
- classification can still be saved

[Confirmed] `STUDY` and `WORK` are mapped to taxonomy category codes `STUDY` and `WORK`.

[Confirmed] Primary category is saved only in:

- `memory_classifications.primary_category_id`

[Confirmed] This STEP does not create:

- `MemoryClassificationCategory` secondary rows
- `MemoryClassificationCategoryRole.PRIMARY` rows

### Tag Mapping Policy

[Confirmed] Clear object tags are mapped when seed codes exist.

Examples:

- `CAT` → `CAT`
- `DOG` → `DOG`
- `FLOWER` → `FLOWER`
- food-like objects → `FOOD`
- water-like objects → `WATER`
- walk-like objects → `ROAD` / `WALKING`
- study-like objects → `BOOK` / `STUDYING`
- work-like objects → `COMPUTER` / `WORKING`

[Confirmed] Tag confidence is `null` because legacy confidence is recognition-level confidence, not tag-level confidence.

[Confirmed] Missing tag codes are skipped and logged as warnings.

### Observation JSON Policy

[Confirmed] Legacy observation JSON stores only minimal legacy recognition fields:

- `source`
- `recognizedObject`
- `legacyCategory`
- `recognized`
- `fallback`
- `confidence`

[Confirmed] Observation JSON does not store:

- JWT
- Authorization headers
- API keys
- user email
- image binary
- base64
- full file paths
- EXIF
- GPS
- full provider raw payload

### Metadata

[Confirmed] Legacy bridge metadata:

- `provider = LEGACY_MOCK`
- `modelVersion = mock-filename-v1`
- `taxonomyVersion = v1`

[Confirmed] `MemoryClassification.confidence` stores normalized legacy recognition confidence in the range `0.00` to `1.00`.

### Idempotency

[Confirmed] Repository lookup was added for:

- `recognitionId`
- `provider`
- `modelVersion`
- `taxonomyVersion`

[Confirmed] V3 migration was added:

- `src/main/resources/db/migration/V3__add_memory_classification_idempotency.sql`

[Confirmed] V3 adds a PostgreSQL partial unique index:

```sql
ON memory_classifications (
    recognition_id,
    provider,
    model_version,
    taxonomy_version
)
WHERE recognition_id IS NOT NULL
```

[Confirmed] Duplicate events for the same recognition produce one classification.

[Confirmed] Current legacy schema has one `Recognition` per `Photo`, so same-photo re-recognition currently reuses the previous recognition and does not create another classification.

[Proposed] If a future Sprint allows multiple recognition attempts per photo, each new recognition ID may produce a separate classification without changing the idempotency key.

### Migration

[Confirmed] V1 and V2 migrations were not modified.

[Confirmed] V3 only adds the partial unique index for controlled dual-write idempotency.

[Confirmed] Test profile still keeps Flyway disabled and uses Hibernate schema generation.

[Proposed] PostgreSQL startup remains the real migration verification path until legacy tables are fully migrated to Flyway.

### Tests

[Confirmed] Added mapper tests for:

- all `RecognizedObject` values
- all legacy `VillageCategory` values
- `UNKNOWN`
- fallback
- `STUDY`
- `WORK`
- no duplicate tags
- no mood/relationship inference

[Confirmed] Added writer tests for:

- classification creation
- photo link
- recognition link
- primary category link
- no secondary category rows
- no primary join row
- tag storage
- observation JSON
- provider/model/taxonomy metadata
- confidence
- fallback
- unknown fallback
- duplicate recognition idempotency
- missing category fallback
- missing tag skip
- code-based lookup even when display name changes

[Confirmed] Added event integration tests for:

- classification creation after Recognition transaction commit
- no classification on rollback
- duplicate event idempotency
- listener failure isolation
- `UNKNOWN`
- `STUDY`
- `WORK`
- reused recognition compatibility

### STEP 5 Boundary

[Proposed] STEP 5 can start reading `MemoryClassification` as a compatibility source, but should not remove legacy fields yet.

[Proposed] STEP 5 should keep existing API contracts stable and add new API fields only behind explicit versioning or additive response changes.

[Proposed] STEP 5 should decide whether classification reads prefer:

1. new `MemoryClassification` rows first with legacy fallback, or
2. legacy Recognition fields first with classification as metadata.

## STEP 6 Implementation Record

### Added

[Confirmed] Added `OpenAIImageObservationProvider` as an opt-in multimodal observation provider.

[Confirmed] Added provider selection through `ImageObservationProviderResolver`.

[Confirmed] Added structured observation response parsing through `OpenAIObservationResponse`.

[Confirmed] Added explicit timeout and limited retry configuration through `OpenAIObservationProperties`.

[Confirmed] Added safe Mock fallback when OpenAI configuration, MIME support, network response, or schema validation is unavailable.

[Confirmed] Added provider configuration under `eden.image-observation`.

### Provider Contract

[Confirmed] API: OpenAI Responses API, `POST /responses`.

[Confirmed] Image transport: base64 data URL sent as a Responses API `input_image`.

[Confirmed] Structured response: `text.format.type = json_schema` is requested by the HTTP client.

[Confirmed] Model configuration: `OPENAI_VISION_MODEL` / `eden.image-observation.openai.model`.

[Confirmed] Timeout: connect timeout and read timeout are configurable.

[Confirmed] Retry: 429 and 5xx / transient I/O are retried up to `max-retries`; 400/401/403 are not retried.

### Failure Policy

[Confirmed] Missing configuration: resolver logs a warning and selects Mock.

[Confirmed] Unsupported MIME: OpenAI provider logs a warning and falls back to Mock.

[Confirmed] Timeout: OpenAI client raises a sanitized provider exception; OpenAI provider falls back to Mock.

[Confirmed] 429: retried according to `max-retries`; final failure falls back to Mock at provider boundary.

[Confirmed] 5xx: retried according to `max-retries`; final failure falls back to Mock at provider boundary.

[Confirmed] Invalid JSON: treated as provider failure and falls back to Mock.

[Confirmed] Authentication error: treated as provider failure, API key is not logged, and provider falls back to Mock.

[Confirmed] Final fallback: if Mock cannot classify the filename, existing UNKNOWN behavior remains.

### Privacy

[Confirmed] Data sent: image bytes as a base64 data URL and the observation instruction/schema.

[Confirmed] Data not sent: user id, email, JWT, friends, NPC memory, village history, GPS, EXIF, local file path, and DB id.

[Confirmed] Data not logged: API key, Authorization header, base64 image, raw request body, raw response, user email, JWT, and full local path.

[Confirmed] Data not persisted: base64 image and raw provider response.

### Compatibility

[Confirmed] Default provider remains Mock.

[Confirmed] Existing API response shape is unchanged.

[Confirmed] Existing World Expression is unchanged.

[Confirmed] STEP 4 dual-write remains the only automatic `MemoryClassification` persistence path.

[Confirmed] STEP 5/6 classification calculation does not directly save rows.

[Confirmed] Frontend is unchanged.

### Known Limitation

[Confirmed] Legacy Classification provider provenance remains `LEGACY_MOCK` / `mock-filename-v1` because STEP 6 does not add a provider provenance migration or Recognition schema fields.

[Confirmed] HEIC is not sent to OpenAI in this step; unsupported MIME falls back to Mock/UNKNOWN.

[Confirmed] Current `Photo` upload stores metadata and a mock URL only, not local/DB/S3 image bytes. Therefore existing `POST /api/photos/{photoId}/recognize` cannot send real image bytes to OpenAI yet and safely falls back to Mock.

[Confirmed] Recognition currently reuses one `Recognition` per photo.

[Unknown] Real photo quality has not been evaluated because default automated tests do not call external OpenAI APIs.

### Next Boundary

[Proposed] STEP 7 will evaluate 30–50 real photos and measure recognition quality, UNKNOWN rate, Primary/Secondary quality, and Tag precision. Before that evaluation, the backend needs a bounded image-byte handoff or storage boundary so the provider can access actual uploaded image content without persisting base64 or exposing secrets.

## STEP 7 Implementation Record

### Added

[Confirmed] Added `UploadedImagePayload` as an internal immutable image-byte handoff object.

[Confirmed] Added `POST /api/photos/{photoId}/recognize-with-image` as an additive multipart recognition endpoint.

[Confirmed] Added `ImageObservationRequest.from(Photo, UploadedImagePayload)` so a persisted `Photo` can be recognized with request-scoped image bytes.

[Confirmed] Added `eden.image-observation.max-image-bytes` / `EDEN_IMAGE_OBSERVATION_MAX_IMAGE_BYTES` with a default of 10MB.

[Confirmed] Added an opt-in image observation evaluation foundation under `com.projecteden.memorytaxonomy.evaluation`.

[Confirmed] Added `.gitignore` entries for local evaluation image and output directories.

### Image Bytes Handoff

[Confirmed] Upload request: existing `POST /api/photos` remains metadata-only and does not trigger recognition in this step.

[Confirmed] Recognition request: additive `POST /api/photos/{photoId}/recognize-with-image` carries the multipart bytes to recognition.

[Confirmed] Existing upload remains metadata-only. `Photo` does not persist bytes, base64, or raw image content.

[Confirmed] Existing `POST /api/photos/{photoId}/recognize` remains unchanged and falls back safely when image bytes are unavailable.

[Confirmed] New `POST /api/photos/{photoId}/recognize-with-image` accepts multipart `file`, verifies the authenticated user's ownership of the `Photo`, reads the multipart bytes once into `UploadedImagePayload`, and passes the payload to the configured observation provider.

[Confirmed] If a `Recognition` already exists for the `Photo`, the service returns the existing result and does not call the provider again.

[Confirmed] Image bytes are request-scoped only. They are not stored in DB, not written to object storage, not included in API responses, and not logged.

[Confirmed] Persistence: only the existing `Photo` metadata and existing `Recognition` result are persisted.

[Confirmed] Re-recognition: Photo ID only re-recognition cannot use real image bytes and keeps Mock/UNKNOWN fallback.

[Confirmed] Maximum size: OpenAI handoff is bounded by `eden.image-observation.max-image-bytes`.

### MIME Policy

[Confirmed] Supported: `image/jpeg`, `image/png`, `image/webp`, `image/gif`.

[Confirmed] Unsupported: all other MIME types fall back safely to Mock.

[Confirmed] HEIC/HEIF: no server conversion is implemented; these MIME types fall back safely to Mock/UNKNOWN.

[Confirmed] Final fallback: Mock provider by filename, then UNKNOWN if no rule matches.

### Provider Size Policy

[Confirmed] OpenAI provider skips external calls when `fileSize > eden.image-observation.max-image-bytes` and falls back to Mock.

[Confirmed] Supported OpenAI MIME types remain `image/jpeg`, `image/png`, `image/webp`, and `image/gif`.

[Confirmed] HEIC/HEIF and unsupported MIME types fall back to Mock. No image conversion pipeline is implemented.

[Confirmed] Oversized AI provider payloads do not fail user recognition flow by themselves; they use the existing Mock/UNKNOWN fallback path.

### Evaluation Foundation

[Confirmed] Manifest: JSON array of `ImageEvaluationCase`.

[Confirmed] `ImageEvaluationApplicationRunner` is disabled by default and only runs when `EDEN_IMAGE_EVALUATION_ENABLED=true`.

[Confirmed] Evaluation reads a JSON manifest and local image files, calls the configured `ImageObservationProvider`, computes `MemoryClassificationResult`, and writes CSV/Markdown reports.

[Confirmed] Provider: evaluation uses the configured provider resolver, so `mock` remains the default and `openai` remains opt-in.

[Confirmed] Metrics: provider success/failure, Mock fallback, UNKNOWN, primary accuracy, secondary precision/recall, tag precision/recall, latency, MIME breakdown, and failure breakdown.

[Confirmed] Reports: CSV detail and Markdown summary.

[Confirmed] Network policy: no network is used by default; OpenAI calls require explicit provider/key/model configuration.

[Confirmed] Evaluation does not create `Photo`, `Recognition`, `VillageMemory`, `MemoryClassification`, or events.

[Confirmed] Evaluation reports include `caseId`, MIME, file size, provider, model version, classification results, expected labels, metric counts, latency, and failure type.

[Confirmed] Evaluation reports do not include raw local image paths, image bytes, base64, API keys, JWTs, or raw provider responses.

### Privacy

[Confirmed] Images committed: evaluation image directories are ignored and images should not be committed.

[Confirmed] Bytes logged: image bytes and base64 are not logged.

[Confirmed] Paths reported: output reports include `caseId`, not raw image path.

[Confirmed] Sensitive traits evaluated: identity, age, gender, race, health, religion, political view, and other sensitive traits are outside the evaluation target.

### Configuration

[Confirmed] Added:

```yaml
eden:
  image-observation:
    max-image-bytes: ${EDEN_IMAGE_OBSERVATION_MAX_IMAGE_BYTES:10485760}
  image-evaluation:
    enabled: ${EDEN_IMAGE_EVALUATION_ENABLED:false}
    manifest: ${EDEN_IMAGE_EVALUATION_MANIFEST:}
    output-directory: ${EDEN_IMAGE_EVALUATION_OUTPUT:}
    max-cases: ${EDEN_IMAGE_EVALUATION_MAX_CASES:100}
```

### Compatibility

[Confirmed] Existing `POST /api/photos/{photoId}/recognize` response shape is unchanged.

[Confirmed] Existing `POST /api/photos` response shape is unchanged.

[Confirmed] Existing `Recognition`, `VillageMemory`, `VillageInterpretation`, `VillageChange`, NPC, Resonance, Collection, and Achievement flows remain unchanged.

[Confirmed] STEP 4 dual-write remains the only automatic `MemoryClassification` persistence path.

[Confirmed] STEP 7 does not add migrations and does not modify V1/V2/V3.

[Confirmed] Frontend is unchanged.

### Known Limitation

[Confirmed] Because the upload endpoint still does not persist image bytes, real-image provider recognition requires clients or test tools to send the file again to `recognize-with-image`.

[Confirmed] Existing frontend can continue using the old endpoints; it will not get real-byte OpenAI recognition until it adopts the additive endpoint or a later storage handoff is implemented.

[Confirmed] Evaluation quality has tooling support, but real-photo metrics require an opt-in manifest and real images outside Git.

[Proposed] STEP 8 or a later boundary can decide whether to add temporary object storage, signed upload storage, or frontend-side immediate recognition handoff.

### Sprint 10 Exit Decision

[Proposed] Ready to close: yes, if full regression tests and local startup pass.

[Proposed] Remaining blockers: none for backend architecture foundation; real-photo quality is still unknown until STEP 7 evaluation is run with a real manifest and OpenAI key.

[Proposed] Recommended next Sprint: run 30–50 real photo evaluation cases, measure UNKNOWN/fallback/precision, then decide whether taxonomy rules or provider prompt need tuning before adding image persistence.

## Sprint 10 Final Validation Status

### Backend Foundation

- Status: Complete
- Tests: 383 passed.
- PostgreSQL: Verified during STEP 7 local startup.
- Flyway: V1–V3 verified during STEP 7 local startup.

### Evaluation Readiness

- Images: 0 local evaluation images found.
- Manifest: No real evaluation manifest found.
- OpenAI configuration: `OPENAI_API_KEY` and `OPENAI_VISION_MODEL` are missing.
- Dry run: Completed with a fake provider; it validates manifest parsing, runner execution, metrics, and CSV/Markdown report generation without network access.

### Real Photo Evaluation

- Status: Pending real dataset
- Reason: Actual evaluation requires an explicitly prepared manifest, at least 30 local consent-reviewed images, an OpenAI API key, and a configured vision model. None were available at final validation.
- Report: `docs/sprint-10-real-photo-evaluation.md`

### Exit Decision

- Sprint 10 status: Conditionally complete
- Remaining blocker: Real-photo quality validation has not been executed, so no recognition-quality metric can be claimed.
- Recommended next action: Prepare a local, consent-reviewed 30–50 image dataset from `docs/evaluation-manifest-template.json`, configure the OpenAI environment variables outside Git, then run the opt-in evaluation and review the generated reports.

### 2026-07-12 Real Dataset Execution Recheck

- [Confirmed] No local evaluation images or real manifest were available.
- [Confirmed] `OPENAI_API_KEY` and `OPENAI_VISION_MODEL` were not configured in the execution environment.
- [Confirmed] The provider environment value was unset; the application configuration defaults it to `mock`.
- [Confirmed] The real OpenAI evaluation was not executed because every required execution condition was missing.
- [Confirmed] No product behavior, API, DB migration, frontend code, or evaluation output was changed by the recheck.
