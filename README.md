# GreenGrub — User Management Service

Authentication, authorization, and user lifecycle management for the GreenGrub food-donation platform. Owns the `User` entity, issues JWTs, and exposes both REST (for the public API) and gRPC (for inter-service calls).

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
11. [Observability](#observability)
12. [Testing](#testing)

---

## What this service does

- Registers users (donors, recipients, admins) and authenticates them with email + password
- Issues short-lived **JWTs** that other GreenGrub services trust as proof of identity
- Stores user records in **Postgres** (Cloud SQL in production)
- Serves user lookup / verification calls over **gRPC** to sibling services (e.g. donation-service, image-service) that need to resolve a `userId` or check credentials without round-tripping to the public API

It is a **stateless** service: the same image runs in all environments; environment is selected via Spring profiles.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.5.0 |
| HTTP | Spring MVC + Spring Security |
| RPC | gRPC 1.70 via `org.springframework.grpc:spring-grpc-server-web-spring-boot-starter` |
| Persistence | Spring Data JPA + Hibernate, Postgres 16 |
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
│   ├── SecurityConfig.java             # JWT filter chain, public/protected matchers
│   ├── PasswordEncoderConfig.java      # BCryptPasswordEncoder bean
│   ├── DataInitializer.java            # @ConditionalOnProperty seed users (local only)
│   ├── CorsConfig.java
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java             # /api/v1/auth/* — public
│   └── UserController.java             # /api/v1/users/* — JWT-protected
├── service/
│   ├── AuthService.java                # signup / login / validateToken
│   ├── UserService.java                # interface
│   └── UserServiceImpl.java            # CRUD + Resilience4j wrappers
├── grpc/
│   └── UserManagementServiceGrpcImpl.java   # @GrpcService implementation
├── security/
│   ├── JwtUtil.java                    # token generate / parse / verify
│   └── JwtAuthenticationFilter.java    # OncePerRequestFilter — extracts Bearer token
├── repository/UserRepository.java      # JpaRepository<User, String>
├── entity/{User, UserRole}.java
├── dto/...                             # request / response records
├── exception/
│   ├── UserNotFoundException.java
│   ├── UserAlreadyExistsException.java
│   ├── InvalidPasswordException.java
│   ├── UserStorageException.java       # wraps transient DB failures
│   └── GlobalExceptionHandler.java     # @RestControllerAdvice — maps to HTTP
├── filter/RequestResponseLoggingFilter.java
├── aspect/LoggingAspect.java
└── mapper/UserMapper.java

src/main/resources
├── application.properties              # shared base config (profile-agnostic)
├── application-local.properties        # Postgres-in-Docker, dev resilience thresholds
└── application-k8s.yml                 # Cloud SQL, production resilience thresholds
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
- gRPC  → `localhost:9091`
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

### Layers

```
Request ──▶ JwtAuthenticationFilter ──▶ SecurityFilterChain ──▶ Controller ──▶ Service ──▶ Repository
              (extract Bearer token)      (route-based authz)
```

1. **`JwtAuthenticationFilter`** (a `OncePerRequestFilter`) reads the `Authorization: Bearer <token>` header on every request.
   - If absent or invalid → request continues unauthenticated; `SecurityFilterChain` rejects it on protected routes.
   - If valid → an `Authentication` is placed on the `SecurityContextHolder` carrying the user's id, email, and role.

2. **`SecurityConfig`** declares the route policy:
   - **Public**: `/api/v1/auth/signup`, `/api/v1/auth/login`, `/api/v1/auth/validate`, `/api/v1/auth/me`, `/api/v1/users/check-email`, `/actuator/**`, Swagger UI
   - **ADMIN-only**: `POST /api/v1/users` (create user as admin), `GET /api/v1/users/stats`
   - **Authenticated**: every other `/api/v1/users/**` endpoint
   - Sessions are `STATELESS`; CSRF is disabled (we don't use cookies for auth)

3. **`JwtUtil`** signs and verifies tokens with HS256 using `jwt.secret` (a 256-bit string). The default secret in `application.properties` is for development; production injects `JWT_SECRET` from a Kubernetes secret.

### Why JWTs and not sessions

This service is one node in a microservice cluster. Sibling services (donation-service, notification-service, image-service) need to identify the caller without sharing a session store. Each service holds the same JWT secret and verifies tokens independently — no central session, no DB hop on every request.

### Password handling

- **`PasswordEncoderConfig`** exposes a `BCryptPasswordEncoder` bean (default strength 10).
- Plaintext passwords never leave the controller layer — `AuthService.signup` and `UserServiceImpl.createUser` hash before persisting.
- `verifyPassword` and the gRPC `verifyCredentials` use `passwordEncoder.matches` for constant-time comparison.

### What's deliberately not here

- **No refresh tokens** — short-lived JWTs (24h default) are all this stack needs right now. A refresh-token flow can be added without changing the public contract.
- **No OAuth / OIDC** — out of scope. If we ever add Google / Apple login it'll be a separate `AuthProvider` strategy invoked from `AuthService`.

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

The image-service splits breakers per backend (Mongo vs Firestore). Here we have a single relational store across both profiles, so a single `userBreaker` is enough. If a second backend is ever added (say, a Redis cache), we'd add a second instance rather than overloading this one.

---

## Exception handling

### The exceptions

| Exception | Thrown when | HTTP | gRPC |
| --- | --- | --- | --- |
| `UserNotFoundException` | Lookup by id / email / token returns empty | 404 | `NOT_FOUND` |
| `UserAlreadyExistsException` | Signup or update collides on email | 409 | `ALREADY_EXISTS` |
| `InvalidPasswordException` | Old password mismatch, weak new password, deactivated account on login | 400 | `INVALID_ARGUMENT` |
| `UserStorageException` | DB call failed after all retries (or threw a non-business exception inside a wrapper) | 503 | `UNAVAILABLE` |
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

### `/api/v1/users` — JWT required

| Method | Path | Auth |
| --- | --- | --- |
| POST   | `/` | ADMIN |
| GET    | `/{userId}` | any |
| GET    | `/email/{email}` | any |
| GET    | `/` | any |
| GET    | `/search?name=...` | any |
| GET    | `/donors` / `/recipients` | any |
| GET    | `/check-email?email=...` | public |
| GET    | `/stats` | ADMIN |
| PUT    | `/{userId}` | any |
| PUT    | `/{userId}/password` | any |
| PUT    | `/{userId}/reset-password` | any |
| PUT    | `/{userId}/activate` / `/deactivate` | any |
| DELETE | `/{userId}` | any (soft delete) |
| DELETE | `/{userId}/permanent` | any (hard delete) |

> "any" above means *any authenticated user*. Stricter per-row authorization (a user can only edit their own profile) is enforced inside the service layer, not in `SecurityConfig`.

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

Same exception → status mapping as the REST layer (see [Exception handling](#exception-handling)).

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
- **Schema changes** must be expressed as explicit migrations (`ddl-auto=validate` in production will refuse to start otherwise). Use `update` only locally.
- **Every Maven invocation needs `-s settings.xml`** to resolve `proto-contracts` from GitHub Packages.
