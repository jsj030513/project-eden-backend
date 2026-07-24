# Auth Login Regression Hotfix

## Incident

[Confirmed] The reported impact was existing-account login failure and signup-followed-by-login failure.

[Confirmed] A local PostgreSQL E2E reproduction with a newly created temporary account succeeded before and after the hotfix: signup `201`, login `200`, JWT-protected `/api/users/me` `200`. The temporary rows were deleted after each verification.

[Unknown] No password for the existing local user was supplied, so its real login cannot be tested without guessing or changing user data. No guess or password modification was performed.

## User Impact

[Confirmed] Login failures were returned as `400 Bad Request`, not `401 Unauthorized`, making the authentication contract ambiguous for clients.

[Confirmed] Email whitespace/case normalization happened after Bean Validation, so an otherwise valid email with surrounding spaces failed validation before reaching service-level normalization.

[Confirmed] A disabled user could previously retain JWT authentication because the JWT filter did not inspect `User.status`.

## Reproduction

[Confirmed] Local profile used PostgreSQL at `localhost:5432/project_eden`, Flyway validated four migrations, and the schema was version 3. Spring Boot started on port 18080.

[Confirmed] A temporary account was created with an uppercase, whitespace-padded email. Before moving normalization to DTO construction, request validation returned `400` (`must be a well-formed email address`).

[Confirmed] Before the hotfix, wrong-password and duplicate-email requests returned `400`. A first duplicate E2E attempt also returned `400` because its test nickname exceeded the existing 20-character validation limit; a second request with a valid nickname reached the duplicate-email branch.

## Root Cause

[Confirmed] Primary cause: authentication and duplicate-resource failures were represented as `IllegalArgumentException`, which the global handler maps to `400`.

[Confirmed] Related cause: email normalization was performed in the service layer, after `@Email` validation.

[Confirmed] Related security gap: JWT authentication looked up an existing user but did not require `status == ACTIVE`.

[Confirmed] Recent dirty-worktree changes in `SecurityConfig`, `JwtAuthenticationFilter`, and `JwtTokenProvider` are CORS and photo-upload diagnostic changes. They do not change `AuthService`, `UserService`, or the BCrypt bean logic. No direct password-encoder regression was found.

## Existing User Compatibility

[Confirmed] The existing PostgreSQL row has a 60-character `$2a$` BCrypt prefix, `USER` role, and `ACTIVE` status. The password column is `varchar(255)`.

[Confirmed] `BCryptPasswordEncoder` supports `$2a$` hashes. The test suite verifies an existing-style BCrypt hash login path.

[Confirmed] No existing user row, password hash, role, status, or database volume was changed.

## Registration Flow

[Confirmed] `SignupRequest` normalizes email with `trim().toLowerCase(Locale.ROOT)` during DTO creation, before validation.

[Confirmed] `UserService` uses the same normalized value for duplicate lookup and persistence, then encodes the password once with the configured BCrypt encoder.

[Confirmed] Duplicate email now returns `409 Conflict` with the existing Korean message.

## Login Flow

[Confirmed] `LoginRequest` normalizes email before validation.

[Confirmed] `AuthService` uses the normalized email, checks `User.isActive()`, then uses BCrypt `matches` against the stored hash.

[Confirmed] Unknown email, incorrect password, and inactive user return the same `401` message to avoid user-enumeration detail.

## JWT Flow

[Confirmed] JWT subject remains the user ID; claims remain `email` and `role`; the access-token expiration remains configuration-driven.

[Confirmed] `JwtAuthenticationFilter` validates the signature/subject, loads the user by ID, and now stores authentication only for an ACTIVE user.

[Confirmed] Tampered and expired token tests reject authentication. There is no logout or refresh-token endpoint in the current backend; expiration is the current stateless token invalidation behavior.

## Security Configuration

[Confirmed] `POST /api/users/signup`, `POST /api/auth/login`, `OPTIONS /**`, and `GET /health` are public. All other requests remain authenticated. CSRF is disabled and the session policy remains stateless.

[Confirmed] CORS allows configured local origins only. An actual preflight from a configured LAN development origin to login returned `200` with the configured allow-origin and `content-type` header. The host-specific origin remains local configuration and is not recorded in the repository.

## Fix

[Confirmed] Added `AuthenticationFailureException` mapped to `401`.

[Confirmed] Added `DuplicateResourceException` mapped to `409`.

[Confirmed] Added shared DTO/service email normalization.

[Confirmed] Added ACTIVE-status checks at login and JWT filter authentication.

## Tests

[Confirmed] Added or updated tests for legacy-style BCrypt login, normalized login/signup email, wrong password `401`, unknown email `401`, duplicate email `409`, inactive-user login/token rejection, and tampered/expired JWT rejection.

## PostgreSQL Verification

[Confirmed] New temporary signup: `201`.

[Confirmed] New temporary login: `200` and token issued.

[Confirmed] JWT-protected `/api/users/me`: `200`.

[Confirmed] Wrong password: `401`.

[Confirmed] Duplicate email with valid request: `409`.

[Confirmed] Temporary test users were deleted; existing row remained untouched.

## Remaining Risks

- [Unknown] Existing user password correctness cannot be proven without a user-provided test credential; BCrypt format compatibility is confirmed.
- [Proposed] A future account-state policy should define allowed status values beyond `ACTIVE` without exposing reasons to clients.
- [Proposed] A future refresh-token/logout sprint should define revocation semantics if immediate logout is required.

## Sprint 11 Resume Decision

[Confirmed] Auth blocker resolved for verified backend flows: normalized signup/login, JWT issuance, protected access, failure contracts, and ACTIVE-status enforcement work against local PostgreSQL.

[Proposed] Sprint 11 image normalization STEP 2 can resume after this hotfix is reviewed. Do not resume by changing authentication behavior again without preserving these regression tests.
