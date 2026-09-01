# Changelog

## [0.11.1](https://github.com/FalkorDB/JFalkorDB/compare/v0.11.0...v0.11.1) (2026-09-01)


### Bug Fixes

* **cache:** warm the graph cache with a read-only query so replica reads work ([#414](https://github.com/FalkorDB/JFalkorDB/issues/414)) ([1c50cab](https://github.com/FalkorDB/JFalkorDB/commit/1c50cab49a3a18f005bd0604715a42587e5ade0f))


### Documentation

* document that path node order is independent of edge direction ([#418](https://github.com/FalkorDB/JFalkorDB/issues/418)) ([0d5a47e](https://github.com/FalkorDB/JFalkorDB/commit/0d5a47e0c86c026b66c33950d472cb5295cb5634)), closes [#393](https://github.com/FalkorDB/JFalkorDB/issues/393)

## [0.11.0](https://github.com/FalkorDB/JFalkorDB/compare/v0.10.1...v0.11.0) (2026-08-17)

This release makes result-set parsing correct and thread-safe, and picks up **Jedis 8**.
The public API is **source- and binary-compatible** with 0.10.1 — `japicmp` reports
`No changes.` — but five *runtime behaviours* changed, which is why this is a minor bump
rather than a patch. No signature changed, so none of the following is visible to an
API-diff tool; they are listed here instead.

### ⚠ BREAKING CHANGES

| Call | 0.10.1 | 0.11.0 | Migration |
| --- | --- | --- | --- |
| `Record.getValue(String)` / `getString(String)` with an **unknown column** | `IndexOutOfBoundsException` (`Index: -1, Size: n`) — the `-1` from the failed lookup leaked into `List.get` | `IllegalArgumentException` naming the key and listing the available columns | Catch `IllegalArgumentException`. The message now identifies the typo instead of hiding it |
| `Record.getString(int)` / `getString(String)` on a **NULL column** | `NullPointerException` from `null.toString()` | returns `null` | Null-check the result. `getValue` already returned `null` for the same column, so the two accessors now agree |
| `Header.getSchemaNames()`, `Header.getSchemaTypes()`, `Record.keys()` | the header's **internal mutable `ArrayList`** | an unmodifiable list; mutating it throws `UnsupportedOperationException` | Copy before mutating: `new ArrayList<>(header.getSchemaNames())` |
| A **malformed or future column-type ordinal** in the header | `ArrayIndexOutOfBoundsException`, thrown **lazily** at the first `getSchemaTypes()` call | `JedisDataException("Unrecognized response type")`, thrown **eagerly** while the reply is parsed | Catch `JedisDataException` around the query rather than around the accessor |
| **Non-finite doubles** (`RETURN 1.0/0.0`) and `vecf32` floats | `NumberFormatException: For input string: "inf"`, or `GraphException: Invalid float value in vector data` for vectors | `Double.POSITIVE_INFINITY`, `NEGATIVE_INFINITY`, `NaN` | None — the value now parses instead of throwing. Only affects code that *relied* on the exception |

Returning the internal `ArrayList` was not a deliberate API decision: because every record
in a result set shares the header's list, a caller who mutated it corrupted the schema for
the whole result set in place. The mutability contract is now documented on `Header` and
`Record` — which previously said nothing about it — and pinned by tests.

One further change is **binary**-incompatible but sits in the internal `com.falkordb.impl`
package, which is excluded from the API-diff gate:
`com.falkordb.impl.resultset.HeaderImpl` is now `final`. `extends HeaderImpl` compiled
against 0.10.1 and no longer does. `ResultSetImpl` was already `final` and `RecordImpl`
remains non-final, so this is the only such change.

### Bug Fixes

* non-finite doubles, header thread-safety, and Record null/unknown-column handling ([#390](https://github.com/FalkorDB/JFalkorDB/issues/390)) ([7e456e7](https://github.com/FalkorDB/JFalkorDB/commit/7e456e76e62d79e2e871130eb09fb4d5a3694d63))

Beyond the behaviours listed above, this also fixes a **data race**: the header used to
parse itself lazily on first access, so two threads reading one `ResultSet` could race and
see a half-built schema. It is now built eagerly and immutably at construction, so a
`ResultSet` is safe to hand to another thread. Column-type lookups are also range-checked
as `long`, closing a narrowing hole where an out-of-range ordinal such as `4294967297`
wrapped to a valid one and silently mis-typed a column.

### Dependencies

* bump `redis.clients:jedis` from 7.5.3 to **8.0.0** ([#397](https://github.com/FalkorDB/JFalkorDB/issues/397)) ([6aacdfb](https://github.com/FalkorDB/JFalkorDB/commit/6aacdfb39d6b55431d7a39b04f0eed652e4402f8))

Jedis 8 is a major upgrade of the transport and reaches applications transitively — check
it against any direct Jedis use of your own. JFalkorDB pins the connection to **RESP2**
explicitly, so protocol behaviour is unchanged from 0.10.1 and Jedis 8's per-connection
RESP3 auto-negotiation warning does not appear.

### Documentation

* fix all Javadoc warnings in the javadoc jar build ([#386](https://github.com/FalkorDB/JFalkorDB/issues/386)) ([c547bfe](https://github.com/FalkorDB/JFalkorDB/commit/c547bfebe3d03a3dd2878a1dbc0fe788a825edaf))

### Compatibility

* **Java 8** remains the minimum and is still verified on a real JDK 8 in CI.
* The jar now pins `Automatic-Module-Name: jfalkordb` — the same name Java already derived
  from the filename, so existing `requires jfalkordb;` declarations are unaffected; it is
  now guaranteed rather than incidental.

## [0.10.1](https://github.com/FalkorDB/JFalkorDB/compare/v0.10.0...v0.10.1) (2026-08-13)


### Bug Fixes

* four correctness bugs in reply parsing and procedure encoding ([#383](https://github.com/FalkorDB/JFalkorDB/issues/383)) ([f197a46](https://github.com/FalkorDB/JFalkorDB/commit/f197a4603aff67091da7986eede459b552b2a369))

## [0.10.0](https://github.com/FalkorDB/JFalkorDB/compare/v0.9.1-SNAPSHOT...v0.10.0) (2026-07-23)


### ⚠ BREAKING CHANGES

* harden the Cypher parameter path against injection ([#317](https://github.com/FalkorDB/JFalkorDB/issues/317))

### Features

* add CompletableFuture async facade (AsyncGraph / AsyncFalkorDB) ([#355](https://github.com/FalkorDB/JFalkorDB/issues/355)) ([f6410c3](https://github.com/FalkorDB/JFalkorDB/commit/f6410c3c18e82368630617bc9dbdfc409f90ee01))
* add FalkorDB.builder() fluent configuration API ([#349](https://github.com/FalkorDB/JFalkorDB/issues/349)) ([59b0cce](https://github.com/FalkorDB/JFalkorDB/commit/59b0cced3faab043c43239ff426280ba4a0715fe))
* add JSpecify nullability to the public API ([#351](https://github.com/FalkorDB/JFalkorDB/issues/351)) ([6812aa9](https://github.com/FalkorDB/JFalkorDB/commit/6812aa98ddf9db98995869e51fa1cc67e4e343f9))


### Bug Fixes

* align Property.hashCode with its Integer/Long-normalizing equals ([#334](https://github.com/FalkorDB/JFalkorDB/issues/334)) ([42d3bc2](https://github.com/FalkorDB/JFalkorDB/commit/42d3bc22e9287be70979d01c7902025844605f91))
* harden the Cypher parameter path against injection ([#317](https://github.com/FalkorDB/JFalkorDB/issues/317)) ([e098807](https://github.com/FalkorDB/JFalkorDB/commit/e0988073a54dc5119dad6a10a7baadd4e03827a3))
* make Point equals/hashCode a consistent, drift-tolerant contract ([#330](https://github.com/FalkorDB/JFalkorDB/issues/330)) ([68d27a3](https://github.com/FalkorDB/JFalkorDB/commit/68d27a362340c47f0106586431908e9cdac78c81))


### Performance Improvements

* de-pin GraphCacheList cache refresh for virtual threads ([#354](https://github.com/FalkorDB/JFalkorDB/issues/354)) ([d9bca2f](https://github.com/FalkorDB/JFalkorDB/commit/d9bca2f708ac80adc7c0a8a90196d0fd1ce241d4))


### Documentation

* add runnable examples module (FalkorDB.builder()) ([#352](https://github.com/FalkorDB/JFalkorDB/issues/352)) ([57b90f4](https://github.com/FalkorDB/JFalkorDB/commit/57b90f483e3236f74f8d410077ba16fe48645d81))
* AI engineering guides + CONTRIBUTING + .editorconfig (Wave 1, PR 2/4) ([#298](https://github.com/FalkorDB/JFalkorDB/issues/298)) ([f5109c3](https://github.com/FalkorDB/JFalkorDB/commit/f5109c3b90931c83895fc4a28e95d3e1268ef3c5))
* document virtual-thread concurrency and pool tuning (Wave 5) ([#358](https://github.com/FalkorDB/JFalkorDB/issues/358)) ([08251f6](https://github.com/FalkorDB/JFalkorDB/commit/08251f69b4f9286d50808edf8173445910ca1673))
* PR 15 plan (FalkorDB.builder() + JSpecify nullability) — for review ([#348](https://github.com/FalkorDB/JFalkorDB/issues/348)) ([951295c](https://github.com/FalkorDB/JFalkorDB/commit/951295ce6b623b1ccae20042f24b21ad3820ed0a))
* Wave 3 PR 10c plan (enable release-please) ([#326](https://github.com/FalkorDB/JFalkorDB/issues/326)) ([9ceb32b](https://github.com/FalkorDB/JFalkorDB/commit/9ceb32bfb1714e9bd4c4ce0e07cc572bddf08829))
