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
- Photo Mission과 보상 지급은 아직 구현하지 않았습니다.
