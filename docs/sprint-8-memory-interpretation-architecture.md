# Sprint 8 Design Spike — Memory Interpretation Architecture

## 1. Summary

Sprint 8에서는 `VillageMemory`와 `VillageChange` 사이에 기억을 해석하는 계층을 추가한다. 권장 구조는 다음과 같다.

- `VillageInterpretation`은 `VillageMemory`에서 매번 계산하는 값 객체로 유지한다.
- 현재 적용 중인 `VillageTheme`만 `VillageThemeSnapshot`으로 저장한다.
- `VillageExpression`은 Theme과 버전이 있는 규칙을 이용해 요청 시 생성한다.
- Theme이 실제로 바뀐 경우에만 기존 `VillageHistory`에 전환 기록을 남긴다.
- 사용자에게 기억 비율, 성향 점수, 사용자 유형을 노출하지 않는다.

이 구조는 Sprint 7의 Memory, Change, History를 유지하면서도 Sprint 9 NPC Memory와 Sprint 10 Memory Reflection에서 동일한 Interpretation과 Theme을 재사용할 수 있다.

## 2. Current Architecture

현재 Sprint 7 흐름은 다음과 같다.

```text
Recognition
  → VillageMemory 누적
  → 카테고리별 임계값 확인
  → VillageChange 기록
  → VillageHistory 및 NPC 문구 기록
```

현재 구조의 장점은 단순하고 결정적이라는 점이다. 반면 Memory 수치가 곧바로 Change와 문구로 연결되므로 다음 한계가 있다.

- 여러 카테고리가 함께 쌓인 마을의 분위기를 설명하기 어렵다.
- NPC, 풍경, 계절이 공통으로 참조할 현재 마을의 의미가 없다.
- 표현 규칙과 기억 집계 규칙이 직접 결합되어 있다.
- 향후 Reflection이나 Story가 원시 Memory 집계를 반복해서 해석할 가능성이 있다.

## 3. Target Architecture

```text
Recognition
  → VillageMemory 기록
  → VillageInterpretation 계산
  → VillageTheme 결정 및 Snapshot 갱신
  → VillageExpression 생성
  → VillageChange / VillageHistory 반영
  → NPC / 풍경 / 계절 / 기억 API 응답
```

각 계층의 책임을 분리한다.

```text
VillageMemory       사실: 무엇이 얼마나 오래 기록되었는가
Interpretation      의미: 어떤 풍경의 흔적이 상대적으로 오래 남았는가
Theme               상태: 현재 마을이 어떤 분위기를 유지하고 있는가
Expression          표현: 그 분위기를 문장과 풍경 요소로 어떻게 보여주는가
```

Interpretation과 Theme은 사용자를 분류하지 않는다. 모든 결과의 주어는 사용자 대신 마을이어야 한다.

## 4. Domain Concepts

### Village Interpretation

`VillageInterpretation`은 현재 Character의 `VillageMemory` 목록을 입력으로 받는 불변 계산 결과다.

권장 필드:

- `primaryCategory`: 현재 마을 풍경에 가장 오래 남은 카테고리
- `secondaryCategory`: 보조적으로 함께 나타나는 카테고리, 없으면 `null`
- `candidateTheme`: 현재 기억으로 계산된 Theme 후보
- `interpretationVersion`: 사용한 해석 규칙 버전
- 내부 전용 `categoryWeights` 또는 원시 count: Theme 결정에만 사용하고 API에 노출하지 않음

Interpretation은 “사용자가 무엇을 좋아한다”는 결론을 만들지 않는다. “마을에 어떤 흔적이 오래 머물렀다”는 표현 재료만 제공한다.

동률은 결정적으로 처리한다. 권장 우선순위는 최근 기록 시각, 기존 Theme과의 연속성, 마지막으로 Enum 고정 순서다. 랜덤 선택은 사용하지 않는다.

### Village Theme

`VillageTheme`은 현재 마을에 적용되는 안정적인 분위기 상태다.

후보 Enum:

- `BLOOMING_VILLAGE`
- `WARM_VILLAGE`
- `WALKING_VILLAGE`
- `WATERSIDE_VILLAGE`
- `ANIMAL_FRIENDLY_VILLAGE`
- `QUIET_VILLAGE`

기본 매핑:

| Primary Category | Theme |
|---|---|
| NATURE | BLOOMING_VILLAGE |
| FOOD | WARM_VILLAGE |
| WALK | WALKING_VILLAGE |
| WATER | WATERSIDE_VILLAGE |
| ANIMAL | ANIMAL_FRIENDLY_VILLAGE |
| UNKNOWN 또는 Memory 없음 | QUIET_VILLAGE |

Theme은 조회할 때마다 즉시 바꾸지 않는다. 작은 count 변화나 동률로 분위기가 자주 뒤집히지 않도록 현재 Theme을 유지하는 전환 정책이 필요하다.

MVP 권장 전환 정책:

1. 최초 Memory 기록 시 후보 Theme을 적용한다.
2. 현재 Theme과 후보 Theme이 같으면 유지한다.
3. 후보 카테고리 count가 현재 Theme 카테고리보다 최소 2회 이상 앞설 때만 전환한다.
4. 동률이면 현재 Theme을 유지한다.
5. 규칙 버전 변경만으로 자동 전환하지 않고 다음 Memory 기록 시 재평가한다.

이 정책의 수치는 사용자에게 노출하지 않는다.

### Village Expression

`VillageExpression`은 Theme을 실제 화면과 다른 도메인이 사용할 수 있는 표현 지시로 변환한 불변 값 객체다.

권장 구성:

- `type`: `NPC_DIALOGUE`, `SCENERY_HINT`, `SEASON_REACTION`, `OBJECT_HINT`
- `key`: 프론트엔드 및 다국어 리소스가 사용할 안정적인 키
- `message`: MVP에서 바로 표시할 한국어 기본 문구
- `priority`: 여러 표현의 표시 우선순위
- `expressionVersion`: 표현 규칙 버전
- 선택적 `metadata`: 렌더러가 해석할 최소 힌트. 자유 형식 AI 출력은 사용하지 않음

예시:

```text
Theme: BLOOMING_VILLAGE
NPC_DIALOGUE: 꽃과 바람이 이 마을에 오래 머물고 있네요.
SCENERY_HINT: SOFT_FLOWER_ACCENT
OBJECT_HINT: SMALL_WIND_CHIME
```

Expression은 렌더링 결과가 아니다. Unity, 이미지, 실제 배치 좌표를 포함하지 않는다.

## 5. Entity vs Computed Decision

| Concept | Store as Entity? | Reason | Repository Needed? | History Needed? | API Exposure |
|---|---|---|---|---|---|
| VillageMemory | 기존 Entity 유지 | 원본 누적 사실이며 재계산할 외부 원천이 없음 | 기존 Repository 유지 | 기존 MEMORY_RECORDED 유지 | 기존 API 유지 |
| VillageInterpretation | 아니오, 계산 값 객체 | Memory로 결정적으로 재계산 가능하며 규칙 변경에 유연해야 함 | 전용 Repository 불필요 | Interpretation 자체는 남기지 않음 | 해석 문장과 primary/secondary만 제한 노출 |
| VillageTheme Enum | 아니오 | 코드상 안정적인 어휘 | 불필요 | 해당 없음 | Theme 이름 노출 가능 |
| VillageThemeSnapshot | 예, Character당 1개 | 현재 Theme의 안정성, 전환 억제, NPC 간 일관성에 필요 | 필요 | Theme 변경 시만 필요 | 현재 Theme과 적용 시각 노출 가능 |
| VillageExpression | 아니오, Rule 기반 값 객체 | Theme과 규칙으로 재생성 가능하고 문구/렌더링 변경이 잦음 | 불필요 | 일반 생성은 남기지 않음 | 표현 목록 노출 |
| Theme Change History | 기존 VillageHistory 확장 | 의미 있는 마을 변화이며 Reflection과 Story의 시간축이 됨 | 기존 Repository 재사용 | 필요 | History API에서 노출 |
| Expression Rule | MVP는 코드/설정 | 운영 편집 요구가 생기기 전까지 DB 복잡도 불필요 | MVP 불필요 | 규칙 버전만 Snapshot에 기록 | 노출하지 않음 |

## 6. Recommended Architecture

### 권장 컴포넌트

```text
VillageInterpretationService
  - interpret(List<VillageMemory>, VillageThemeSnapshot?)
  - 순수 계산에 가깝게 유지

VillageThemeService
  - evaluateAndApply(characterId, interpretation)
  - 전환 정책 적용
  - Snapshot 저장
  - 실제 Theme 변경 시 History 기록

VillageExpressionService
  - express(theme, interpretation, context)
  - 버전된 결정적 Rule 사용

VillageService
  - Sprint 7 Memory 기록 흐름 조정
  - 위 세 서비스를 orchestration
  - 기존 Change/History 정책 유지
```

### 저장 모델

MVP에서 새로 저장할 Entity는 `VillageThemeSnapshot` 하나만 권장한다.

```text
VillageThemeSnapshot
- id
- character (OneToOne, unique)
- currentTheme
- primaryCategory
- secondaryCategory nullable
- interpretationVersion
- appliedAt
- createdAt
- updatedAt
```

`primaryCategory`와 `secondaryCategory`는 사용자의 정체성이 아니라 Snapshot을 재현하기 위한 마을 상태다. API 문서에서도 반드시 “마을에 남은 흔적”으로 정의한다.

기존 `VillageHistory`에는 다음 확장을 권장한다.

- `VillageHistoryType.THEME_CHANGED` 추가
- `theme` nullable 필드 추가 또는 message에만 기록

MVP에서는 조회와 Story 재사용성을 위해 nullable `theme` 필드를 추가하는 편이 낫다. 별도 Theme History Entity는 만들지 않는다.

### 계산 모델

- Interpretation은 Memory를 읽을 때마다 계산한다.
- Theme 후보도 Interpretation에서 계산한다.
- 실제 현재 Theme은 Snapshot에서 읽는다.
- Expression은 현재 Snapshot과 Interpretation을 기반으로 매번 생성한다.
- 규칙은 `interpretationVersion`, `expressionVersion`으로 버전 관리한다.

### 설계 질문 답변

#### Q1. Village Interpretation은 저장해야 하는가?

저장하지 않는다. VillageMemory로 결정적으로 계산할 수 있고 해석 규칙은 향후 변경될 가능성이 높다. 저장하면 동일 Memory에 서로 다른 시대의 해석 결과가 섞이거나 대규모 재계산이 필요해진다. 필요하면 로그나 관측 지표로만 측정한다.

#### Q2. Village Theme은 저장해야 하는가?

현재 적용 Theme은 Snapshot으로 저장한다. 조회 시 계산만 하면 동률이나 작은 변화로 Theme이 흔들리고, NPC와 풍경이 같은 요청 시점에도 다른 분위기를 참조할 수 있다. 후보 Theme은 계산하되 적용 결과만 저장한다.

#### Q3. Village Expression은 저장해야 하는가?

MVP에서는 저장하지 않는다. Theme과 Rule로 생성하며 문구, 로컬라이징, 렌더링 힌트가 바뀌어도 DB 마이그레이션 없이 개선할 수 있어야 한다. 향후 AI Story처럼 사용자가 다시 열람해야 하는 완성된 서사만 별도 Artifact로 저장한다.

#### Q4. Theme 변경 순간을 History로 남겨야 하는가?

남겨야 한다. Theme 전환은 단순 조회 결과가 아니라 마을의 의미 있는 시간 변화다. 단, 후보 Theme 계산이나 동일 Theme 재평가는 기록하지 않고 실제 전환만 기록한다.

#### Q5. Sprint 8 MVP 최소 저장 데이터는 무엇인가?

- 기존 VillageMemory, VillageChange, VillageHistory
- 신규 VillageThemeSnapshot 1개/Character
- 실제 Theme 변경 시 VillageHistory 1건

Interpretation과 Expression은 저장하지 않는다.

#### Q6. Sprint 9 NPC Memory에서 활용하기 좋은 구조는 무엇인가?

NPC는 원시 count 대신 다음 읽기 모델을 사용해야 한다.

```text
VillageContext
- currentTheme
- primaryCategory
- secondaryCategory
- latestVillageChanges
- recentVillageHistories
- expressions filtered by NPC_DIALOGUE
```

`VillageContextProvider` 인터페이스를 두면 NPC 도메인이 Village Repository를 직접 조합하지 않아도 된다. NPC별 기억은 별도 도메인에 저장하되 Village Theme을 외래키로 강결합하지 않고 당시 Theme 값을 Snapshot 형태로 복사한다.

#### Q7. Sprint 10 Reflection과 AI Story 확장 포인트는 무엇인가?

- Interpretation/Expression 규칙 버전
- 기간 범위를 받는 Interpretation 입력 모델
- `VillageContextProvider`
- Theme 전환 History
- 최근 History를 안전하게 요약하는 `MemoryNarrativePort`
- deterministic Rule 구현과 AI 구현을 교체할 수 있는 Port
- 생성된 Reflection/Story의 prompt version, source history IDs, 생성 시각을 저장하는 별도 Artifact

AI 입력에는 사용자의 성향 라벨 대신 Village Memory, Theme, History의 제한된 사실만 제공한다.

## 7. Data Flow

### Memory 기록 흐름

```text
Recognition 성공
  → VillageService.recordVillageMemory
  → VillageMemory upsert
  → MEMORY_RECORDED History
  → VillageInterpretationService.interpret
  → VillageThemeService.evaluateAndApply
      → 기존 Snapshot과 후보 Theme 비교
      → 실제 전환 시 Snapshot 갱신
      → THEME_CHANGED History
  → 기존 VillageChange 임계값 평가
  → VillageExpressionService.express
  → NPC_REACTION History
  → VillageResponse 반환
```

### 조회 흐름

```text
GET /api/village/interpretation
  → VillageMemory 조회
  → Interpretation 계산
  → Theme Snapshot 조회
  → Expression 생성
  → 사용자 성향 수치 없이 응답
```

Recognition, Evolution, Village 기록은 기존처럼 하나의 트랜잭션에 참여한다. Theme 또는 History 저장이 실패하면 해당 Recognition 이후 부가 기록도 함께 롤백되는 현재 원자성을 유지한다.

## 8. Proposed API

기존 `/api/village/me`, `/history`, `/changes`는 변경하지 않는다. Sprint 8에서는 비파괴적으로 다음 API를 추가한다.

```http
GET /api/village/interpretation
Authorization: Bearer {accessToken}
```

응답 예시:

```json
{
  "theme": "BLOOMING_VILLAGE",
  "primaryCategory": "NATURE",
  "secondaryCategory": "WALK",
  "message": "이 마을은 꽃과 바람이 오래 머무는 곳이 되어가고 있습니다.",
  "expressions": [
    {
      "type": "NPC_DIALOGUE",
      "key": "village.blooming.npc.default",
      "message": "꽃이 이 마을을 참 좋아하는 것 같네요."
    },
    {
      "type": "SCENERY_HINT",
      "key": "village.blooming.scenery.soft_flower_accent",
      "message": "바람이 머무는 자리에 작은 꽃빛이 이어집니다."
    }
  ],
  "appliedAt": "2026-07-07T15:00:00"
}
```

API 원칙:

- count, 비율, confidence, score는 노출하지 않는다.
- `primaryCategory`는 사용자 분류가 아니라 현재 마을 표현을 선택한 근거다.
- `secondaryCategory`는 없을 수 있다.
- 빈 마을은 `QUIET_VILLAGE`와 기존 첫 순간 대기 문구를 반환한다.
- 기존 Village API 응답 구조는 Sprint 8에서 변경하지 않는다.

장기적으로 `/api/village/me`에 Interpretation을 합칠 수 있지만, Sprint 8에서는 기존 클라이언트 호환성을 위해 별도 API가 안전하다.

## 9. Risks & Trade-offs

| Risk | Impact | Mitigation |
|---|---|---|
| Theme oscillation | 문구와 풍경이 자주 뒤집힘 | 현재 Theme 유지, 최소 차이 전환, 동률 유지 |
| 규칙 변경에 따른 결과 차이 | 같은 Memory가 다른 후보 Theme 생성 | 규칙 버전 저장, 다음 Memory 시점에만 재평가 |
| 사용자 라벨링으로 오해 | 핵심 철학과 개인정보 신뢰 훼손 | 모든 문장 주어를 마을로 제한, 수치/성향 API 금지 |
| Expression DB 저장 남용 | 문구 수정과 로컬라이징 어려움 | MVP Rule 기반 계산, 완성 Story만 향후 저장 |
| History 과다 생성 | 저장량과 노이즈 증가 | 실제 Theme 전환만 기록, 재평가 기록 금지 |
| Recognition 트랜잭션 결합 | 표현 오류가 Recognition을 롤백할 수 있음 | Sprint 8은 결정적 로컬 Rule만 사용, 외부 AI 호출 금지 |
| 다국어 문구 하드코딩 | 향후 번역 비용 | Expression key를 응답하고 message는 기본 로케일 fallback으로 취급 |
| 오래된 Snapshot | Memory와 현재 Theme 불일치 가능 | Memory 기록 트랜잭션에서만 Snapshot 갱신, 운영 검증용 재계산 도구는 별도 |

Theme Snapshot 저장은 안정성을 얻는 대신 규칙 변경 후 즉시 반영되지 않는 비용이 있다. Project Eden에서는 즉각적인 재분류보다 천천히 변화하는 일관성이 철학에 더 적합하다.

## 10. Sprint 8 Implementation Plan

### 생성 후보 파일

```text
com.projecteden.village.domain
- VillageTheme.java
- VillageExpressionType.java
- VillageThemeSnapshot.java

com.projecteden.village.repository
- VillageThemeSnapshotRepository.java

com.projecteden.village.interpretation
- VillageInterpretation.java
- VillageInterpretationService.java
- VillageThemePolicy.java

com.projecteden.village.expression
- VillageExpression.java
- VillageExpressionRule.java
- VillageExpressionService.java

com.projecteden.village.dto
- VillageInterpretationResponse.java
- VillageExpressionResponse.java

com.projecteden.village.controller
- 기존 VillageController에 GET /interpretation 추가
```

패키지는 현재 프로젝트의 `domain/dto/repository/service/controller` 규칙을 유지해야 한다면 interpretation과 expression 클래스를 `service` 및 `dto` 하위에 배치해도 된다. 별도 계층은 책임 구분을 위한 제안이며 필수 패키지 규칙은 아니다.

### 수정 후보 파일

- `VillageHistoryType.java`: `THEME_CHANGED` 추가
- `VillageHistory.java`: nullable `theme` 추가 검토
- `VillageService.java`: Interpretation → Theme → Expression orchestration 추가
- `VillageResponse.java`: 기존 구조는 유지
- `VillageController.java`: 신규 조회 API만 추가
- `RecognitionApplicationService.java`: 호출 위치는 유지하고 VillageService 내부 흐름만 확장
- `README.md`: 철학, Theme, Expression API 문서 추가

### 구현 순서

1. Theme Enum과 Snapshot Entity/Repository 추가
2. 순수 계산 Interpretation 테스트 작성
3. Theme 전환 및 흔들림 방지 Policy 테스트 작성
4. Theme Snapshot 갱신과 THEME_CHANGED History 구현
5. 결정적 Expression Rule 구현
6. 기존 `recordVillageMemory` orchestration 확장
7. 신규 API 추가
8. 기존 Sprint 7 테스트와 전체 테스트 실행

### 테스트 계획

- Memory 없음 → QUIET_VILLAGE
- NATURE 우세 → BLOOMING_VILLAGE
- FOOD 우세 → WARM_VILLAGE
- 동률에서 현재 Theme 유지
- 후보가 최소 차이를 넘기 전까지 Theme 유지
- Theme 실제 전환 시 Snapshot 갱신
- 동일 Theme 재평가 시 History 미생성
- Theme 변경 시 THEME_CHANGED History 1건
- Expression이 Theme별 결정적 결과 반환
- 응답에 count, ratio, score, user type이 없음
- 기존 `/api/village/me`, `/history`, `/changes` 회귀 없음
- JWT 없이 신규 API 호출 시 401
- 기존 전체 테스트 유지

## 11. Sprint 9+ Extension Plan

### Sprint 9 — NPC Memory

- `VillageContextProvider`를 통해 Theme과 NPC_DIALOGUE Expression 제공
- NPC별 최근 반응과 마지막 참조 History ID만 저장
- NPC가 VillageMemory Repository를 직접 조회하지 않도록 분리
- 당시 Theme과 Expression key를 NPC Memory에 복사해 이후 규칙 변경에도 과거 맥락 유지

### Sprint 10 — Memory Reflection

- 하루, 주간, 계절 등 기간 범위를 받는 Interpretation 추가
- Reflection 생성에 사용한 VillageHistory ID 목록 저장
- Reflection 결과는 사용자가 다시 열람하므로 별도 Artifact Entity로 저장
- Theme과 Reflection은 사용자 성격 진단이 아니라 마을의 변화 회고로 제한

### AI Story

- `MemoryNarrativePort` 인터페이스 정의
- deterministic template 구현을 기본값으로 유지
- 외부 AI 구현은 Port 뒤에 배치
- 입력 데이터 최소화, prompt/version/source IDs 기록
- 금지 표현 필터와 결과 검증 단계를 저장 전에 적용

### Season Story 및 표현 확장

- Expression context에 Season을 읽기 전용으로 추가
- Theme 자체를 Season에 따라 변경하지 않고 표현만 변주
- 사운드, 조명, 오브젝트는 새로운 ExpressionType으로 확장
- 렌더링 좌표나 엔진 종속 데이터는 백엔드 Theme Entity에 저장하지 않음

## 12. Final Recommendation

Sprint 8에서는 VillageMemory를 원본 사실로 유지하고, VillageInterpretation은 매번 계산하며, 현재 적용된 VillageTheme만 Character 단위 Snapshot으로 저장하는 구조를 권장한다. VillageExpression은 버전된 결정적 Rule로 생성하고 DB에 저장하지 않는다. 실제 Theme 전환만 기존 VillageHistory에 남겨 시간적 연속성을 확보한다. 이 구조는 사용자를 수치나 유형으로 정의하지 않으면서도 마을의 분위기를 안정적으로 유지하고, Sprint 9 NPC Memory와 Sprint 10 Reflection·AI Story가 동일한 `VillageContext`를 재사용할 수 있게 한다.
