# Changelog

## [0.16.0](https://github.com/FalkorDB/JFalkorDB/compare/v0.15.0...v0.16.0) (2026-11-11)


### ⚠ BREAKING CHANGES

* Driver.close(), GraphPipeline.close() and GraphTransaction.close() no longer declare IOException. Code that catches IOException around them, with nothing else in the try throwing it, no longer compiles ("exception IOException is never thrown in body of corresponding try statement"). That includes try (Driver d = ...) { ... } catch (IOException e), which was the only way to use try-with-resources with these types until now. Remove the catch, or catch a broader exception. A class implementing one of these interfaces whose close() declares throws IOException must drop the throws clause. A Mockito stub such as doThrow(new IOException()).when(driver).close() now fails with "Checked exception is invalid for this method": throw an unchecked exception instead. Compiled code keeps working: the change is binary compatible.
* Query parameter names that aren't Cypher identifiers, such as `1`, `a b` or `é`, used to make the query methods throw IllegalArgumentException; they are now backtick-quoted and sent. Names that are still rejected (empty, containing a backtick, a NUL character or an unpaired surrogate, or a null key) throw IllegalArgumentException with new messages. On FalkorDB 4.x the query text must refer to such a name quoted, as `` $`1` ``.

### Features

* add type-checked Record.getValue overloads, deprecate the unchecked ones ([#523](https://github.com/FalkorDB/JFalkorDB/issues/523)) ([557e416](https://github.com/FalkorDB/JFalkorDB/commit/557e416776e9710c7ac633f0d5aab881b061f90a))


### Bug Fixes

* accept query parameter names that aren't Cypher identifiers ([#519](https://github.com/FalkorDB/JFalkorDB/issues/519)) ([1ee2fd1](https://github.com/FalkorDB/JFalkorDB/commit/1ee2fd126dcb58e84770db1cb916edae0b0615a5))
* correct the public API's nullability where null actually occurs ([#524](https://github.com/FalkorDB/JFalkorDB/issues/524)) ([4f5b973](https://github.com/FalkorDB/JFalkorDB/commit/4f5b973b264b38b38460d7dee7e9f22d590a8a02))
* let Driver, GraphPipeline and GraphTransaction close without a checked exception ([#522](https://github.com/FalkorDB/JFalkorDB/issues/522)) ([9fdd19f](https://github.com/FalkorDB/JFalkorDB/commit/9fdd19f12735fd2314809740a02eb05ea87dd86d))


### Performance Improvements

* copy literal runs when encoding string query parameters ([#520](https://github.com/FalkorDB/JFalkorDB/issues/520)) ([f145e1f](https://github.com/FalkorDB/JFalkorDB/commit/f145e1f1d9da9dd42210df3628dd7ae89c80a7ab))

## [0.15.0](https://github.com/FalkorDB/JFalkorDB/compare/v0.14.0...v0.15.0) (2026-11-03)


### ⚠ BREAKING CHANGES

* a query or profile whose reply can't be decoded because a schema lookup was redirected now fails with a GraphException saying the command ran, instead of being run again.
* a reply queued before deleteGraph() in the same pipeline or transaction, needing an id the client hadn't cached, now fails with GraphException instead of being looked up after the delete, where it either failed or silently got a recreated graph's names. syncAndReturnAll() and exec() decode before they learn whether the delete succeeded, so this also applies to a delete that fails there.
* explain(), copyGraph() and deleteGraph() on a Graph, and copyGraph() and deleteGraph() on a GraphContext, throw GraphException for a server error instead of the JedisDataException Jedis raised. GraphException extends JedisDataException, so catch (JedisDataException) still matches, but a catch of a Jedis subclass such as JedisAccessControlException (NOPERM) no longer does: look at getCause(). The message now starts with the cause's class name, as for the other wrapped commands.
* a query sent with a timeout now also obeys the connection's socket timeout. With the drivers FalkorDB.builder() builds nothing changes unless socketTimeout is set, but a pool passed to DriverImpl(Pool) gets Jedis' 2000 ms default, so on such a pool query(q, 30_000) now fails with a read timeout after 2 s while the server may still run it. Set the socket timeout above your longest query timeout, or to 0. A negative timeout throws IllegalArgumentException instead of running with the server's default.
* callProcedure rejects with IllegalArgumentException what it used to send: a backtick-quoted procedure name, a YIELD column with an AS alias or holding several columns ("node, score"), and anything that isn't identifiers. A null procedure, args, kwargs, argument or column throws IllegalArgumentException instead of NullPointerException or sending "null". Pass one column per list element, without aliases, and read results by those names.
* a reply that names a label, relationship type or property key the graph doesn't list now fails with GraphException instead of IndexOutOfBoundsException, and an id beyond the int range fails instead of wrapping to another entry. GraphException is a JedisDataException, so GraphPipeline.syncAndReturnAll() and GraphTransaction.exec() now return it as that command's result instead of throwing it.
* configGet("*") now throws IllegalArgumentException instead of returning a meaningless string. Use configGetAll() to read every parameter. Closes #489 Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

### Features

* add configGetAll() and make configGet() reject the "*" wildcard ([#495](https://github.com/FalkorDB/JFalkorDB/issues/495)) ([06327d4](https://github.com/FalkorDB/JFalkorDB/commit/06327d476e038c80bc087bd4b0989693922bd32b))


### Bug Fixes

* clear the schema cache when deleteGraph runs, and decode queued replies safely ([#500](https://github.com/FalkorDB/JFalkorDB/issues/500)) ([a5a1448](https://github.com/FalkorDB/JFalkorDB/commit/a5a14483d3d4ef2a10171c3b1b51d3efd30e1095))
* **deps:** bump com.google.errorprone:error_prone_core ([#480](https://github.com/FalkorDB/JFalkorDB/issues/480)) ([7ee2556](https://github.com/FalkorDB/JFalkorDB/commit/7ee2556cfe907489a43807e874ade94b1c42417c))
* do not close the borrowed connection in GraphContext.deleteGraph() on error ([#493](https://github.com/FalkorDB/JFalkorDB/issues/493)) ([a777901](https://github.com/FalkorDB/JFalkorDB/commit/a777901f513125b2378adfa5652073f334a0cc7e))
* follow ASK redirects, and move single-primary reads off a dead primary ([#501](https://github.com/FalkorDB/JFalkorDB/issues/501)) ([142a846](https://github.com/FalkorDB/JFalkorDB/commit/142a846e6ea7a169e4de9cbbfe369d909bf4c038))
* keep the socket timeout for queries sent with a timeout, and reject negative timeouts ([#498](https://github.com/FalkorDB/JFalkorDB/issues/498)) ([ce47943](https://github.com/FalkorDB/JFalkorDB/commit/ce4794354bc2724247c54d7f71652abe35935203))
* replace stale entries and reject unknown ids in the graph schema cache ([#496](https://github.com/FalkorDB/JFalkorDB/issues/496)) ([2608edb](https://github.com/FalkorDB/JFalkorDB/commit/2608edb35cf1c0a337933744227812b1ecfe5895))
* report explain, copyGraph and deleteGraph errors as GraphException ([#499](https://github.com/FalkorDB/JFalkorDB/issues/499)) ([259ba54](https://github.com/FalkorDB/JFalkorDB/commit/259ba54f21d841da7101c71173805c3232823585))
* validate procedure names and YIELD columns in callProcedure ([#497](https://github.com/FalkorDB/JFalkorDB/issues/497)) ([7bbce00](https://github.com/FalkorDB/JFalkorDB/commit/7bbce00f1f0536fca0aedd36a18fba7e6e284485))


### Documentation

* import the engineering conventions into CLAUDE.md ([#494](https://github.com/FalkorDB/JFalkorDB/issues/494)) ([c8fe919](https://github.com/FalkorDB/JFalkorDB/commit/c8fe919b2ca6a0a45c7b95ec7e299ea054267bcf))

## [0.14.0](https://github.com/FalkorDB/JFalkorDB/compare/v0.13.1...v0.14.0) (2026-10-18)


### Features

* support Redis Cluster ([#469](https://github.com/FalkorDB/JFalkorDB/issues/469)) ([1d2fe4c](https://github.com/FalkorDB/JFalkorDB/commit/1d2fe4c4c6a89a309736f9528582a7adaa770475))


### Documentation

* fix stale links ([#470](https://github.com/FalkorDB/JFalkorDB/issues/470)) ([daae63d](https://github.com/FalkorDB/JFalkorDB/commit/daae63d49a486b665be5dbf3cc1b506dda422825))

## [0.13.1](https://github.com/FalkorDB/JFalkorDB/compare/v0.13.0...v0.13.1) (2026-10-07)


### Bug Fixes

* **build:** restore Maven 3.9.16 so snapshot deploys authenticate ([#456](https://github.com/FalkorDB/JFalkorDB/issues/456)) ([34f6249](https://github.com/FalkorDB/JFalkorDB/commit/34f62499b9c181bc6808cf13ca15ce7a59bf7ff8))

## [0.13.0](https://github.com/FalkorDB/JFalkorDB/compare/v0.12.0...v0.13.0) (2026-09-22)


### Features

* add Redis Sentinel support ([#434](https://github.com/FalkorDB/JFalkorDB/issues/434)) ([25cee11](https://github.com/FalkorDB/JFalkorDB/commit/25cee11d8e25dcecd52ee9fc81dc2f8ad21e3f80))


### Documentation

* add a Redis Sentinel connection example ([#435](https://github.com/FalkorDB/JFalkorDB/issues/435)) ([b1b6add](https://github.com/FalkorDB/JFalkorDB/commit/b1b6add8d468f2a01b51b42babab2041c63f1eb7))

## [0.12.0](https://github.com/FalkorDB/JFalkorDB/compare/v0.11.1...v0.12.0) (2026-09-08)


### Features

* let the no-arg FalkorDB.driver() pick up connection settings from the environment ([#425](https://github.com/FalkorDB/JFalkorDB/issues/425)) ([5540802](https://github.com/FalkorDB/JFalkorDB/commit/5540802cdd8470acfa546149a2ca39fb36fc3dec))

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
