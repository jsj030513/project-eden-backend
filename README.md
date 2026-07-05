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
