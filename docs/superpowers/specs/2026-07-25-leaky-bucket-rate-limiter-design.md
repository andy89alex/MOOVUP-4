# Design: Leaky Bucket Rate Limiter with REST API

**Date:** 2026-07-25
**Status:** Approved

## 1. Overview

A small Spring Boot (Java, Maven) REST API backed by a single global in-memory
**leaky bucket** rate limiter. Each user has an independent bucket with fixed
capacity. Each request adds 1 unit; the bucket leaks at a constant rate over
time. A request that would overflow the bucket is rejected.

The core algorithm is implemented as **pure functions over immutable data**,
with no dependency on Spring. The REST layer only orchestrates: it calls the
core functions and swaps the global limiter state.

## 2. Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Framework | Spring Boot | Conventional for Java take-home; mature test tooling (MockMvc). |
| Build tool | Maven | Zero-surprise for evaluators; `mvn spring-boot:run`, `mvn test`. |
| State model | Pure immutability | `allow_request` returns `[allowed, new_state]`; matches the functional emphasis of the spec. Single-threaded → safe. |
| Reject semantics | Leak + timestamp still advance | Leak is a pure function of time; it must apply even when a request is rejected. Only the `+1` is skipped. |
| Timestamp source | Optional in request body, default = server now | Deterministic endpoint tests via explicit timestamp; realistic default for normal use. |
| Reject HTTP status | 429 Too Many Requests | Correct semantic status for rate limiting. |
| Config | `.properties` + Spring Profiles (`dev`/`prod`) | Multi-environment support; overridable via env vars. |

## 3. Architecture & Package Structure

Hard separation between **core** (pure algorithm, no Spring) and **REST layer**.
The core is fully testable without starting the server.

```
src/main/java/com/example/ratelimiter/
├── core/
│   ├── Bucket.java          // record: level, lastTimestamp
│   ├── RateLimiter.java     // record: capacity, leakRate, Map<String,Bucket>
│   ├── RateLimiterOps.java  // pure static fns: createRateLimiter, allowRequest, getBucketState
│   └── AllowResult.java     // record: boolean allowed, RateLimiter newState  (models "[allowed, new_state]")
├── api/
│   ├── RateLimiterController.java   // 2 endpoints
│   ├── RateLimiterService.java      // holds AtomicReference<RateLimiter>, swaps state
│   ├── dto/ (AllowRequestDto, AllowResponseDto, BucketStateDto)
│   └── GlobalExceptionHandler.java  // 404 & validation → 400
├── config/
│   └── RateLimiterProperties.java   // capacity & leakRate from application.properties / env
└── Application.java
```

`RateLimiterOps` is pure static functions to mirror the pseudocode interface
(`allow_request(limiter, ...)`) and to make clear there is no hidden state.

## 4. Core Data Model (immutable)

```java
public record Bucket(double level, double lastTimestamp) {}

public record RateLimiter(int capacity, double leakRate, Map<String, Bucket> buckets) {
    // buckets stored as an unmodifiable Map (Map.copyOf / Map.of)
}

public record AllowResult(boolean allowed, RateLimiter newState) {}
```

All records are immutable. `allowRequest` produces a new `Map` via copy-on-write
(`new HashMap<>(old)`, put, then wrap unmodifiable) and returns a new
`RateLimiter`. Nothing is mutated in place. Copy is O(n users) per request;
acceptable given "correctness first, optimization later" and single-threaded
execution.

## 5. Core Logic (`RateLimiterOps`)

```text
createRateLimiter(capacity, leakRate)
    → new RateLimiter(capacity, leakRate, Map.of())

allowRequest(rl, userId, timestamp):
    Bucket b = rl.buckets().getOrDefault(userId, new Bucket(0, timestamp)); // new user: level 0
    double elapsed = max(0, timestamp - b.lastTimestamp());
    double leaked  = max(0, b.level() - rl.leakRate() * elapsed);
    if (leaked + 1 <= rl.capacity()):
        newBucket = new Bucket(leaked + 1, timestamp); allowed = true
    else:
        newBucket = new Bucket(leaked, timestamp);     allowed = false   // leak & timestamp still advance
    return new AllowResult(allowed, rl.withBucket(userId, newBucket))

getBucketState(rl, userId)
    → rl.buckets().get(userId)   // null if user has never made a request
```

`getBucketState` takes no timestamp (per spec) and returns state **as stored**
at the last update — a snapshot, not leaked to "now".

## 6. REST Layer

```
POST /requests {userId, timestamp?}
   → RateLimiterService.allowRequest():
        RateLimiter current = ref.get();
        AllowResult r = RateLimiterOps.allowRequest(current, userId, ts ?? now);
        ref.set(r.newState());        // swap global state (single-threaded → safe)
        return r;
   → 200 {allowed:true,  bucket:{...}}
   | 429 {allowed:false, bucket:{...}}

GET /users/{userId}/bucket
   → getBucketState
   → 200 {level, lastTimestamp}
   | 404 if null
```

`RateLimiterService` holds one `AtomicReference<RateLimiter>`, initialized from
`RateLimiterProperties` at startup via `createRateLimiter(capacity, leakRate)`.

## 7. Configuration & Startup

`.properties` files with Spring Profiles for multiple environments:

```
src/main/resources/
├── application.properties          # base / default (spec defaults)
├── application-dev.properties
└── application-prod.properties
```

`application.properties` (defaults per spec):

```properties
ratelimiter.capacity=5
ratelimiter.leak-rate=1.0
```

Activate a profile and/or override individual values:

```bash
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
RATELIMITER_CAPACITY=20 mvn spring-boot:run
```

## 8. Error Handling

| Condition | Response |
|-----------|----------|
| Request rejected (overflow) | 429 + `{allowed:false, bucket}` |
| `getBucketState` → null (unknown user) | 404 |
| Invalid body (blank userId, negative timestamp) | 400 via `@Valid` + `GlobalExceptionHandler` |
| Success | 200 |

## 9. Testing Strategy

**Core unit tests (`RateLimiterOpsTest`)** — primary graded artifact; explicit
timestamps (deterministic); no Spring:

- Basic: two requests for user1 at t=0 and t=1
- Burst: many rapid requests → some rejected once full
- Time-based leaking: time-separated requests allowed again after leaking
- Multiple users: independent buckets
- Unknown user: `getBucketState` → null
- Immutability: `allowRequest` does not alter the old limiter
- Edge: first-ever request; exact capacity boundary (`leaked + 1 == capacity`)

**Integration tests (optional, 1–2)** — `MockMvc`:

- `POST /requests` with explicit timestamp → 200 then 429 once full
- `GET /users/unknown/bucket` → 404

## 10. Deliverables

1. Core implementation of the three required functions.
2. Minimal REST API (Spring Boot / Maven).
3. Unit tests (+ 1–2 integration tests).
4. README: how to run (`mvn spring-boot:run`), how to test (`mvn test`),
   configuration/profiles, and design decisions & trade-offs (pure
   immutability, copy-on-write O(n), reject semantics, 429 vs 200).
5. Git repository with clean commit history.
