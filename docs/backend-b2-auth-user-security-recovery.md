# Backend B2 Auth/User Security Recovery

## Scope

This recovery commit tracks the authentication and user-account changes required for
signup, login, JWT authentication, validation, and stable authentication errors. It
does not include photo/recognition, dataset/evaluation, vision, village/world, CORS
LAN configuration, or general runtime configuration.

## Authentication contract

- Signup and login normalize email addresses with trim and locale-stable lowercase.
- Passwords are encoded with BCrypt before persistence and checked with
  `PasswordEncoder.matches`.
- Unknown users, incorrect passwords, and inactive accounts share the same `401`
  response so the login endpoint does not reveal account existence.
- Duplicate email and nickname requests return `409`; database unique constraints
  remain the final concurrent-write defense.
- Protected endpoints require a valid Bearer JWT whose subject maps to an active
  user. Missing prefixes, malformed tokens, expired tokens, and invalid signatures
  do not populate the security context.

## JWT boundary

- `JWT_SECRET` is required from runtime configuration and has no production default.
- Tests use a clearly test-only secret.
- Access tokens contain only the user ID subject and role plus standard timestamps.
- Passwords, password hashes, email addresses, secrets, and raw tokens are neither
  placed in claims nor logged.

## Database alignment

The existing `users` schema and B9 baseline provide unique constraints for both
`email` and `nickname`, a `varchar(255)` password-hash column, and the role/status
columns used by the entity. No B2 migration is required.

## Verification

The B2 release gate requires the auth-targeted suite, the complete backend suite,
compile/package checks, and the same checks from a tracked-only archive. Exact
command results are recorded in the B2 completion report.
