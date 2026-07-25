# Leaky Bucket Rate Limiter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot REST API backed by a single global in-memory leaky-bucket rate limiter implemented as pure functions over immutable data.

**Architecture:** A Spring-free `core` package holds immutable records (`Bucket`, `RateLimiter`, `AllowResult`) and pure static functions (`RateLimiterOps`). The `api` package orchestrates: a service holds one `AtomicReference<RateLimiter>`, calls the core functions, and swaps in the returned state. Configuration comes from `.properties` files with Spring Profiles.

**Tech Stack:** Java 17, Spring Boot 3.x, Maven, JUnit 5, Spring Boot Test (MockMvc).

## Global Constraints

- Java 17, Spring Boot 3.x, Maven build.
- Base package: `com.example.ratelimiter`.
- In-memory only — no database, Redis, auth, or multi-node.
- Single-threaded execution assumed.
- Timestamps are Unix epoch **seconds**, floating-point (`double`).
- User IDs are non-empty strings.
- Core (`core` package) MUST NOT import any Spring type.
- Pure immutability: core functions never mutate inputs; `allowRequest` returns a new `RateLimiter`.
- Reject semantics: on overflow, the bucket's leaked level and timestamp still advance; only the `+1` is skipped.
- Default config: `capacity=5`, `leak-rate=1.0`.
- Reject → HTTP 429; unknown user on bucket lookup → HTTP 404; invalid body → HTTP 400.

---

### Task 1: Maven project scaffold + Spring Boot app boots

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/example/ratelimiter/Application.java`
- Create: `src/main/resources/application.properties`
- Test: `src/test/java/com/example/ratelimiter/ApplicationTests.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: bootable Spring Boot context; base package `com.example.ratelimiter`; Maven commands `mvn test` and `mvn spring-boot:run`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/ratelimiter/ApplicationTests.java`:
```java
package com.example.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — no `pom.xml` / build cannot resolve Spring Boot yet (compilation or build error).

- [ ] **Step 3: Write minimal implementation**

`pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.2</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>rate-limiter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>rate-limiter</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/com/example/ratelimiter/Application.java`:
```java
package com.example.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

`src/main/resources/application.properties`:
```properties
ratelimiter.capacity=5
ratelimiter.leak-rate=1.0
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `ApplicationTests.contextLoads` green.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/example/ratelimiter/Application.java src/main/resources/application.properties src/test/java/com/example/ratelimiter/ApplicationTests.java
git commit -m "chore: scaffold Spring Boot Maven project"
```

---

### Task 2: Immutable data model (`Bucket`, `RateLimiter`, `AllowResult`)

**Files:**
- Create: `src/main/java/com/example/ratelimiter/core/Bucket.java`
- Create: `src/main/java/com/example/ratelimiter/core/RateLimiter.java`
- Create: `src/main/java/com/example/ratelimiter/core/AllowResult.java`
- Test: `src/test/java/com/example/ratelimiter/core/RateLimiterModelTest.java`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces:
  - `record Bucket(double level, double lastTimestamp)`
  - `record RateLimiter(int capacity, double leakRate, Map<String,Bucket> buckets)` with method `RateLimiter withBucket(String userId, Bucket bucket)` returning a new `RateLimiter` whose `buckets` is an unmodifiable copy with the entry set.
  - `record AllowResult(boolean allowed, RateLimiter newState)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/ratelimiter/core/RateLimiterModelTest.java`:
```java
package com.example.ratelimiter.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterModelTest {

    @Test
    void withBucketReturnsNewInstanceAndDoesNotMutateOriginal() {
        RateLimiter original = new RateLimiter(5, 1.0, Map.of());
        RateLimiter updated = original.withBucket("user1", new Bucket(2.0, 10.0));

        assertTrue(original.buckets().isEmpty(), "original must be unchanged");
        assertEquals(new Bucket(2.0, 10.0), updated.buckets().get("user1"));
        assertNotSame(original, updated);
    }

    @Test
    void bucketsMapIsUnmodifiable() {
        RateLimiter rl = new RateLimiter(5, 1.0, Map.of()).withBucket("u", new Bucket(1.0, 0.0));
        assertThrows(UnsupportedOperationException.class,
                () -> rl.buckets().put("x", new Bucket(0, 0)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RateLimiterModelTest`
Expected: FAIL — `Bucket` / `RateLimiter` / `AllowResult` do not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/example/ratelimiter/core/Bucket.java`:
```java
package com.example.ratelimiter.core;

public record Bucket(double level, double lastTimestamp) {
}
```

`src/main/java/com/example/ratelimiter/core/RateLimiter.java`:
```java
package com.example.ratelimiter.core;

import java.util.HashMap;
import java.util.Map;

public record RateLimiter(int capacity, double leakRate, Map<String, Bucket> buckets) {

    public RateLimiter {
        buckets = Map.copyOf(buckets);
    }

    public RateLimiter withBucket(String userId, Bucket bucket) {
        Map<String, Bucket> next = new HashMap<>(buckets);
        next.put(userId, bucket);
        return new RateLimiter(capacity, leakRate, next);
    }
}
```

`src/main/java/com/example/ratelimiter/core/AllowResult.java`:
```java
package com.example.ratelimiter.core;

public record AllowResult(boolean allowed, RateLimiter newState) {
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RateLimiterModelTest`
Expected: PASS — both tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ratelimiter/core/Bucket.java src/main/java/com/example/ratelimiter/core/RateLimiter.java src/main/java/com/example/ratelimiter/core/AllowResult.java src/test/java/com/example/ratelimiter/core/RateLimiterModelTest.java
git commit -m "feat: add immutable rate limiter data model"
```

---

### Task 3: Core logic — `createRateLimiter` and `getBucketState`

**Files:**
- Create: `src/main/java/com/example/ratelimiter/core/RateLimiterOps.java`
- Test: `src/test/java/com/example/ratelimiter/core/RateLimiterOpsCreateAndStateTest.java`

**Interfaces:**
- Consumes: `Bucket`, `RateLimiter` (Task 2).
- Produces:
  - `static RateLimiter RateLimiterOps.createRateLimiter(int capacity, double leakRate)`
  - `static Bucket RateLimiterOps.getBucketState(RateLimiter rl, String userId)` — returns `null` if the user has never made a request.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/ratelimiter/core/RateLimiterOpsCreateAndStateTest.java`:
```java
package com.example.ratelimiter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterOpsCreateAndStateTest {

    @Test
    void createRateLimiterStartsEmptyWithGivenConfig() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        assertEquals(5, rl.capacity());
        assertEquals(1.0, rl.leakRate());
        assertTrue(rl.buckets().isEmpty());
    }

    @Test
    void getBucketStateReturnsNullForUnknownUser() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        assertNull(RateLimiterOps.getBucketState(rl, "ghost"));
    }

    @Test
    void getBucketStateReturnsStoredBucket() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0)
                .withBucket("user1", new Bucket(3.0, 12.0));
        assertEquals(new Bucket(3.0, 12.0), RateLimiterOps.getBucketState(rl, "user1"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RateLimiterOpsCreateAndStateTest`
Expected: FAIL — `RateLimiterOps` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/example/ratelimiter/core/RateLimiterOps.java`:
```java
package com.example.ratelimiter.core;

import java.util.Map;

public final class RateLimiterOps {

    private RateLimiterOps() {
    }

    public static RateLimiter createRateLimiter(int capacity, double leakRate) {
        return new RateLimiter(capacity, leakRate, Map.of());
    }

    public static Bucket getBucketState(RateLimiter rl, String userId) {
        return rl.buckets().get(userId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RateLimiterOpsCreateAndStateTest`
Expected: PASS — all three tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ratelimiter/core/RateLimiterOps.java src/test/java/com/example/ratelimiter/core/RateLimiterOpsCreateAndStateTest.java
git commit -m "feat: add createRateLimiter and getBucketState core functions"
```

---

### Task 4: Core logic — `allowRequest` (leak, allow/reject, immutability)

**Files:**
- Modify: `src/main/java/com/example/ratelimiter/core/RateLimiterOps.java`
- Test: `src/test/java/com/example/ratelimiter/core/RateLimiterOpsAllowTest.java`

**Interfaces:**
- Consumes: `Bucket`, `RateLimiter`, `AllowResult` (Task 2); existing `RateLimiterOps` (Task 3).
- Produces:
  - `static AllowResult RateLimiterOps.allowRequest(RateLimiter rl, String userId, double timestamp)` — new user starts at level 0; leaks `leakRate * elapsed` (elapsed clamped to ≥0); allows when `leaked + 1 <= capacity` (level becomes `leaked + 1`), else rejects (level becomes `leaked`); timestamp always advances to the request timestamp.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/ratelimiter/core/RateLimiterOpsAllowTest.java`:
```java
package com.example.ratelimiter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterOpsAllowTest {

    @Test
    void firstRequestFromNewUserIsAllowed() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        AllowResult r = RateLimiterOps.allowRequest(rl, "user1", 0.0);

        assertTrue(r.allowed());
        assertEquals(new Bucket(1.0, 0.0), r.newState().buckets().get("user1"));
    }

    @Test
    void allowRequestDoesNotMutateInputLimiter() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        RateLimiterOps.allowRequest(rl, "user1", 0.0);
        assertTrue(rl.buckets().isEmpty(), "input limiter must be unchanged");
    }

    @Test
    void burstBeyondCapacityIsRejected() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        // 5 rapid requests at same timestamp fill the bucket to capacity
        boolean lastAllowed = true;
        for (int i = 0; i < 5; i++) {
            AllowResult r = RateLimiterOps.allowRequest(rl, "user1", 0.0);
            lastAllowed = r.allowed();
            rl = r.newState();
        }
        assertTrue(lastAllowed, "5th request fills to capacity and is allowed");

        AllowResult overflow = RateLimiterOps.allowRequest(rl, "user1", 0.0);
        assertFalse(overflow.allowed(), "6th request overflows and is rejected");
        // rejected: level stays at 5.0 (leaked=5, no +1), timestamp advances
        assertEquals(new Bucket(5.0, 0.0), overflow.newState().buckets().get("user1"));
    }

    @Test
    void bucketLeaksOverTimeAllowingLaterRequests() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(5, 1.0);
        // fill to capacity at t=0
        for (int i = 0; i < 5; i++) {
            rl = RateLimiterOps.allowRequest(rl, "user1", 0.0).newState();
        }
        // at t=0 it's full → reject
        assertFalse(RateLimiterOps.allowRequest(rl, "user1", 0.0).allowed());
        // after 1 second, 1 unit leaked → room for one → allow
        AllowResult afterLeak = RateLimiterOps.allowRequest(rl, "user1", 1.0);
        assertTrue(afterLeak.allowed());
        // leaked from 5 to 4, then +1 = 5, timestamp 1.0
        assertEquals(new Bucket(5.0, 1.0), afterLeak.newState().buckets().get("user1"));
    }

    @Test
    void usersHaveIndependentBuckets() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(1, 1.0);
        rl = RateLimiterOps.allowRequest(rl, "user1", 0.0).newState(); // user1 now full
        AllowResult u2 = RateLimiterOps.allowRequest(rl, "user2", 0.0);
        assertTrue(u2.allowed(), "user2 has its own empty bucket");
    }

    @Test
    void exactCapacityBoundaryIsAllowed() {
        RateLimiter rl = RateLimiterOps.createRateLimiter(2, 1.0);
        rl = RateLimiterOps.allowRequest(rl, "u", 0.0).newState(); // level 1
        AllowResult r = RateLimiterOps.allowRequest(rl, "u", 0.0);  // leaked+1 == 2 == capacity
        assertTrue(r.allowed());
        assertEquals(new Bucket(2.0, 0.0), r.newState().buckets().get("u"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RateLimiterOpsAllowTest`
Expected: FAIL — `allowRequest` not defined (compilation error).

- [ ] **Step 3: Write minimal implementation**

Add to `src/main/java/com/example/ratelimiter/core/RateLimiterOps.java` (inside the class, after `getBucketState`):
```java
    public static AllowResult allowRequest(RateLimiter rl, String userId, double timestamp) {
        Bucket current = rl.buckets().getOrDefault(userId, new Bucket(0.0, timestamp));
        double elapsed = Math.max(0.0, timestamp - current.lastTimestamp());
        double leaked = Math.max(0.0, current.level() - rl.leakRate() * elapsed);

        if (leaked + 1.0 <= rl.capacity()) {
            Bucket updated = new Bucket(leaked + 1.0, timestamp);
            return new AllowResult(true, rl.withBucket(userId, updated));
        }
        Bucket updated = new Bucket(leaked, timestamp);
        return new AllowResult(false, rl.withBucket(userId, updated));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RateLimiterOpsAllowTest`
Expected: PASS — all six tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ratelimiter/core/RateLimiterOps.java src/test/java/com/example/ratelimiter/core/RateLimiterOpsAllowTest.java
git commit -m "feat: add allowRequest core logic with leak and reject semantics"
```

---

### Task 5: Config properties + service holding global state

**Files:**
- Create: `src/main/java/com/example/ratelimiter/config/RateLimiterProperties.java`
- Create: `src/main/java/com/example/ratelimiter/api/RateLimiterService.java`
- Modify: `src/main/java/com/example/ratelimiter/Application.java` (enable config properties)
- Test: `src/test/java/com/example/ratelimiter/api/RateLimiterServiceTest.java`

**Interfaces:**
- Consumes: `RateLimiterOps`, `RateLimiter`, `Bucket`, `AllowResult` (Tasks 2–4).
- Produces:
  - `RateLimiterProperties` — bound to prefix `ratelimiter`, fields `int capacity`, `double leakRate`.
  - `RateLimiterService` with:
    - constructor `RateLimiterService(RateLimiterProperties props)` initializing an `AtomicReference<RateLimiter>` via `createRateLimiter`.
    - `AllowResult allowRequest(String userId, double timestamp)` — calls core, swaps stored state, returns the result.
    - `Bucket getBucketState(String userId)` — reads current state.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/ratelimiter/api/RateLimiterServiceTest.java`:
```java
package com.example.ratelimiter.api;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.AllowResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService newService(int capacity, double leakRate) {
        RateLimiterProperties props = new RateLimiterProperties();
        props.setCapacity(capacity);
        props.setLeakRate(leakRate);
        return new RateLimiterService(props);
    }

    @Test
    void serviceSwapsStateBetweenCalls() {
        RateLimiterService service = newService(1, 1.0);
        AllowResult first = service.allowRequest("user1", 0.0);
        AllowResult second = service.allowRequest("user1", 0.0);

        assertTrue(first.allowed());
        assertFalse(second.allowed(), "state persisted across calls; bucket now full");
    }

    @Test
    void getBucketStateReflectsStoredState() {
        RateLimiterService service = newService(5, 1.0);
        assertNull(service.getBucketState("user1"));
        service.allowRequest("user1", 0.0);
        assertEquals(1.0, service.getBucketState("user1").level());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RateLimiterServiceTest`
Expected: FAIL — `RateLimiterProperties` / `RateLimiterService` do not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/example/ratelimiter/config/RateLimiterProperties.java`:
```java
package com.example.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimiterProperties {
    private int capacity = 5;
    private double leakRate = 1.0;

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getLeakRate() {
        return leakRate;
    }

    public void setLeakRate(double leakRate) {
        this.leakRate = leakRate;
    }
}
```

`src/main/java/com/example/ratelimiter/api/RateLimiterService.java`:
```java
package com.example.ratelimiter.api;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.AllowResult;
import com.example.ratelimiter.core.Bucket;
import com.example.ratelimiter.core.RateLimiter;
import com.example.ratelimiter.core.RateLimiterOps;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class RateLimiterService {

    private final AtomicReference<RateLimiter> state;

    public RateLimiterService(RateLimiterProperties props) {
        this.state = new AtomicReference<>(
                RateLimiterOps.createRateLimiter(props.getCapacity(), props.getLeakRate()));
    }

    public AllowResult allowRequest(String userId, double timestamp) {
        AllowResult result = RateLimiterOps.allowRequest(state.get(), userId, timestamp);
        state.set(result.newState());
        return result;
    }

    public Bucket getBucketState(String userId) {
        return RateLimiterOps.getBucketState(state.get(), userId);
    }
}
```

Modify `src/main/java/com/example/ratelimiter/Application.java` — add the annotation and import so the properties bean is registered:
```java
package com.example.ratelimiter;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimiterProperties.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RateLimiterServiceTest`
Expected: PASS — both tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ratelimiter/config/RateLimiterProperties.java src/main/java/com/example/ratelimiter/api/RateLimiterService.java src/main/java/com/example/ratelimiter/Application.java src/test/java/com/example/ratelimiter/api/RateLimiterServiceTest.java
git commit -m "feat: add config properties and stateful rate limiter service"
```

---

### Task 6: REST layer — DTOs, controller, exception handling

**Files:**
- Create: `src/main/java/com/example/ratelimiter/api/dto/AllowRequestDto.java`
- Create: `src/main/java/com/example/ratelimiter/api/dto/BucketStateDto.java`
- Create: `src/main/java/com/example/ratelimiter/api/dto/AllowResponseDto.java`
- Create: `src/main/java/com/example/ratelimiter/api/UnknownUserException.java`
- Create: `src/main/java/com/example/ratelimiter/api/GlobalExceptionHandler.java`
- Create: `src/main/java/com/example/ratelimiter/api/RateLimiterController.java`
- Test: `src/test/java/com/example/ratelimiter/api/RateLimiterControllerTest.java`

**Interfaces:**
- Consumes: `RateLimiterService`, `AllowResult`, `Bucket` (Tasks 4–5).
- Produces: HTTP endpoints
  - `POST /requests` body `{userId, timestamp?}` → 200 `{allowed, bucket}` or 429 `{allowed, bucket}`; 400 on invalid body.
  - `GET /users/{userId}/bucket` → 200 `{level, lastTimestamp}` or 404.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/ratelimiter/api/RateLimiterControllerTest.java`:
```java
package com.example.ratelimiter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsThenRejectsOnceFull() throws Exception {
        // default capacity=5; fill with 5 allowed requests at t=0
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"burst\",\"timestamp\":0.0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }
        // 6th overflows → 429
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"burst\",\"timestamp\":0.0}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void unknownUserBucketReturns404() throws Exception {
        mockMvc.perform(get("/users/nobody/bucket"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankUserIdReturns400() throws Exception {
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"timestamp\":0.0}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RateLimiterControllerTest`
Expected: FAIL — controller/DTOs do not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/example/ratelimiter/api/dto/AllowRequestDto.java`:
```java
package com.example.ratelimiter.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public class AllowRequestDto {

    @NotBlank
    private String userId;

    @PositiveOrZero
    private Double timestamp; // optional; null → server now

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Double timestamp) {
        this.timestamp = timestamp;
    }
}
```

`src/main/java/com/example/ratelimiter/api/dto/BucketStateDto.java`:
```java
package com.example.ratelimiter.api.dto;

import com.example.ratelimiter.core.Bucket;

public record BucketStateDto(double level, double lastTimestamp) {
    public static BucketStateDto from(Bucket bucket) {
        return new BucketStateDto(bucket.level(), bucket.lastTimestamp());
    }
}
```

`src/main/java/com/example/ratelimiter/api/dto/AllowResponseDto.java`:
```java
package com.example.ratelimiter.api.dto;

public record AllowResponseDto(boolean allowed, BucketStateDto bucket) {
}
```

`src/main/java/com/example/ratelimiter/api/UnknownUserException.java`:
```java
package com.example.ratelimiter.api;

public class UnknownUserException extends RuntimeException {
    public UnknownUserException(String userId) {
        super("Unknown user: " + userId);
    }
}
```

`src/main/java/com/example/ratelimiter/api/GlobalExceptionHandler.java`:
```java
package com.example.ratelimiter.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnknownUserException.class)
    public ResponseEntity<Map<String, String>> handleUnknownUser(UnknownUserException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "invalid request body"));
    }
}
```

`src/main/java/com/example/ratelimiter/api/RateLimiterController.java`:
```java
package com.example.ratelimiter.api;

import com.example.ratelimiter.api.dto.AllowRequestDto;
import com.example.ratelimiter.api.dto.AllowResponseDto;
import com.example.ratelimiter.api.dto.BucketStateDto;
import com.example.ratelimiter.core.AllowResult;
import com.example.ratelimiter.core.Bucket;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class RateLimiterController {

    private final RateLimiterService service;

    public RateLimiterController(RateLimiterService service) {
        this.service = service;
    }

    @PostMapping("/requests")
    public ResponseEntity<AllowResponseDto> checkRequest(@Valid @RequestBody AllowRequestDto request) {
        double timestamp = request.getTimestamp() != null
                ? request.getTimestamp()
                : System.currentTimeMillis() / 1000.0;

        AllowResult result = service.allowRequest(request.getUserId(), timestamp);
        BucketStateDto bucket = BucketStateDto.from(result.newState().buckets().get(request.getUserId()));
        AllowResponseDto body = new AllowResponseDto(result.allowed(), bucket);

        HttpStatus status = result.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/users/{userId}/bucket")
    public BucketStateDto getBucket(@PathVariable String userId) {
        Bucket bucket = service.getBucketState(userId);
        if (bucket == null) {
            throw new UnknownUserException(userId);
        }
        return BucketStateDto.from(bucket);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RateLimiterControllerTest`
Expected: PASS — all three tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/ratelimiter/api/
git commit -m "feat: add REST controller, DTOs, and exception handling"
```

---

### Task 7: Environment profiles + README + full verification

**Files:**
- Create: `src/main/resources/application-dev.properties`
- Create: `src/main/resources/application-prod.properties`
- Create: `README.md`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: `dev`/`prod` profile config files; documented run/test instructions. No new code interfaces.

- [ ] **Step 1: Create the profile property files**

`src/main/resources/application-dev.properties`:
```properties
ratelimiter.capacity=5
ratelimiter.leak-rate=1.0
```

`src/main/resources/application-prod.properties`:
```properties
ratelimiter.capacity=100
ratelimiter.leak-rate=10.0
```

- [ ] **Step 2: Write the README**

`README.md`:
```markdown
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
```

- [ ] **Step 3: Run the full test suite**

Run: `mvn -q test`
Expected: PASS — all tests from Tasks 1–6 green.

- [ ] **Step 4: Smoke-test the running app**

Run:
```bash
mvn spring-boot:run &
sleep 20
curl -s -X POST http://localhost:8080/requests -H 'Content-Type: application/json' -d '{"userId":"smoke","timestamp":0.0}'
curl -s http://localhost:8080/users/smoke/bucket
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/users/ghost/bucket
kill %1
```
Expected: first curl → `{"allowed":true,...}`; second → bucket JSON with `level` 1.0; third → `404`.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application-dev.properties src/main/resources/application-prod.properties README.md
git commit -m "docs: add environment profiles and README"
```

---

## Notes for the Implementer

- Run tasks in order; each builds on the previous.
- After Task 6, `mvn test` runs the whole suite — keep it green before moving on.
- Do not add Spring imports to anything under `core/` — that separation is a graded design point.
- Keep commits per-task as shown; clean history is a deliverable.
