# GreenGrub — User Management Service

User lifecycle, credentials, and JWT issuance for the GreenGrub food-donation platform. Owns the `User` entity and exposes both REST (for the public API, fronted by the GCP API Gateway) and gRPC (for inter-service calls). **Authentication and authorization at the HTTP edge are enforced by the API Gateway, not by this service.**

---

## Table of contents

1. [What this service does](#what-this-service-does)
2. [Tech stack](#tech-stack)
3. [Project layout](#project-layout)
4. [Running locally](#running-locally)
5. [Profiles](#profiles)
6. [Security model](#security-model)
7. [Resilience patterns](#resilience-patterns)
8. [Exception handling](#exception-handling)
9. [REST API](#rest-api)
10. [gRPC API](#grpc-api)
11. [Profile image handling](#profile-image-handling)
12. [Donations lookup](#donations-lookup)
13. [Database migrations](#database-migrations)
14. [Observability](#observability)
15. [Testing](#testing)

---

## What this service does

- Registers users (donors, recipients, admins) and verifies their credentials with email + password
- Mints **JWTs** at `/signup` and `/login` for the API Gateway to validate on subsequent calls
- Stores user records in **Postgres** (Cloud SQL in production)
- Serves user lookup / verification calls over **gRPC** to sibling services (e.g. donation-service, image-service) that need to resolve a `userId` or check credentials without round-tripping to the public API

It is a **stateless** service: the same image runs in all environments; environment is selected via Spring profiles.

> **Trust boundary**: in production this service sits behind a GCP API Gateway. The gateway terminates JWT validation and authorization, then forwards requests with `X-User-Id` / `X-User-Email` / `X-User-Role` headers. The service trusts those headers because it trusts the gateway. **Never expose this service directly to the public internet** — any client could otherwise spoof those headers.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.5.0 |
| HTTP | Spring MVC + Spring Security |
| RPC | gRPC 1.70 via `org.springframework.grpc:spring-grpc-server-web-spring-boot-starter` (server) and `spring-grpc-client-spring-boot-starter` 0.10.0 (client of image-service and donation-service) |
| Persistence | Spring Data JPA + Hibernate, Postgres 16 |
| Migrations | Flyway 10 (`flyway-core` + `flyway-database-postgresql`) |
| Cloud DB | Google Cloud SQL via `spring-cloud-gcp-starter-sql-postgresql` (k8s profile) |
| Auth | `jjwt` 0.12.6 (HS256), BCrypt password hashing |
| Resilience | Resilience4j 2.2.0 (Retry + CircuitBreaker), Spring AOP |
| API docs | springdoc-openapi 2.7.0 (Swagger UI at `/swagger-ui.html`) |
| Build | Maven (`./mvnw clean install -s settings.xml`) |

`settings.xml` carries the credentials for the GitHub Packages repo where the shared `proto-contracts` artifact lives — every Maven invocation must pass `-s settings.xml`.

---

## Project layout

```
src/main/java/com/greengrub/usermanagement
├── UserManagementApplication.java      # @SpringBootApplication entrypoint
├── config/
│   ├── SecurityConfig.java             # permitAll filter chain (gateway terminates auth)
│   ├── PasswordEncoderConfig.java      # BCryptPasswordEncoder bean
│   ├── DataInitializer.java            # @ConditionalOnProperty seed users (local only)
│   ├── CorsConfig.java                 # CorsConfigurationSource bound to app.cors.allowed-origins
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java             # /api/v1/auth/* — JWT issuance + introspection
│   └── UserController.java             # /api/v1/users/* — open (gateway gates upstream)
├── service/
│   ├── AuthService.java                # signup / login / validateToken
│   ├── UserService.java                # interface
│   └── UserServiceImpl.java            # CRUD + Resilience4j wrappers
├── grpc/
│   └── UserManagementServiceGrpcImpl.java   # @GrpcService implementation
├── client/
│   ├── ImageServiceClient.java             # gRPC client of image-service (Resilience4j-wrapped)
│   └── DonationServiceClient.java          # gRPC client of donation-service (Resilience4j-wrapped)
├── security/
│   ├── JwtUtil.java                    # token generate / parse / verify (issuance only)
│   └── GatewayHeadersFilter.java       # copies X-User-* into request attributes
├── repository/UserRepository.java      # JpaRepository<User, String>
├── entity/{User, UserRole}.java
├── dto/...                             # request / response records
├── exception/
│   ├── UserNotFoundException.java
│   ├── UserAlreadyExistsException.java
│   ├── InvalidPasswordException.java
│   ├── UserStorageException.java       # wraps transient DB failures
│   ├── ImageServiceException.java      # wraps transient image-service failures
│   ├── DonationServiceException.java   # wraps transient donation-service failures
│   └── GlobalExceptionHandler.java     # @RestControllerAdvice — maps to HTTP
├── filter/RequestResponseLoggingFilter.java
├── aspect/LoggingAspect.java
└── mapper/UserMapper.java

src/main/resources
├── application.properties              # shared base config (profile-agnostic)
├── application-local.properties        # Postgres-in-Docker, dev resilience thresholds
├── application-k8s.yml                 # Cloud SQL, production resilience thresholds
└── db/migration/
    ├── V1__init_users.sql              # Flyway baseline of the users table
    └── V2__add_image_id_to_users.sql   # adds image_id pointer to image-service
```

---

## Running locally

```bash
# 1. Start Postgres (the docker-compose file also bundles Kafka/Eureka but they
#    aren't needed for this service — start postgres only)
docker compose up -d postgres

# 2. Build and run (local profile is the default)
./mvnw clean install -s settings.xml
./mvnw spring-boot:run -s settings.xml
```

The service is now reachable at:
- REST  → `http://localhost:8082`
- gRPC  → `localhost:9092`
- Swagger UI → `http://localhost:8082/swagger-ui.html`
- Actuator  → `http://localhost:8082/actuator`

`DataInitializer` seeds a small set of sample users on first run (only in `local` — disabled in `k8s`).

---

## Profiles

The service intentionally has **only two profiles**. Anything else is environment variables.

### `local` (default)

`application-local.properties` — for developer machines and the Docker compose stack.

- Datasource: `jdbc:postgresql://localhost:5432/greengrub_usermanagement`
- HikariCP: max pool 10, min idle 5
- `spring.jpa.hibernate.ddl-auto=update` — Hibernate manages the schema
- `app.data.init.enabled=true` — seed sample users
- Logging: `DEBUG` for `com.greengrub.usermanagement` and Hibernate SQL
- Resilience: lighter thresholds (see [Resilience patterns](#resilience-patterns))
- Cloud SQL autoconfig **excluded** so Spring doesn't try to dial GCP

### `k8s`

`application-k8s.yml` — for the GKE deployment.

- Datasource: Cloud SQL Postgres via `spring.cloud.gcp.sql.instance-connection-name`
  - No public IP needed — the Cloud SQL Auth Proxy / private IP is used
- HikariCP: max pool 20, min idle 10
- `spring.jpa.hibernate.ddl-auto=validate` — schema migrations are explicit, never auto
- `app.data.init.enabled=false` — never seed in production
- Logging: `INFO`
- Resilience: stricter thresholds (50% failure rate, 30s open state)
- Required env vars: `CLOUD_SQL_INSTANCE`, `CLOUD_SQL_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`

Selected via `SPRING_PROFILES_ACTIVE=k8s`.

> Eureka is **not** used in either profile. In local we don't need service discovery; in k8s the cluster's native DNS resolves sibling services by name.

---

## Security model

This service is **gateway-fronted**. The GCP API Gateway is the single enforcement point for authentication and authorization on the public edge — it validates the JWT, applies route-level rules, and only then forwards the request. By the time traffic reaches us, the caller has already been vetted.

### Request flow

```
Browser ──▶ GCP API Gateway ──▶ GatewayHeadersFilter ──▶ SecurityFilterChain ──▶ Controller ──▶ Service ──▶ Repository
            (validate JWT,      (copy X-User-* headers    (permitAll, stateless,
             apply authz)        into request attrs)       CORS-aware)
```

1. **`SecurityConfig`** installs a `SecurityFilterChain` that:
   - permits all requests (`anyRequest().permitAll()`) — the gateway has already decided
   - is `STATELESS` (no HTTP session) and CSRF-disabled (no cookies)
   - delegates CORS to the `CorsConfigurationSource` bean (see below)
   - explicitly disables HTTP Basic and form login so the security starter doesn't accidentally lock anything down

2. **`GatewayHeadersFilter`** (a `OncePerRequestFilter`) reads `X-User-Id`, `X-User-Email`, `X-User-Role` from the request and copies them into request attributes. Controllers read them via `@RequestAttribute`. The filter performs **no signature verification and makes no decisions** — that's the gateway's job.

3. **`AuthController`** still exists and still mints JWTs:
   - `POST /api/v1/auth/signup` — create a user, return `{ token, user }` so the caller has a JWT immediately
   - `POST /api/v1/auth/login` — verify credentials, return `{ token, user }`
   - `GET /api/v1/auth/validate` — decode a token and return the user (useful for the gateway and for debugging)
   - `GET /api/v1/auth/me` — return identity from the gateway-injected attributes

4. **`JwtUtil`** signs tokens with HS256 using `jwt.secret`. The gateway holds the same secret (or a public key, if HS256 is later swapped for RS256) and verifies tokens upstream. The default secret in `application.properties` is for local development only; production injects `JWT_SECRET` from a Kubernetes secret.

### Why JWT issuance still lives here

The gateway needs *something* to mint a token after a user logs in, and the only place that owns the credential store and BCrypt-hashed passwords is this service. Splitting issuance from validation is the standard pattern: the credential authority issues, the policy enforcement point validates.

### CORS

Profile-driven, **never `*` with credentials**:

| Profile | `app.cors.allowed-origins` |
| --- | --- |
| `local` | `http://localhost:3000,http://localhost:5173` (CRA + Vite defaults) |
| `k8s` | `${CORS_ALLOWED_ORIGINS:}` — set in the cluster ConfigMap/Secret. Empty default → no `Access-Control-Allow-Origin` header is emitted |

`CorsConfig` reads the property as a `List<String>`, registers it on `/api/**`, and enables credentials only when the list is non-empty. In production the React app calls the gateway, not this service directly, so cross-origin traffic shouldn't be hitting us in the first place.

### Password handling

- **`PasswordEncoderConfig`** exposes a `BCryptPasswordEncoder` bean (default strength 10).
- Plaintext passwords never leave the controller layer — `AuthService.signup` and `UserServiceImpl.createUser` hash before persisting.
- `verifyPassword` and the gRPC `verifyCredentials` use `passwordEncoder.matches` for constant-time comparison.

### Local testing without a gateway

For endpoints that read identity attributes (e.g. `/api/v1/auth/me`), simulate the gateway by setting the headers yourself:

```bash
curl -i http://localhost:8082/api/v1/auth/me \
  -H "X-User-Id: 1" \
  -H "X-User-Email: alice@example.com" \
  -H "X-User-Role: DONOR"
```

Without those headers, `/me` returns its existing 403 path. Every other endpoint is open in `local` — no auth required.

### What's deliberately not here

- **No JWT validation in this service.** That's the gateway's job. Don't reintroduce a `JwtAuthenticationFilter` here without first deleting the gateway-headers contract.
- **No refresh tokens.** Tokens are short-lived (24h default); the gateway can manage refresh if needed.
- **No OAuth / OIDC.** Out of scope. The gateway can integrate Google / Apple sign-in independently and still call our `/signup` to materialize a user record.

---

## Resilience patterns

The database is the only real failure surface in this service. Resilience4j wraps every repository call so that **transient** failures (connection blips, lock timeouts, deadlocks) get retried before the request is failed, and a **persistent** outage opens the circuit so we shed load instead of piling up threads on a dead Postgres.

### Where the annotations live

Annotations sit on `protected` wrapper methods inside `UserServiceImpl` and `AuthService`. The public service methods orchestrate business logic and call these wrappers:

```java
public UserResponse getUserById(String userId) {        // public — no annotation
    return userMapper.toResponse(findUserByIdOrThrow(userId));
}

@Retry(name = "userRetry")
@CircuitBreaker(name = "userBreaker")
protected User findUserByIdOrThrow(String userId) {     // wrapper — Spring AOP proxied
    try {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    } catch (UserNotFoundException e) {
        throw e;                                        // business → propagate, no retry
    } catch (Exception e) {
        throw new UserStorageException("...", e);       // transient → retryable
    }
}
```

**Why a wrapper instead of annotating the public method?** Spring AOP only proxies external calls. If `getUserById` were annotated and another method in the same class called `this.getUserById(...)`, the annotation would be bypassed because self-invocation skips the proxy. Putting the annotations on a method that's *only* called from elsewhere in the class avoids that footgun while keeping the public surface clean.

### Retry — `userRetry`

| Property | Local | k8s |
| --- | --- | --- |
| `max-attempts` | 3 | 3 |
| `wait-duration` | 500ms | 500ms |
| `enable-exponential-backoff` | true | true |
| `exponential-backoff-multiplier` | 2 | 2 |
| `retry-exceptions` | `UserStorageException` | `UserStorageException` |

The retry list contains **only** `UserStorageException`. Business exceptions (`UserNotFoundException`, `UserAlreadyExistsException`, `InvalidPasswordException`) are caught explicitly and re-thrown so they reach the caller on the first attempt — no point retrying "user already exists".

Backoff sequence on failure: 500ms → 1s → 2s, then give up.

### Circuit breaker — `userBreaker`

| Property | Local | k8s |
| --- | --- | --- |
| `failure-rate-threshold` | 60% | 50% |
| `sliding-window-size` | 5 | 10 |
| `wait-duration-in-open-state` | 10s | 30s |
| `permitted-number-of-calls-in-half-open-state` | 2 | 3 |
| `register-health-indicator` | true | true |

When the circuit opens, the next call throws `CallNotPermittedException` immediately rather than queueing — both the REST handler and the gRPC handler map this to a 503 / `UNAVAILABLE` so callers can back off.

The breaker's state is exposed at `/actuator/health` (look for `circuitBreakers.userBreaker.state`).

### Why one breaker, not many

Postgres is the only persistent store, so a single `userBreaker` covers every repository call. Each remote dependency gets its **own** breaker so failures stay isolated:

| Breaker | Protects |
| --- | --- |
| `userBreaker` | Postgres (every repository call) |
| `imageServiceBreaker` | image-service gRPC (profile image upload + URL inflate) |
| `donationServiceBreaker` | donation-service gRPC (donations-by-user lookup) |

A dead image-service does not trip the user or donation breakers, and vice versa. See [Profile image handling](#profile-image-handling) and [Donations lookup](#donations-lookup).

### Image-service breaker — `imageServiceRetry` + `imageServiceBreaker`

Wraps every gRPC call into image-service so a sick image backend can't cascade into user-service.

| Property | Local | k8s |
| --- | --- | --- |
| Retry `max-attempts` | 2 | 2 |
| Retry `wait-duration` | 300ms | 300ms |
| Retry `retry-exceptions` | `ImageServiceException` | `ImageServiceException` |
| CB `failure-rate-threshold` | 60% | 50% |
| CB `sliding-window-size` | 5 | 10 |
| CB `wait-duration-in-open-state` | 10s | 30s |
| CB `permitted-number-of-calls-in-half-open-state` | 2 | 3 |

**Uploads** are wrapped — failures throw `ImageServiceException`, which the retry policy targets and the breaker counts. **Reads** in `ImageServiceClient.getById` deliberately degrade silently: gRPC failures are caught and returned as `Optional.empty()` so a missing image URL never breaks a user GET. The breaker still protects upload paths.

### Donation-service breaker — `donationServiceRetry` + `donationServiceBreaker`

Wraps every gRPC call into donation-service. Same shape as the image breaker; failures surface as `DonationServiceException` (→ 503).

| Property | Local | k8s |
| --- | --- | --- |
| Retry `max-attempts` | 2 | 2 |
| Retry `wait-duration` | 300ms | 300ms |
| Retry `retry-exceptions` | `DonationServiceException` | `DonationServiceException` |
| CB `failure-rate-threshold` | 60% | 50% |
| CB `sliding-window-size` | 5 | 10 |
| CB `wait-duration-in-open-state` | 10s | 30s |
| CB `permitted-number-of-calls-in-half-open-state` | 2 | 3 |

Unlike the image-service read inflate, donation reads do **not** degrade silently — donation-service is the source of truth for the list, and an empty list when it's down would be a lie. `DonationServiceClient.getDonationsByUserId` throws on failure so the caller can decide whether to retry.

---

## Exception handling

### The exceptions

| Exception | Thrown when | HTTP | gRPC |
| --- | --- | --- | --- |
| `UserNotFoundException` | Lookup by id / email / token returns empty | 404 | `NOT_FOUND` |
| `UserAlreadyExistsException` | Signup or update collides on email | 409 | `ALREADY_EXISTS` |
| `InvalidPasswordException` | Old password mismatch, weak new password, deactivated account on login | 400 | `INVALID_ARGUMENT` |
| `UserStorageException` | DB call failed after all retries (or threw a non-business exception inside a wrapper) | 503 | `UNAVAILABLE` |
| `ImageServiceException` | image-service gRPC upload failed after retries | 503 | `UNAVAILABLE` |
| `DonationServiceException` | donation-service gRPC call failed after retries | 503 | `UNAVAILABLE` |
| `CallNotPermittedException` (Resilience4j) | Circuit breaker is OPEN | 503 | `UNAVAILABLE` |
| `MethodArgumentNotValidException` | `@Valid` request DTO failed bean-validation | 400 with field errors | n/a |
| `MethodArgumentTypeMismatchException` | Path/query param wrong type | 400 | n/a |
| `IllegalArgumentException` | Defensive throws inside services / gRPC layer | 400 | `INVALID_ARGUMENT` |
| Anything else | Catch-all in `GlobalExceptionHandler` | 500 | `INTERNAL` |

### The two layers

**REST: `GlobalExceptionHandler`** is a `@RestControllerAdvice` that converts each exception into a typed `ErrorResponse` JSON payload (status, error, message, path, timestamp, optional `validationErrors` map). Controllers themselves never catch anything — they let the exception bubble.

**gRPC: `mapException` helper** in `UserManagementServiceGrpcImpl` does the same translation for the gRPC surface. Each service method catches `Exception` once at its boundary and delegates to `mapException`, which returns the right `Status` based on the exception class. This keeps the per-method catch blocks one line long and avoids `Status.INTERNAL` for what should be a 404 or 503.

### Why `UserStorageException` exists

Resilience4j's `retry-exceptions` list is a positive filter — only listed exception types trigger a retry. If we retried on plain `RuntimeException`, we'd retry `UserNotFoundException` (pointless) and `UserAlreadyExistsException` (pointless and slow). Wrapping transient infra failures in a dedicated type lets the retry policy be precise: retry **only** what's actually retryable.

### Verifying the mapping

```bash
# Stop Postgres while the service is running, then:
curl -i http://localhost:8082/api/v1/users/some-id  -H "Authorization: Bearer <token>"
# 1st request: ~3.5s (3 retries with backoff) → 503 with body { "error": "Service Unavailable", ... }
# After 5 such failures: circuit opens → next request fails fast (~1ms) with the same 503
# 10s later (local profile) the breaker enters HALF_OPEN and probes again
```

---

## REST API

Full reference is at **`/swagger-ui.html`** when the service is running. Quick map:

### `/api/v1/auth` — public

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/signup` | Create user, return JWT |
| POST | `/login` | Verify credentials, return JWT |
| GET  | `/validate` | Check a token, return the user |
| GET  | `/me` | Same as `/validate` against the current header |

### `/api/v1/users` — gateway-protected

All routes are `permitAll` in this service; the gateway gates them upstream. The "Gateway policy" column is the policy the gateway is expected to enforce, not anything checked here.

| Method | Path | Gateway policy |
| --- | --- | --- |
| POST   | `/` | ADMIN |
| GET    | `/{userId}` | authenticated |
| GET    | `/email/{email}` | authenticated |
| GET    | `/` | authenticated |
| GET    | `/search?name=...` | authenticated |
| GET    | `/donors` / `/recipients` | authenticated |
| GET    | `/check-email?email=...` | public |
| GET    | `/stats` | ADMIN |
| PUT    | `/{userId}` | authenticated |
| PUT    | `/{userId}/password` | authenticated |
| PUT    | `/{userId}/reset-password` | authenticated |
| PUT    | `/{userId}/activate` / `/deactivate` | authenticated |
| DELETE | `/{userId}` | authenticated (soft delete) |
| DELETE | `/{userId}/permanent` | authenticated (hard delete) |
| POST   | `/{userId}/image` | authenticated (multipart, ≤5 MB, jpeg/png/webp) |
| DELETE | `/{userId}/image` | authenticated (clears imageId pointer) |
| GET    | `/{userId}/donations?page=&pageSize=` | authenticated (defaults: page=0, pageSize=10, max pageSize=100) |

> Stricter per-row authorization (a user can only edit their own profile) is enforced inside the service layer using the identity attributes set by `GatewayHeadersFilter`, not in `SecurityConfig`.

---

## gRPC API

Definitions live in the shared `com.greengrub:proto-contracts` artifact (GitHub Packages). The generated stubs are pulled in at build time — there is no `src/main/proto` directory in this repo.

Implemented RPCs in `UserManagementServiceGrpcImpl`:

- `getUser(UserByIdRequest)` → `UserResponse`
- `getUserByEmail(GetUserByEmailRequest)` → `UserResponse`
- `createUser(CreateUserRequest)` → `UserResponse`
- `updateUser(UpdateUserRequest)` → `UserResponse`
- `deleteUser(UserByIdRequest)` → `DeleteUserResponse`
- `activateUser` / `deactivateUser(UserByIdRequest)` → `UserResponse`
- `verifyCredentials(VerifyCredentialsRequest)` → `VerifyCredentialsResponse` (deliberately returns `valid=false` with no detail on failure — does not reveal whether the email exists)
- `listUsers(ListUsersRequest)` → `ListUsersResponse` (in-memory pagination)

`UserResponse` payloads include the proto `Image` field when `user.imageId != null`. The mapper calls `ImageServiceClient.getById` to resolve the URL — a failing image-service does not break the user response (the field is just left unset).

Same exception → status mapping as the REST layer (see [Exception handling](#exception-handling)).

---

## Profile image handling

User-service is the canonical source of the user record but **not** of the image bytes. Images live in the separate **image-service** (Cloud Firestore + Cloud Storage in production, MongoDB locally). User records carry only an opaque `imageId` pointer; the bytes / URL are resolved on demand.

### Wire diagram

```
Browser ──multipart POST /users/{id}/image──▶ user-service ──gRPC UploadImages──▶ image-service
                                                  │
                                                  ▼
                                              Postgres (users.image_id = <new id>)

Browser ──GET /users/{id}──▶ user-service ──gRPC GetImageByImageId──▶ image-service
                                  │                       (best-effort; failures degrade silently)
                                  ▼
                              UserResponse { imageId, imageUrl }
```

### Upload flow (`POST /api/v1/users/{userId}/image`)

1. Controller validates the multipart payload **before** any RPC: non-empty, ≤ 5 MB, content-type in `{image/jpeg, image/png, image/webp}`. Garbage input is rejected with 400 — image-service never sees it.
2. `UserService.uploadProfileImage` loads the user row (Postgres breaker), then calls `ImageServiceClient.uploadProfileImage` (image-service breaker).
3. On success, the new `imageId` is persisted on the user row and the response is returned with `imageId` + a freshly resolved `imageUrl`.
4. If image-service is down, step 2 throws `ImageServiceException` → 503; the user row is **not** modified, so the system never sits on a half-set `imageId` pointing at nothing.

### Read inflate

Single-user reads (`GET /users/{id}`, `GET /users/email/{email}`, gRPC `getUser` / `getUserByEmail`) call `ImageServiceClient.getById` and populate `imageUrl` from the response.

List endpoints (`getAllUsers`, `searchByName`, `getDonors`, `getRecipients`, gRPC `listUsers`) deliberately **do not** inflate — they return `imageId` only. The frontend can resolve URLs lazily, or a batch RPC can be added later. The N×RPC fan-out a per-row inflate would cause is not worth it.

### gRPC client configuration

Spring-managed channel via `org.springframework.grpc:spring-grpc-client-spring-boot-starter`. Address is profile-driven, plaintext on the wire (mTLS is the service mesh's job, not the app's):

| Profile | `spring.grpc.client.channels.image-service.address` |
| --- | --- |
| `local` | `static://${IMAGE_SERVICE_HOST:localhost}:${IMAGE_SERVICE_PORT:9095}` |
| `k8s` | `static://${IMAGE_SERVICE_HOST:image-service}:${IMAGE_SERVICE_PORT:9095}` |

Deadlines are enforced on every call: 5s for upload, 2s for read. Image-service is not allowed to hang user requests indefinitely.

### Delete

`DELETE /api/v1/users/{userId}/image` clears the `imageId` pointer on the user row. It does **not** call image-service to delete the underlying bytes — image lifecycle is image-service's responsibility (e.g. its own GC pass cleans up orphans). If the user later uploads a new image, the old one is left orphaned in image-service until it sweeps.

---

## Donations lookup

Donations are owned by a separate **donation-service**. User-service does not store donation rows — it forwards the lookup over gRPC and returns a JSON-friendly view to the caller. This keeps the donation schema out of user-service's database while still letting clients hit a single endpoint to list a user's donations.

### Wire diagram

```
Browser ──GET /users/{id}/donations?page=&pageSize=──▶ user-service
                                                          │
                                                          ├── findUserByIdOrThrow (Postgres breaker)
                                                          │     └── 404 if user does not exist
                                                          │
                                                          └── gRPC GetDonationsByUserId ──▶ donation-service
                                                                                   │
                                                                                   ▼
                                                                       DonationListResponse
                                                                                   │
                                                                                   ▼
                                                                  DonationListView (JSON)
```

### Endpoint — `GET /api/v1/users/{userId}/donations`

Query params:

| Param | Default | Bounds | Notes |
| --- | --- | --- | --- |
| `page` | `0` | `>= 0` | Zero-indexed |
| `pageSize` | `10` | `1..100` | Cap is enforced at the controller (`MAX_DONATIONS_PAGE_SIZE = 100`) to prevent runaway list pulls |

Both bounds are validated **before** the gRPC call — bad input returns 400, donation-service is never dialled.

The endpoint also validates that the user exists (`findUserByIdOrThrow`) before delegating, so an unknown `userId` returns 404 instead of a successful empty list.

### Why fail-fast, not degrade-silent

Image reads degrade silently (a missing image URL is cosmetic). Donation reads do **not**: donation-service is the source of truth, and an empty list when it's actually down would be a lie that the frontend would render as "this user has no donations". `DonationServiceClient.getDonationsByUserId` throws `DonationServiceException` on failure → 503 / `UNAVAILABLE`, so callers can back off and retry instead of caching a wrong answer.

### Response shape

The proto `DonationListResponse` is mapped to a JSON-friendly `DonationListView`:

```json
{
  "donations": [
    {
      "id": "...",
      "donationName": "...",
      "pickUpAddress": "...",
      "pickUpTime": "...",
      "estimatedQuantity": { "amount": 5.0, "unit": "KG" },
      "foodItemsId": ["..."],
      "status": "OPEN",
      "creationDate": "...",
      "updateDate": "..."
    }
  ],
  "totalCount": 42,
  "page": 0,
  "pageSize": 10
}
```

Proto types are deliberately not serialized to HTTP — protobuf-generated classes leak default-valued primitives, render enums as ints, and pin the wire shape to our public API. The `DonationListView` DTO insulates clients from those details.

### gRPC client configuration

| Profile | `spring.grpc.client.channels.donation-service.address` |
| --- | --- |
| `local` | `static://${DONATION_SERVICE_HOST:localhost}:${DONATION_SERVICE_PORT:9096}` |
| `k8s` | `static://${DONATION_SERVICE_HOST:donation-service}:${DONATION_SERVICE_PORT:9096}` |

Plaintext on the wire (mTLS is the service mesh's job). A 3-second deadline is enforced on every call — donation-service is not allowed to hang user requests.

---

## Database migrations

Schema is **Flyway-managed**. Migrations live in `src/main/resources/db/migration/` and run on application startup.

| File | Purpose |
| --- | --- |
| `V1__init_users.sql` | Baseline of the `users` table that Hibernate previously generated from the `User` entity (idempotent — uses `IF NOT EXISTS` so existing local DBs don't fail). |
| `V2__add_image_id_to_users.sql` | Adds the `image_id VARCHAR(36)` column referenced by the [profile image handling](#profile-image-handling) flow. |

Configuration:

| Property | Value | Why |
| --- | --- | --- |
| `spring.flyway.enabled` | `true` | Flyway is the schema authority. |
| `spring.flyway.baseline-on-migrate` | `true` | Existing local DBs created by the old `ddl-auto=update` get baselined as V1 instead of failing. |
| `spring.jpa.hibernate.ddl-auto` (local) | `update` | Allows fast iteration on entity changes without writing a migration. New columns are still expected to be migrated *eventually* — Flyway is the source of truth in k8s. |
| `spring.jpa.hibernate.ddl-auto` (k8s) | `validate` | Production refuses to start if the schema doesn't match the entities — Flyway must have run first. |

### Adding a new migration

1. Create `VN__short_description.sql` in `src/main/resources/db/migration/` with the next version number.
2. Update the corresponding entity / DTO / mapper in the same PR.
3. Run locally to verify Flyway applies it cleanly: `mvn spring-boot:run -s settings.xml`.
4. Never edit a migration once it's been merged — write a new `V(N+1)__…` instead. Flyway tracks checksums in `flyway_schema_history` and will refuse to start if a past migration changes.

---

## Observability

Actuator endpoints are exposed and **public** (allowed by `SecurityConfig`):

- `GET /actuator/health` — overall + circuit-breaker state
- `GET /actuator/info`
- `GET /actuator/metrics` and `GET /actuator/prometheus` — for Prometheus scraping
- `GET /actuator/loggers` — runtime log-level changes

Application logs:
- `RequestResponseLoggingFilter` logs every HTTP request/response at INFO
- `LoggingAspect` adds method-level entry/exit logging for service classes
- Local profile turns on `DEBUG` for the application package and Hibernate SQL

---

## Testing

```bash
./mvnw test -s settings.xml
```

`application-test.properties` is a self-contained test profile. Spring Security's `spring-security-test` module is on the classpath for `@WithMockUser` and friends.

For a hands-on resilience walkthrough (kill Postgres mid-request, watch retries and the breaker open) see the manual scenarios in `src/test/...` or run the curl sequence under [Verifying the mapping](#verifying-the-mapping).

---

## Conventions for new contributors

- **Don't catch exceptions in controllers.** Let them bubble to `GlobalExceptionHandler`. Adding a catch in a controller almost always means a missing handler in the advice — fix it there.
- **Don't call the repository from a public service method.** Route through one of the resilience-wrapped `protected` helpers (or add a new one). A direct repo call bypasses retry and the circuit breaker.
- **When you add a new business exception**, register it in both `GlobalExceptionHandler` and the gRPC `mapException` helper in the same PR. They are intentionally symmetric.
- **When you add a new external service call**, wrap it in `@Retry` + `@CircuitBreaker` with its **own** instance (don't share `userBreaker`), classify failures into a typed exception (e.g. `ImageServiceException`), and register that exception in both the REST and gRPC error mappers. The image-service integration is the reference example — see `ImageServiceClient`.
- **Don't reintroduce HTTP authentication here.** Authn/authz is the gateway's contract. If you need a new identity attribute, extend `GatewayHeadersFilter` and update the gateway to inject the matching header — don't grow a JWT filter back in this service.
- **Schema changes** must be expressed as a new Flyway migration in `src/main/resources/db/migration/` (`V(N+1)__short_description.sql`) — never edit a past migration. `ddl-auto=validate` in k8s will refuse to start otherwise. Locally, `ddl-auto=update` lets entity tweaks land without a migration, but the migration is still required before merge.
- **Every Maven invocation needs `-s settings.xml`** to resolve `proto-contracts` from GitHub Packages.
