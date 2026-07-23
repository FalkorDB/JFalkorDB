# Changelog

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
