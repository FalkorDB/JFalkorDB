# PR 15 — `FalkorDB.builder()` + JSpecify nullability — implementation plan

> **Temporary planning artifact** for Wave 4 · PR 15 (tracked by #332). Reviewed for approval;
> **NOT merged** — deleted once PR 15 lands. Not referenced from permanent project files.
>
> This revision folds in a rubber-duck review that was verified against the pinned Jedis **7.5.3**
> (`javap` on the resolved jar) and commons-pool2 **2.12.1**.

## Goal

Wave 4 · master-plan step 11 (API ergonomics): one **discoverable, fluent** entry point that covers the
**common** connection options — host/port, auth, **TLS**, connection-**pool sizing** (incl. the
`maxWait` the README already documents), and **timeouts** (the #282 socket-timeout knob) — plus
**JSpecify nullability** on the public API. It replaces most hand-rolled `DriverImpl(new JedisPool(...))`
snippets in the README with a supported API.

## Hard constraints

- **Additive only.** Every existing `FalkorDB.driver(...)` factory and the whole `Driver` interface stay
  **unchanged**. New public *classes/methods* are binary/source-compatible additions → **`api-diff`
  (japicmp 0.26.1) stays green** (public-class method/annotation additions are compatible).
- **Java 8.** `--release 8`, Java-8 APIs only; must pass Animal Sniffer, the Enforcer bytecode check,
  and the JDK-8 runtime smoke.
- **JSpecify ships at runtime.** Normal Maven **compile** scope (the JSpecify recommendation — not
  `provided`/`optional`). Must be Java-8 bytecode (Enforcer) and load on JDK 8 (smoke).

## Verified against the code (7.5.3)

- `FalkorDB` (final) exposes `driver()`, `driver(host,port)`, `driver(host,port,user,password)`,
  `driver(URI)`, all delegating to `com.falkordb.impl.api.DriverImpl`.
- `DriverImpl` wraps a `Pool<Jedis>`; `clientConfig(user,password)` builds a `DefaultJedisClientConfig`
  with `connectionTimeoutMillis = Protocol.DEFAULT_TIMEOUT (2000)`, `socketTimeoutMillis = 0` (**#282**:
  no client read deadline), optional creds. A **public** `DriverImpl(Pool<Jedis>)` escape hatch exists.
- **Constructor we build on exists**: `JedisPool(GenericObjectPoolConfig<Jedis>, HostAndPort,
  JedisClientConfig)` ✅. `DefaultJedisClientConfig.Builder` has `ssl(boolean)`,
  `connectionTimeoutMillis`, `socketTimeoutMillis`, plus `database`, `clientName`, `protocol`,
  `blockingSocketTimeoutMillis`, `sslOptions`.
- commons-pool2 `GenericObjectPoolConfig` defaults: `maxTotal=8`, `maxIdle=8`, `minIdle=0`,
  `maxWait=-1ms` (**wait indefinitely**). `driver()` uses these defaults today.
- **No** `JedisPool(GenericObjectPoolConfig, URI, JedisClientConfig)` ctor exists — so combining a URI
  with credential/TLS overrides would need `DefaultJedisClientConfig.builder(uri)` +
  `JedisURIHelper.getHostAndPort(uri)` + the 3-arg ctor above (all verified present). → see §A note on
  deferring `.uri()`.

## A. `FalkorDB.builder()` — the API (15a)

New public **nested** `public static final class FalkorDB.Builder` with an **explicit `private`
constructor** (reached only via `FalkorDB.builder()`, so no compiler-generated public ctor becomes
permanent API). Fluent setters return `this`; `build()` returns the **existing** `Driver`.

```java
Driver driver = FalkorDB.builder()
    .host("db.example.com")            // default "localhost"
    .port(6379)                        // default 6379
    .credentials("user", "password")   // optional; also credentials("password") for password-only
    .ssl(true)                         // optional TLS (default false)
    .poolMaxTotal(64)                  // default 8   (Loom-relevant fan-out ceiling)
    .poolMaxIdle(16)                   // default 8
    .poolMaxWait(Duration.ofSeconds(30))       // default -1ms = wait indefinitely
    .connectionTimeout(Duration.ofSeconds(2))  // default 2000ms (Protocol.DEFAULT_TIMEOUT)
    .socketTimeout(Duration.ZERO)      // #282 knob; default ZERO = no client read deadline
    .build();
```

Design decisions (rationale from the review):

1. **Nested `FalkorDB.Builder`**, discoverable from `FalkorDB.builder()`; keeps the public surface tidy.
2. **`Duration`** timeouts (java.time, Java-8-safe). **Two distinct paths — do not share a helper:**
   - **`connectionTimeout` / `socketTimeout`** → the Jedis `Duration → int millis` helper (§B): rejects
     `null`/negative and `> Integer.MAX_VALUE` ms; a **positive sub-millisecond** value rounds **up to
     1 ms** (never silently truncates to 0/“infinite”); `Duration.ZERO → 0` (“no deadline”, matching
     #282). These map to `DefaultJedisClientConfig.connectionTimeoutMillis/socketTimeoutMillis`.
   - **`poolMaxWait`** → passed **directly** to commons-pool2 `GenericObjectPoolConfig.setMaxWait(
     Duration)` — **no int conversion**. Its semantics are **different**: **negative = wait
     indefinitely** (the default, `-1 ms`), **`Duration.ZERO` = fail immediately** when the pool is
     exhausted, positive = bounded wait. So `poolMaxWait` is **not** run through the Jedis helper and a
     negative value is **allowed** (means “forever”); document this explicitly.
3. **`build()` returns `Driver`** (interface unchanged). It resolves a **public immutable config spec**
   in `com.falkordb.impl.api` (public so `FalkorDB.Builder` in `com.falkordb` can construct it; in
   `impl`, so **excluded from api-diff**) and hands it to a new **public** `DriverImpl` factory that owns
   all Jedis wiring (`DefaultJedisClientConfig` + `GenericObjectPoolConfig` + `new JedisPool(poolCfg,
   HostAndPort, clientCfg)` → `new DriverImpl(pool)`). Keeping Jedis imports in `impl` avoids leaking
   them into `com.falkordb`; making the spec **public** (not package-private) is what lets the
   cross-package call compile.
4. **Defaults ≡ `driver()`** exactly: `localhost:6379`, no creds, `ssl=false`, `connTimeout=2000`,
   `socketTimeout=0`, pool `maxTotal=8 / maxIdle=8 / maxWait=-1`. A unit test locks the builder-default
   **spec** to these values (see §D — not object-equality on Jedis configs).
5. **`ssl(boolean)`** stays the public knob; internally mapped through `SslOptions.defaults()` if the
   raw `ssl(boolean)` builder method is deprecated in 7.5.3 (tests then assert on `getSslOptions()`,
   else `isSsl()`).
6. **Validation at `build()`** (order-independent): non-blank `host`; `1 ≤ port ≤ 65535`;
   `poolMaxTotal ≥ 1`; `0 ≤ poolMaxIdle ≤ poolMaxTotal`; `connectionTimeout`/`socketTimeout` per the
   Jedis-timeout rules in (2). `poolMaxWait` accepts **any** `Duration` incl. negative (“wait forever”)
   and zero (“fail fast”) — it is **not** validated as a Jedis timeout. Failures throw
   `IllegalArgumentException`.

**`.uri()` is deferred out of 15a.** Rationale: `FalkorDB.driver(URI)` already covers URI users, and
mixing `.uri()` with `.ssl()/.credentials()` needs careful tri-state precedence (a bare default `false`
applied after `rediss://` would **silently downgrade TLS**; `builder(uri)` installs a credentials
provider that plain `.user()/.password()` won’t override). The correct future recipe is documented
here (`builder(uri)` + only-explicit overrides + `getHostAndPort(uri)` + 3-arg ctor) but implementing it
is a separate, opt-in follow-up, not 15a.

**Deferred options** (documented as “future”, keeping the escape hatch for now): `poolMinIdle`
(needs an evictor/`preparePool` to take effect — semantic change), `testOnBorrow/Return/WhileIdle`,
`database`, `clientName`, RESP `protocol`, `blockingSocketTimeout`, custom TLS trust/keystore. PR 16
keeps `DriverImpl(pool)` examples for these until/unless the builder grows them.

## B. Impl wiring (`com.falkordb.impl`)

- Add a **public** immutable **config spec** in `com.falkordb.impl.api` (host, port, user, password,
  ssl, connectionTimeoutMillis `int`, socketTimeoutMillis `int`, poolMaxTotal, poolMaxIdle,
  **poolMaxWait `Duration`**) with getters — the **testable seam**. It is public so `FalkorDB.Builder`
  (package `com.falkordb`) can construct it across packages; being in `impl` it is **excluded from
  api-diff**.
- Add a **public** `DriverImpl` static factory / constructor taking that spec, which assembles the
  `DefaultJedisClientConfig` (creds + `ssl` via `SslOptions.defaults()` + the two **int-millis**
  timeouts) + `GenericObjectPoolConfig` (`setMaxTotal/ setMaxIdle`, and **`setMaxWait(Duration)`
  directly** — commons-pool2 native, negative=forever/zero=fail-fast) + `new JedisPool(poolCfg,
  HostAndPort, clientCfg)` and returns a `DriverImpl`. All new `impl` surface is excluded from
  `api-diff`.
- A single `Duration → int millis` helper (used **only** for connection/socket timeouts, never for
  `poolMaxWait`) implements the §A(2) rules in one place.

## C. JSpecify nullability (15b)

- Add `org.jspecify:jspecify` (pin the current 1.0.0; base annotations are **class-file 52** and
  runtime-retained; its Java-9 `module-info` lives in an MRJAR entry the Enforcer already skips) at
  **compile** scope.
- For a **real, complete** contract (not merely “some `@Nullable`s”): apply package-level
  **`@NullMarked`** to the public API packages (`com.falkordb`, `com.falkordb.graph_entities`,
  `com.falkordb.exceptions`; **not** `com.falkordb.impl`), then annotate the genuine nullables with
  `@Nullable` at the correct **type-use** position — e.g. `Property` **name and value** are both
  nullable (`graph_entities/Property.java`), `GraphEntity.getProperty(...)` returns null when absent,
  optional builder getters. Per-member review required; watch generic/container element positions.
- Verify: Enforcer accepts the jspecify bytecode; `just verify-jdk8` still green with jspecify on the
  runtime classpath; the smoke reflects on an **annotated** member (`GraphEntity.getProperty()`) to
  assert `@Nullable` is visible on JDK 8 — **not** on `@NullMarked` itself (JSpecify documents a Java-8
  `ElementType.MODULE` reflection caveat).

## D. Tests

- **Unit** (`*Test`, no server): builder → **config spec** mapping (host/port/creds/ssl/timeouts/pool);
  **all validation errors**; the **defaults regression** — builder-default spec **field-by-field ==**
  the `driver()` reference values (do **not** `assertEquals` on `DefaultJedisClientConfig` /
  `GenericObjectPoolConfig`; neither overrides `equals`, and Jedis hides the client config in
  `JedisFactory`). TLS mapping asserted via `getSslOptions()`/`isSsl()`.
- **IT** (`*IT`, Testcontainers): `builder().build()` connect → query → close (mirror
  `InstantiationIT`), plaintext.
- **`api-diff`**: green (additions only) — no `Driver`/factory changes.

## E. Javadoc

Doclint-clean Javadoc on `FalkorDB.builder()` and every `Builder` method (the `javadoc` gate enforces
it), including the default value and units for each option.

## F. Validation (via `just` = CI)

`just verify` · `just lint` · `just fmt-check` · `just javadoc` · `just api-diff` (green — additive) ·
`just verify-jdk8` (jspecify loads on JDK 8, 15b) · `just spellcheck` (if docs change).

## G. Risks & mitigations

| Risk | Mitigation |
| --- | --- |
| Builder breaks `api-diff` | Additive only; `public static final Builder` + **private** ctor (no generated public ctor); `Driver`/factories untouched. |
| Jedis hides client config → can’t assert defaults | Internal immutable **spec** seam; compare getters field-by-field, never object-equals/`toString`. |
| `.uri()` + `.ssl()/.credentials()` silently downgrades TLS/auth | **Defer `.uri()`** from 15a; document correct tri-state recipe for later. |
| `Duration→int` overflow / sub-ms truncation | Central helper: reject null/negative/`>Int.MAX` ms; round positive sub-ms up to 1 ms. |
| `poolMinIdle` ineffective without evictor | **Defer** `poolMinIdle`; ship `maxTotal/maxIdle/maxWait` (matches README + Loom need). |
| `ssl(boolean)` deprecated in 7.5.3 | Map through `SslOptions.defaults()` internally; test `getSslOptions()`. |
| JSpecify shipped dep breaks Java-8 | 1.0.0 base is class-file 52 + compile scope; Enforcer + JDK-8 smoke verify. |
| “`@Nullable`-only” ≠ real contract | Package `@NullMarked` + explicit `@Nullable`; per-member review. |

## H. Proposed sub-PR split

- **15a** — `FalkorDB.builder()` (host/port/creds/ssl/`maxTotal`/`maxIdle`/`maxWait`/conn+socket
  timeouts) + config-spec seam + public `DriverImpl` factory + unit/IT tests + Javadoc. **No JSpecify.**
- **15b** — `org.jspecify:jspecify` dependency + package `@NullMarked` + per-member `@Nullable` audit +
  JDK-8 annotation-loading smoke.

Each is independently `api-diff`-green. **PR 16** consumes the 15a builder for the common README
examples; it keeps `DriverImpl(pool)` snippets for the deferred advanced knobs (custom TLS keystore,
`testOn*`, `minIdle`) until the builder covers them.

## I. Open questions

1. **`.uri()`**: OK to **defer** it from the builder (URI users keep `FalkorDB.driver(URI)`), given the
   TLS/credential-precedence hazard? (Recommended.)
2. **Split**: one PR, or the **15a / 15b** split above? (Recommended: split.)
3. **Nullability contract**: package-level **`@NullMarked`** across the 3 public packages now
   (complete contract, more review), **or** a smaller “known-nullable” `@Nullable`-only pass first with
   `@NullMarked` to follow?
4. **Pool knobs in 15a**: is `maxTotal/maxIdle/maxWait` the right initial set, deferring
   `minIdle`/`testOn*` (which need an evictor / eager prefill) to a follow-up?
