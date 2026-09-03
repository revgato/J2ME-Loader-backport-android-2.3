# ADR 001: Low-memory conversion in a private process

## Status

Accepted

## Decision

The API 10 launcher binds to `LegacyConversionService`, which runs in the private `:converter`
process and reports progress, logs, and a terminal install result with `Messenger`. The launcher
keeps a non-cancelable progress modal while conversion is active. If the converter process is
disconnected, the modal becomes an explicit failure instead of silently disappearing.

The converter uses a genuinely synchronous dx path when `--num-threads=1`. Archive entries and DEX
files with a known size are read into one exact-sized array, avoiding the temporary
`ByteArrayOutputStream.toByteArray()` copy that exhausted the IS14SH heap.

Class files are staged into batches of at most 256 KiB uncompressed data or 64 classes. A class
larger than 8 MiB is rejected; a class larger than the batch limit is kept in its own batch.
If dx reports `OutOfMemoryError`, the failed DEX is removed and that batch is bisected by class
payload size, preserving archive order, until conversion succeeds or one named class still cannot
be converted. `converted.dex`, `converted.2.dex`, and so on are committed only after each DEX has
been written and verified as version 035. `converted.dex.conf` records
`J2ME-Loader-Dex-Count: N`. The runtime loads all parts in numeric order using the API 3
`DexClassLoader` path separator.

After every dx invocation, the converter releases its graph/output references, clears the
`RegisterSpec`, `Prototype`, `CstType`, and `Type` intern tables, and requests garbage collection.
This cleanup runs on success, I/O failure, and OOM so a retry or later installation does not inherit
the previous conversion's heap state.

The dx backend emits an explicit `int-to-byte`, `int-to-char`, or `int-to-short` before the
corresponding narrow array store. JVM `bastore`, `castore`, and `sastore` accept a category-1
`int`, but API 10's verifier requires the source register to carry the narrowed element type; the
extra instruction keeps old obfuscated MIDlets loadable without changing their behavior.

## Consequences

- Conversion memory is bounded by one class plus the current dx structures, and a game conversion
  cannot poison the launcher process after an OOM.
- All parts and the config are assembled in a staging directory before the old game directory is
  atomically replaced, preserving existing config and RMS data on failure.
- Existing single-DEX directories remain compatible because a missing count key means `N=1`.
- The repair cache fingerprints every DEX part, so changing or omitting any part invalidates a
  derived compatibility DEX.
