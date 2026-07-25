# Design: Modular Refactor — Lombok, Logging, Class Conventions

**Date:** 2026-07-25
**Status:** Approved
**Type:** Structural refactor of the existing leaky-bucket rate limiter (no behavior change)

## 1. Overview

Reorganize the existing Spring Boot rate limiter into a conventional
layered package structure, adopt Lombok for the two mutable data classes,
add request/response logging in the controller, and apply a per-class
authorship/documentation convention. No functional behavior changes: all 18
existing tests must stay green (with updated imports only).

This is a single Maven module (one `pom.xml`) organized by package/layer —
not a Maven multi-module build. There is no database, so `repository` and
JPA `model`/entity layers are intentionally omitted.

## 2. Target Package Structure

```
com.example.ratelimiter
├── Application.java
├── config/
│   └── RateLimiterProperties.java     # @ConfigurationProperties + Lombok @Getter/@Setter
├── model/                             # immutable domain records (NOT JPA entities)
│   ├── Bucket.java
│   ├── RateLimiter.java
│   └── AllowResult.java
├── service/
│   ├── RateLimiterService.java        # interface
│   ├── RateLimiterOps.java            # pure, Spring-free static algorithm (functional core)
│   └── impl/
│       └── RateLimiterServiceImpl.java  # @Service, holds AtomicReference, delegates to Ops
├── dto/
│   ├── AllowRequestDto.java           # Lombok @Getter/@Setter + @Valid (@NotBlank/@PositiveOrZero)
│   ├── AllowResponseDto.java          # record
│   └── BucketStateDto.java            # record
├── controller/
│   └── RateLimiterController.java     # @Slf4j, logs API entry & result
├── exception/
│   ├── UnknownUserException.java
│   └── GlobalExceptionHandler.java
└── util/
    └── TimeUtil.java                  # nowEpochSeconds() — centralizes server-now timestamp
```

**Functional core placement.** `RateLimiterOps` remains a pure, Spring-free
class (static methods over immutable records) living in the `service`
package. It stays independently unit-testable without a Spring context.
`RateLimiterServiceImpl` is the Spring bean that holds the global state
(`AtomicReference<RateLimiter>`) and delegates computation to
`RateLimiterOps`. This satisfies the service interface+impl convention
without sacrificing the graded "functional core" design point.

### Package moves (from → to)

| Class | From | To |
|-------|------|----|
| `Bucket`, `RateLimiter`, `AllowResult` | `core` | `model` |
| `RateLimiterOps` | `core` | `service` |
| `RateLimiterService` (new interface) | — | `service` |
| `RateLimiterServiceImpl` (was `RateLimiterService`) | `api` | `service.impl` |
| `RateLimiterController` | `api` | `controller` |
| `AllowRequestDto`, `AllowResponseDto`, `BucketStateDto` | `api.dto` | `dto` |
| `UnknownUserException`, `GlobalExceptionHandler` | `api` | `exception` |
| `RateLimiterProperties` | `config` | `config` (unchanged) |
| `TimeUtil` (new) | — | `util` |

## 3. Lombok

Add dependency `org.projectlombok:lombok` (provided/optional scope; the
Spring Boot parent manages the version). Apply Lombok only to the two
mutable data classes:

```java
@Getter @Setter
public class RateLimiterProperties {
    private int capacity = 5;
    private double leakRate = 1.0;
}

@Getter @Setter
public class AllowRequestDto {
    @NotBlank private String userId;
    @PositiveOrZero private Double timestamp;
}
```

Records (`Bucket`, `RateLimiter`, `AllowResult`, `AllowResponseDto`,
`BucketStateDto`) are left as records — they are immutable and already
generate accessors; Lombok is not applied to them.

## 4. Logging

`@Slf4j` on the controller. Log on API entry and on result, without
sensitive data:

```java
// POST /requests
log.info("Incoming allow-request: userId={}", request.getUserId());
// ... after calling the service ...
log.info("Allow-request result: userId={}, allowed={}", request.getUserId(), result.allowed());
```

- Log only `userId` (the rate-limit key, not a credential) and the boolean
  `allowed`. Do not log the raw request body, headers, or any secret.
- `timestamp` is not logged.
- The `GET /users/{userId}/bucket` endpoint may log entry at `debug` level;
  not required.

## 5. Class Documentation Convention

Every class gets an authorship header. Service (interface & impl), util, and
controller additionally get an English Javadoc description of their purpose.

**Header for all classes** (dto, model, config, exception):

```java
/**
 * @author Andi Hermanto
 * @since 2026-07-25
 */
```

**Full Javadoc (English description) for service, util, controller** —
example:

```java
/**
 * REST controller exposing the leaky-bucket rate limiter API.
 * Provides an endpoint to check whether a request is allowed and an
 * endpoint to inspect a user's current bucket state.
 *
 * @author Andi Hermanto
 * @since 2026-07-25
 */
```

## 6. Testing Impact

No new scenarios; logic is unchanged. Tests are updated for the moved
packages and the interface/impl split:

- `RateLimiterModelTest`, `RateLimiterOpsCreateAndStateTest`,
  `RateLimiterOpsAllowTest` — update imports from `...core.*` to `...model.*`
  and `...service.RateLimiterOps`. Assertions unchanged.
- `RateLimiterServiceTest` — construct `RateLimiterServiceImpl(props)`
  instead of the old concrete class; `props.setCapacity(...)` still works via
  Lombok `@Setter`.
- `RateLimiterControllerTest` (MockMvc) — imports only; behavior unchanged.

**Verification:** `mvn test` must report 18/18 green, and one runtime
smoke-test confirms Lombok, logging, and the service interface/impl wiring
work end-to-end (`POST /requests` → 200/429, `GET /users/{id}/bucket` →
200/404).

## 7. Non-Goals

- No database, repository layer, or JPA entities.
- No Maven multi-module split.
- No change to the algorithm, endpoints, status codes, or configuration
  semantics.
- No change to the README's documented behavior (paths/statuses/defaults
  stay the same). README package references, if any, are updated to match.
