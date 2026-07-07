# Project Eden Backend

Project Eden의 Spring Boot 백엔드 애플리케이션입니다.

## 실행 방법

프로젝트 루트에서 다음 명령을 실행합니다.

```bash
docker compose up --build
```

환경값을 변경하려면 `.env.example`을 참고하여 `.env` 파일을 생성합니다.

## Health Check

애플리케이션 실행 후 아래 URL에서 상태를 확인할 수 있습니다.

http://localhost:8080/health

## 테스트

로컬에서 다음 명령으로 전체 테스트를 실행합니다.

```bash
./mvnw test
```

## CI

GitHub Actions CI가 `main` 또는 `develop` 브랜치로의 push와 pull request에서 자동으로 Maven 테스트를 실행합니다.

## 회원가입 API

### Endpoint

```http
POST /api/users/signup
```

### Request

```json
{
  "email": "test@example.com",
  "password": "password123",
  "nickname": "eden"
}
```

### Response

성공 시 `201 Created`를 반환합니다.

```json
{
  "id": 1,
  "email": "test@example.com",
  "nickname": "eden"
}
```

## 로그인 API

### Endpoint

```http
POST /api/auth/login
```

### Request

```json
{
  "email": "test@example.com",
  "password": "password123"
}
```

### Response

```json
{
  "accessToken": "jwt-token",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "test@example.com",
  "nickname": "eden"
}
```

## 내 정보 조회 API

로그인 응답의 Access Token을 `Authorization` 헤더에 전달합니다.

```http
GET /api/users/me
Authorization: Bearer jwt-token
```

```json
{
  "id": 1,
  "email": "test@example.com",
  "nickname": "eden"
}
```

> `JWT_SECRET`은 로컬 개발 기본값만 제공됩니다. 운영 환경에서는 반드시 충분히 긴 비밀값을 환경변수로 관리해야 합니다.

## Character API

Character API는 JWT 인증이 필요합니다.

### 캐릭터 생성

```http
POST /api/characters
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "name": "에덴",
  "gender": "NONE",
  "hairStyle": "PIXEL_CUT",
  "hairColor": "brown",
  "outfit": "ROBE",
  "job": "WIZARD"
}
```

성공 시 `201 Created`와 생성된 캐릭터 정보를 반환합니다.

### 내 캐릭터 조회

```http
GET /api/characters/me
Authorization: Bearer {accessToken}
```

### 직업별 기본 도구 및 무기

| Job | WeaponType | 설명 |
|---|---|---|
| BEGINNER | NONE | 초보자 |
| FARMER | HOE | 농부의 괭이 |
| EXPLORER | COMPASS | 탐험가의 나침반 |
| GUARDIAN | SHIELD | 수호자의 방패 |
| MERCHANT | BAG | 상인의 가방 |
| BREEDER | FEED_BASKET | 사육사의 먹이 바구니 |
| WIZARD | STAFF | 마법사의 지팡이 |
| WARRIOR | SWORD | 전사의 검 |
| ARCHER | BOW | 궁수의 활 |
| BUILDER | HAMMER | 건축가의 망치 |

## World API

World API는 캐릭터를 생성한 사용자만 이용할 수 있으며 JWT 인증이 필요합니다.

### 첫 월드 생성

```http
POST /api/worlds
Authorization: Bearer {accessToken}
```

성공 시 `201 Created`를 반환합니다.

```json
{
  "id": 1,
  "worldName": "에덴의 세계",
  "season": "SPRING",
  "weather": "SUNNY",
  "day": 1,
  "gold": 100,
  "wood": 20,
  "stone": 10,
  "food": 20
}
```

### 내 월드 조회

```http
GET /api/worlds/me
Authorization: Bearer {accessToken}
```

사용자 캐릭터에 연결된 월드 정보를 반환합니다.

## House API

House API는 월드를 생성한 사용자만 이용할 수 있으며 JWT 인증이 필요합니다.

### 첫 집 생성

```http
POST /api/houses
Authorization: Bearer {accessToken}
```

성공 시 `201 Created`를 반환합니다.

```json
{
  "id": 1,
  "houseName": "에덴의 집",
  "level": 1,
  "houseType": "CABIN",
  "maxDecoration": 10
}
```

### 내 집 조회

```http
GET /api/houses/me
Authorization: Bearer {accessToken}
```

사용자 월드에 연결된 집 정보를 반환합니다.

## Inventory API

Inventory API는 집을 생성한 사용자만 이용할 수 있으며 JWT 인증이 필요합니다.

### 인벤토리 생성

```http
POST /api/inventories
Authorization: Bearer {accessToken}
```

성공 시 `201 Created`를 반환합니다.

```json
{
  "id": 1,
  "capacity": 30,
  "usedSlot": 0
}
```

### 내 인벤토리 조회

```http
GET /api/inventories/me
Authorization: Bearer {accessToken}
```

사용자 집에 연결된 인벤토리 정보를 반환합니다.

## Region API

World 생성 시 다음 기본 지역 5개가 자동 생성됩니다.

| RegionType | DisplayName |
|---|---|
| VILLAGE | 마을 |
| FOREST | 숲 |
| RIVER | 강 |
| HILL | 언덕 |
| FLOWER_FIELD | 꽃밭 |

### 내 지역 목록 조회

```http
GET /api/regions/me
Authorization: Bearer {accessToken}
```

```json
[
  {
    "id": 1,
    "regionType": "VILLAGE",
    "displayName": "마을",
    "unlocked": true
  }
]
```

## Seed API

Inventory 생성 시 튜토리얼용 `FLOWER` 씨앗 5개가 자동 지급됩니다.

### 내 씨앗 조회

```http
GET /api/seeds/me
Authorization: Bearer {accessToken}
```

### 씨앗 심기

```http
POST /api/seeds/plant
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "seedType": "FLOWER"
}
```

```json
{
  "seedType": "FLOWER",
  "remaining": 4,
  "plantId": 1,
  "plantStage": "SEED",
  "resonanceBoosted": true
}
```

씨앗을 심으면 FLOWER_FIELD에 `SEED` 단계의 Plant가 생성됩니다.

## Tutorial API

Character 생성 시 `WELCOME` 단계의 튜토리얼 진행 상태가 자동 생성됩니다.

진행 순서:

```text
WELCOME → MEET_CHIEF → CHECK_HOME → CHECK_INVENTORY
→ VISIT_FLOWER_FIELD → FINISHED
```

### 내 튜토리얼 조회

```http
GET /api/tutorial/me
Authorization: Bearer {accessToken}
```

### 다음 단계 진행

```http
PATCH /api/tutorial/advance
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "nextStep": "MEET_CHIEF"
}
```

```json
{
  "currentStep": "MEET_CHIEF",
  "completed": false
}
```

## NPC API

World 생성 시 기본 NPC 5명이 Region에 자동 배치됩니다.

| NPCType | 이름 | Region |
|---|---|---|
| VILLAGE_CHIEF | 촌장 에반 | VILLAGE |
| CARPENTER | 목수 브람 | VILLAGE |
| MERCHANT | 상인 노아 | VILLAGE |
| ARCHIVIST | 기록관 루나 | VILLAGE |
| GARDENER | 정원사 릴리 | FLOWER_FIELD |

### 내 World NPC 조회

```http
GET /api/npcs/me
Authorization: Bearer {accessToken}
```

```json
[
  {
    "id": 1,
    "npcType": "VILLAGE_CHIEF",
    "npcName": "촌장 에반",
    "description": "에덴 마을의 촌장",
    "regionType": "VILLAGE"
  }
]
```

## Plant API

### 내 식물 조회

```http
GET /api/plants/me
Authorization: Bearer {accessToken}
```

Plant 단계:

| PlantStage | 설명 |
|---|---|
| SEED | 씨앗 상태 |
| SPROUT | 새싹 |
| GROWING | 성장 중 |
| BLOOMED | 개화 또는 완성 상태 |
| WITHERED | 시든 상태 |

`GET /api/plants/me` 호출 시 현재 시간을 기준으로 성장 단계를 계산하고 저장합니다. 자동 성장 스케줄러는 아직 없습니다.

### 공명 Plant 성장 규칙

| 경과 시간 | PlantStage |
|---|---|
| 심은 직후 | SEED |
| 10초 | SPROUT |
| 30초 | GROWING |
| 60초 | BLOOMED |

첫 Plant는 `resonanceBoosted=true`로 생성되어 위 빠른 성장 규칙을 적용받습니다.

### 일반 Plant 성장 규칙

| 경과 시간 | PlantStage |
|---|---|
| 심은 직후 | SEED |
| 1일 | SPROUT |
| 2일 | GROWING |
| 3일 | BLOOMED |

`WITHERED` 단계와 자동 스케줄러는 향후 Sprint에서 구현할 예정입니다.

## Harvest API

### 수확 가능한 식물 조회

```http
GET /api/plants/harvestable
Authorization: Bearer {accessToken}
```

성장 상태를 갱신한 뒤 `BLOOMED` 상태의 Plant만 반환합니다.

### Plant 수확

```http
POST /api/plants/{plantId}/harvest
Authorization: Bearer {accessToken}
```

```json
{
  "plantId": 1,
  "seedType": "FLOWER",
  "earnedGold": 10,
  "earnedSeedType": "FLOWER",
  "earnedSeedQuantity": 1,
  "message": "FLOWER를 수확했습니다."
}
```

수확된 Plant는 삭제되며 동일 Plant를 다시 수확할 수 없습니다.

### 수확 보상

| SeedType | Gold | Seed |
|---|---:|---:|
| FLOWER | 10 | FLOWER +1 |
| WHEAT | 15 | WHEAT +1 |
| CARROT | 20 | CARROT +1 |
| POTATO | 20 | POTATO +1 |
| TOMATO | 25 | TOMATO +1 |

## Daily Mission API

Daily Mission은 플레이를 제한하지 않고 오늘 수행하면 좋은 목표의 완료 상태를 제공합니다.

```http
GET /api/daily
Authorization: Bearer {accessToken}
```

```json
{
  "missionDate": "2026-07-06",
  "plantCompleted": true,
  "harvestCompleted": false,
  "photoCompleted": false,
  "rewardClaimed": false
}
```

- 오늘 Mission이 없으면 조회 또는 행동 시 자동 생성됩니다.
- 씨앗 심기 성공 시 `plantCompleted=true`가 됩니다.
- 수확 성공 시 `harvestCompleted=true`가 됩니다.
- Photo Mission은 아직 구현하지 않았습니다.

### Daily Mission 보상 수령

```http
POST /api/daily/reward
Authorization: Bearer {accessToken}
```

```json
{
  "missionDate": "2026-07-06",
  "earnedGold": 50,
  "earnedSeedType": "FLOWER",
  "earnedSeedQuantity": 2,
  "rewardClaimed": true,
  "message": "일일 미션 보상을 수령했습니다."
}
```

- 씨앗 심기와 수확 미션을 모두 완료해야 수령할 수 있습니다.
- 하루에 한 번만 수령할 수 있습니다.
- 완료 보상은 Gold `+50`, FLOWER Seed `+2`입니다.
- 미구현 상태인 Photo Mission은 현재 보상 조건에 포함되지 않습니다.
- 공명 경험치와 랜덤 보상은 아직 지급하지 않습니다.

## Photo API

사진 파일 자체는 저장하지 않으며, 파일 메타데이터와 UUID 기반 Mock URL만 DB에 기록합니다.

### Plant 사진 업로드

```http
POST /api/photos
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

Multipart 파라미터:

| 이름 | 타입 | 설명 |
|---|---|---|
| `plantId` | Long | 사진을 연결할 Plant ID |
| `file` | File | 업로드할 사진 |

```bash
curl -X POST http://localhost:8080/api/photos \
  -H "Authorization: Bearer {accessToken}" \
  -F "plantId=1" \
  -F "file=@flower.jpg"
```

```json
{
  "id": 1,
  "plantId": 1,
  "imageUrl": "/uploads/photos/9b8c1b5e-0000-0000-0000-000000000000.jpg",
  "uploadedAt": "2026-07-06T16:30:00"
}
```

본인 소유의 `BLOOMED` Plant에만 사진을 업로드할 수 있습니다.

### 내 사진 조회

```http
GET /api/photos/me
Authorization: Bearer {accessToken}
```

실제 파일 저장소, S3, 공명 및 보상 연동은 아직 구현하지 않았습니다.

## AI Recognition API

현재 Recognition은 외부 AI API를 호출하지 않고 `MockRecognitionService`를 사용합니다.

### 사진 인식

```http
POST /api/photos/{photoId}/recognize
Authorization: Bearer {accessToken}
```

```json
{
  "id": 1,
  "photoId": 1,
  "recognizedObject": "FLOWER",
  "confidence": 95,
  "recognized": true
}
```

동일한 사진에 인식 결과가 이미 존재하면 새로 생성하지 않고 기존 결과를 반환합니다.

### 내 인식 결과 조회

```http
GET /api/photos/recognitions
Authorization: Bearer {accessToken}
```

Mock AI는 이미지 URL이나 파일 내용을 분석하지 않고 항상 `FLOWER`, 신뢰도 `95`, 인식 성공을 반환합니다.
Gemini, OpenAI Vision과 다중 객체 인식은 아직 구현하지 않았습니다.

## Resonance API

공명은 사진의 Recognition 결과와 캐릭터가 연결되어 Seed 또는 Gold 보상을 얻는 시스템입니다.

### 공명 생성 및 보상 수령

```http
POST /api/resonances
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "recognitionId": 1
}
```

```json
{
  "id": 1,
  "recognizedObject": "FLOWER",
  "rewardType": "SEED",
  "rewardSeedType": "FLOWER",
  "rewardSeedQuantity": 1,
  "rewardGold": 0,
  "resonanceDate": "2026-07-06",
  "message": "FLOWER와 공명하여 FLOWER 씨앗을 획득했습니다."
}
```

### 내 공명 기록 조회

```http
GET /api/resonances/me
Authorization: Bearer {accessToken}
```

### 공명 보상 규칙

| RecognizedObject | RewardType | 보상 |
|---|---|---|
| FLOWER | SEED | FLOWER Seed +1 |
| TOMATO | SEED | TOMATO Seed +1 |
| CARROT | SEED | CARROT Seed +1 |
| POTATO | SEED | POTATO Seed +1 |
| WHEAT | SEED | WHEAT Seed +1 |
| UNKNOWN | NONE | Gold +5 |

- 동일 Recognition은 한 번만 공명할 수 있습니다.
- 같은 대상의 공명 보상은 하루 한 번만 받을 수 있습니다.
- 하루 최대 공명 보상 횟수는 10회입니다.
- `recognized=false`인 결과는 공명할 수 없습니다.
- 도감, 스토리, 친구 보너스와 계절 이벤트는 아직 구현하지 않았습니다.

## Social API

모든 Social API는 JWT 인증이 필요합니다.

### 친구

```http
POST /api/friends
PUT /api/friends/{friendshipId}/accept
DELETE /api/friends/{friendshipId}
GET /api/friends
GET /api/friends/pending
```

친구 요청은 닉네임 또는 friendCode로 전송합니다. friendCode는 현재 User ID 또는 이메일을 사용합니다.

```json
{
  "nickname": "eden_friend"
}
```

자기 자신, 중복 요청, 이미 친구인 사용자에 대한 요청은 거부됩니다.

- 잘못된 요청이나 중복 요청은 `400 Bad Request`를 반환합니다.
- JWT가 없거나 유효하지 않으면 `401 Unauthorized`를 반환합니다.
- 다른 사용자의 요청·알림을 수정하거나 비친구 기능에 접근하면 `403 Forbidden`을 반환합니다.
- 존재하지 않는 리소스는 `404 Not Found`를 반환합니다.

### 섬 방문과 응원

```http
POST /api/visits/{friendId}
GET /api/visits/history
POST /api/cheers/{friendId}
```

섬 방문은 친구 관계에서만 가능하고 읽기 전용 정보를 반환합니다. 같은 친구 응원은 하루 한 번 가능하며 수신 캐릭터에게 EXP `+5`와 알림을 지급합니다.

### 미접속 패널티

```http
GET /api/penalties/me
```

| 미접속 일수 | 표시 단계 |
|---:|---|
| 0 | NONE |
| 1 | WEEDS |
| 2 | LEAVES |
| 3 | WILTED_FLOWERS |
| 4 이상 | DESOLATE_ISLAND |

패널티는 시각적 상태만 반환하며 아이템, 건물, 경험치 등 게임 데이터를 삭제하거나 감소시키지 않습니다.

### 알림, 프로필, 친구 랭킹

```http
GET /api/notifications
PUT /api/notifications/{notificationId}/read
GET /api/profiles/me
PUT /api/profiles/me
GET /api/ranking/friends
```

- 매일 오전 8시에 `DAILY_PROMPT` 알림을 생성합니다.
- `CHEER_RECEIVED`, `SEASON_CHANGE`, `FRIEND_ADDED` 알림을 지원합니다.
- 프로필에서 닉네임, 아바타, 대표 섬과 대표 생물을 수정할 수 있습니다.
- 랭킹은 친구와 본인만 대상으로 하며 전역 랭킹은 제공하지 않습니다.

## Collection & Achievement API

공명에 성공하면 Recognition 결과가 도감에 등록됩니다. 이미 등록된 대상은 발견 횟수와 최근 발견 시각이 갱신됩니다.

### 내 도감 조회

```http
GET /api/collections/me
Authorization: Bearer {accessToken}
```

```json
{
  "totalCollectableCount": 6,
  "uniqueCollectedCount": 1,
  "completionRate": 16.666666666666668,
  "collections": [
    {
      "id": 1,
      "recognizedObject": "FLOWER",
      "rarity": "COMMON",
      "discoveredCount": 2,
      "firstDiscoveredAt": "2026-07-07T10:00:00",
      "lastDiscoveredAt": "2026-07-07T11:00:00"
    }
  ]
}
```

### 희귀도 규칙

| 인식 대상 | 희귀도 |
|---|---|
| FLOWER, TOMATO, CARROT, POTATO, WHEAT | COMMON |
| UNKNOWN | UNCOMMON |

### 내 업적 조회

```http
GET /api/achievements/me
Authorization: Bearer {accessToken}
```

| 업적 코드 | 조건 | 지급 칭호 |
|---|---|---|
| FIRST_DISCOVERY | 최초 발견 1회 | FIRST_OBSERVER |
| COLLECTION_3 | 서로 다른 대상 3종 | SMALL_COLLECTOR |
| TOTAL_DISCOVERY_10 | 총 발견 10회 | DAILY_OBSERVER |
| SAME_OBJECT_5 | 같은 대상 5회 | FOCUSED_OBSERVER |

업적은 조건 충족 시 자동 달성되며 같은 업적과 보상 칭호는 중복 지급되지 않습니다.

### 칭호 조회 및 대표 칭호 설정

```http
GET /api/titles/me
Authorization: Bearer {accessToken}
```

```http
PUT /api/titles/me/active
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "titleCode": "SMALL_COLLECTOR"
}
```

보유한 칭호만 대표 칭호로 설정할 수 있으며 대표 칭호는 한 개만 활성화됩니다.

### 내 통계 조회

```http
GET /api/statistics/me
Authorization: Bearer {accessToken}
```

통계에는 총 발견 횟수, 도감 등록 종수, 달성 업적 수, 보유 칭호 수와 최근 발견 시각이 포함됩니다.

## World Evolution API

World Evolution은 Recognition, Resonance, Achievement, Cheer 활동을 월드 성장 포인트로 누적합니다.

### Evolution Point 규칙

| 활동 | EP |
|---|---:|
| RECOGNITION | +5 |
| RESONANCE | +10 |
| ACHIEVEMENT | +30 |
| CHEER | +2 |

### 레벨 및 스테이지 규칙

| 누적 EP | 레벨 | 스테이지 |
|---:|---:|---|
| 0 | 1 | SEED |
| 100 | 2 | SPROUT |
| 250 | 3 | GARDEN |
| 500 | 4 | FOREST |
| 1000 | 5 | PARADISE |

최대 레벨은 5입니다.

### 장식 해금 규칙

| 레벨 | 해금 장식 |
|---:|---|
| 2 | FLOWER_FIELD |
| 3 | TREE |
| 4 | BENCH, ROAD |
| 5 | LAMP, FOUNTAIN, WINDMILL |

장식은 한 번만 해금되며 중복 저장되지 않습니다.

### 월드 성장 상태 조회

```http
GET /api/evolution/me
Authorization: Bearer {accessToken}
```

```json
{
  "worldLevel": 3,
  "evolutionPoint": 260,
  "worldStage": "GARDEN",
  "nextLevelPoint": 500,
  "progressRate": 52.0
}
```

### 성장 기록 및 장식 조회

```http
GET /api/evolution/history
Authorization: Bearer {accessToken}
```

```http
GET /api/evolution/decorations
Authorization: Bearer {accessToken}
```

History에는 EP 획득, 레벨 상승, 스테이지 상승, 장식 해금처럼 실제 발생한 이벤트만 저장됩니다.

## Living Village API

> 당신은 마을을 꾸미지 않습니다. 마을이 당신을 닮아갑니다.

Living Village는 오늘의 순간을 카테고리별 기억으로 남기고, 누적된 삶의 흔적에 따라 마을의 변화와 NPC 문구를 기록합니다. 실제 렌더링이나 직접 꾸미기 기능은 포함하지 않습니다.

### Village Category

| Recognition 결과 | VillageCategory |
|---|---|
| FLOWER | NATURE |
| TOMATO, CARROT, POTATO, WHEAT | FOOD |
| UNKNOWN | UNKNOWN |

`WALK`, `WATER`, `ANIMAL`은 향후 Recognition 대상 확장을 위해 준비되어 있습니다.

### Village Memory와 Change

- Village Memory는 카테고리별 누적 기억 수와 최근 기록 시각을 관리합니다.
- Village Change는 렌더링 데이터가 아니라 마을에 나타난 변화의 기록입니다.
- 같은 변화는 한 번만 나타납니다.
- UNKNOWN도 `QUIET_PLACE`라는 조용한 변화로 기록됩니다.

주요 변화 임계값:

| 기억 수 | FOOD | NATURE |
|---:|---|---|
| 1 | TABLE | FLOWER_PATH |
| 5 | HERB_GARDEN | GARDEN |
| 10 | BAKERY | FOREST_PATH |
| 20 | CAFE | VIEWPOINT |

### 내 마을 조회

```http
GET /api/village/me
Authorization: Bearer {accessToken}
```

```json
{
  "dominantCategory": "NATURE",
  "totalMemoryCount": 3,
  "memories": [
    {
      "category": "NATURE",
      "memoryCount": 3,
      "lastRecordedAt": "2026-07-07T15:00:00"
    }
  ],
  "changes": [
    {
      "category": "NATURE",
      "changeType": "FLOWER_PATH",
      "appeared": true,
      "appearedAt": "2026-07-07T15:00:00",
      "message": "꽃이 참 많이 피는 계절이네요."
    }
  ],
  "latestMessage": "꽃이 참 많이 피는 계절이네요."
}
```

### 마을 기록과 변화 조회

```http
GET /api/village/history
Authorization: Bearer {accessToken}
```

```http
GET /api/village/changes
Authorization: Bearer {accessToken}
```

Village History는 `MEMORY_RECORDED`, `CHANGE_APPEARED`, `NPC_REACTION` 기록을 제공합니다. World Evolution이 활동의 성장 수치를 누적한다면, Living Village는 같은 순간이 마을의 풍경과 기억에 어떻게 머물렀는지를 기록합니다.

## Memory Interpretation API

> 마을은 당신을 판단하지 않습니다. 당신이 오래 바라본 것들을 천천히 닮아갑니다.

Memory Interpretation은 Sprint 7의 `VillageMemory` 누적값을 사용자 유형이나 점수로 노출하지 않고, 현재 마을의 분위기와 결정적인 표현 문구로 변환합니다. Recognition이 성공해 기억이 기록되면 Interpretation Snapshot도 같은 흐름에서 갱신됩니다.

### Village Theme

| 중심 기억 | Theme |
|---|---|
| NATURE | BLOOMING_VILLAGE |
| FOOD | WARM_VILLAGE |
| WALK | WALKING_VILLAGE |
| WATER | WATERSIDE_VILLAGE |
| ANIMAL | ANIMAL_FRIENDLY_VILLAGE |
| UNKNOWN | QUIET_VILLAGE |
| 기억 없음 | UNDEFINED |

`VillageThemeSnapshot`은 Character별로 하나만 저장되며 현재 Theme, 중심/보조 카테고리, 규칙 버전과 Theme 적용 시각을 보관합니다. 같은 Theme이 유지되면 `appliedAt`은 바뀌지 않습니다. 새로운 중심 카테고리의 기억 수가 기존 중심보다 3 이상 많거나 기존 Theme이 `UNDEFINED`인 경우에만 Theme이 전환되어, 짧은 변화로 분위기가 흔들리지 않습니다. 실제 Theme 전환만 `THEME_CHANGED` History로 기록합니다.

### 현재 마을 해석 조회

```http
GET /api/village/interpretation
Authorization: Bearer {accessToken}
```

```json
{
  "theme": "BLOOMING_VILLAGE",
  "primaryCategory": "NATURE",
  "secondaryCategory": "WALK",
  "message": "이 마을은 꽃과 바람이 오래 머무는 곳이 되어가고 있습니다.",
  "expressions": [
    {
      "type": "NPC_DIALOGUE",
      "message": "꽃이 이 마을을 참 좋아하는 것 같네요.",
      "hint": "flower"
    },
    {
      "type": "SCENERY_HINT",
      "message": "부드러운 꽃길이 마을에 더 오래 머뭅니다.",
      "hint": "flower_path"
    }
  ],
  "appliedAt": "2026-07-07T15:00:00",
  "ruleVersion": "v1"
}
```

Expression은 랜덤이나 외부 AI 없이 Theme별 규칙으로 항상 동일하게 생성됩니다. 실제 AI 분석, 사용자 심리 진단, NPC Memory, Memory Reflection 및 AI Story는 이 Sprint에 포함하지 않습니다.
