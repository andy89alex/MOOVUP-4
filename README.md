# Leaky Bucket Rate Limiter (Spring Boot)

A REST API backed by a single global in-memory leaky-bucket rate limiter.
Each user has an independent bucket of fixed `capacity`. Each request adds 1
unit; the bucket leaks `leak-rate` units per second. A request that would
overflow the bucket is rejected.

## Requirements

- Java 17+
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`.

### Environment profiles

Config lives in `src/main/resources/application*.properties`. Select a profile:

```bash
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
# or
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Override individual values via environment variables:

```bash
RATELIMITER_CAPACITY=20 RATELIMITER_LEAK_RATE=2.0 mvn spring-boot:run
```

Defaults: `capacity=5`, `leak-rate=1.0`.

## API

### Check whether a request is allowed

```bash
curl -i -X POST http://localhost:8080/requests \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user1","timestamp":0.0}'
```

- `timestamp` is optional (Unix epoch seconds, floating-point). If omitted, the
  server uses the current time.
- Response `200` with `{"allowed":true,"bucket":{...}}` when allowed.
- Response `429` with `{"allowed":false,"bucket":{...}}` when rejected.
- Response `400` when `userId` is blank or `timestamp` is negative.

### Get a user's bucket state

```bash
curl -i http://localhost:8080/users/user1/bucket
```

- Response `200` with `{"level":...,"lastTimestamp":...}`.
- Response `404` if the user has never made a request.

## Test

```bash
mvn test
```

## Design decisions & trade-offs

- **Pure functional core.** The algorithm lives in `core/RateLimiterOps` as
  pure static functions over immutable `record`s (`Bucket`, `RateLimiter`,
  `AllowResult`). `allowRequest` returns `[allowed, newState]` and never
  mutates its input. The core has zero Spring dependencies and is unit-tested
  in isolation.
- **State swapping.** `RateLimiterService` holds the current limiter in an
  `AtomicReference` and swaps in the new state returned by the core after each
  request. Single-threaded execution is assumed, per the brief.
- **Immutability cost.** Each `allowRequest` copies the bucket map
  (O(number of users)). Acceptable here — correctness first; the data set is
  small and single-node.
- **Reject semantics.** On overflow the bucket's leaked level and timestamp
  still advance to the request time; only the `+1` is skipped. Leaking is a
  pure function of elapsed time, so it must apply even to rejected requests.
- **HTTP status.** Rejection maps to `429 Too Many Requests`, the semantically
  correct rate-limit status, rather than a `200` with a flag.
- **`getBucketState` takes no timestamp** (per the required interface), so it
  returns the stored snapshot as of the last update rather than leaking to
  "now".
