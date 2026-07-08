# LoadBalancer

A small HTTP load balancer written in plain Java (no frameworks), built as a learning
project to practice clean design (SOLID, design patterns) and Java concurrency.

It listens on a port, forwards each incoming request to one of several backend
servers, and continuously monitors backend health in the background.

## Features

- Pluggable load balancing algorithm (**Strategy** pattern) — ships with round-robin
  and random, and a new one can be added without touching `LoadBalancer`
- Fluent, validated configuration (**Builder** pattern)
- Background health checks with pluggable reactions to state changes (**Observer**
  pattern) — e.g. logging when a backend goes down
- Thread-safe under concurrent load: bounded request thread pool, lock-free
  round-robin counter, safe concurrent listener registration
- Parallel health checks (`CompletableFuture`) instead of checking backends one by one
- Automatic retry to the next healthy backend when one fails, and a `502` response
  when none are healthy

## Request flow

```
                 ┌────────────┐
   client  ───▶  │LoadBalancer│  1. accept connection (bounded thread pool)
                 └─────┬──────┘
                       │ 2. filter healthy backends
                       │ 3. BalancingStrategy.select(healthy)
                       ▼
              ┌────────┬────────┬────────┐
              │backend1│backend2│backend3│
              └────────┴────────┴────────┘
                       ▲
                       │ periodically, in parallel (CompletableFuture)
                 ┌─────┴───────┐
                 │HealthChecker│──▶ notifies HealthListeners on state change
                 └─────────────┘
```

If forwarding to the selected backend fails, it's marked unhealthy and the next
healthy one is tried. If no backend is healthy, the client gets `502 Bad Gateway`.

## Architecture

| Class | Responsibility |
|---|---|
| `LoadBalancer` | Accepts connections and forwards requests to a healthy backend |
| `HealthChecker` | Periodically checks backend health in the background and notifies listeners on change |
| `BalancingStrategy` | Picks one backend from a list of healthy ones (`RoundRobinStrategy`, `RandomStrategy`) |
| `HealthListener` | Reacts to a backend going up/down (implement your own, e.g. for logging/metrics) |
| `Backend` / `BackendServer` | A backend target; `BackendServer` is a real socket-based server used for local testing |

`LoadBalancer` and `HealthChecker` were originally one class. Splitting them was a
deliberate SRP fix: `LoadBalancer` now only forwards requests, `HealthChecker` only
tracks backend health — each can be tested and changed independently.

## Concurrency notes

- Round-robin index: `AtomicInteger` (lock-free, avoids the lost-update race of a
  plain `int++` under concurrent access)
- Backend `isAlive` flag: `volatile` (needs visibility across threads, not atomicity —
  it's a single read or write, never a read-modify-write)
- Health listeners: `CopyOnWriteArrayList` (read-heavy: iterated on every health
  check, written to rarely — only on registration)
- Backend list: immutable (`List.copyOf` in the builder) — safe to share across
  threads with no locking needed
- Requests are handled on a bounded `ExecutorService` instead of one raw `Thread` per
  request, so load can't exhaust system resources
- Health checks run in parallel per backend via `CompletableFuture`, instead of
  sequentially waiting on each one

## Usage

```java
LoadBalancer loadBalancer = LoadBalancer.builder()
        .port(8080)
        .backends(listOfBackends)              // required
        .balancingStrategy(new RandomStrategy())  // optional, defaults to round-robin
        .healthCheckInterval(5000)              // optional, defaults to 10s
        .poolSize(50)                           // optional, defaults to 50
        .build();

loadBalancer.addHealthListener((backend, alive) ->
        System.out.println(backend.getAddress() + ":" + backend.getPort() + " -> " + alive));

loadBalancer.startHealthCheck();
loadBalancer.start();
```

## Running it

```
mvn compile
java -cp target/classes loadbalancer.Main
```

`Main` starts three local `BackendServer` instances (ports 8081-8083) and a
`LoadBalancer` on port 8080 in front of them. Visit `http://localhost:8080/`.

An optional first argument sets the health check interval in milliseconds
(default 10000).

## Testing

```
mvn test
```

- `RoundRobinStrategyTest`, `RandomStrategyTest` — unit tests for the balancing
  strategies (fake backends, no sockets)
- `HealthCheckerTest` — unit test for health-change notifications, isolated from
  `LoadBalancer` and from real sockets
- `LoadBalancerIntegrationTest` — end-to-end tests over real sockets (healthy
  response, failover, 502 when all backends are down)
- `RaceConditionTest` — demonstrates why the round-robin counter needs
  `AtomicInteger`: a naive `int++` under concurrent access loses increments
  (kept `@Disabled`, since a race isn't guaranteed to reproduce on every run);
  the `AtomicInteger` version is asserted to hold under the same load, every time

Concurrency tests avoid `Thread.sleep`-based timing: they start all worker threads,
join on all of them, then assert an invariant on the final state.
