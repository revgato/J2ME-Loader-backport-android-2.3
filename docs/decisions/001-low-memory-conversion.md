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

Class files are staged into batches of at most 512 KiB uncompressed data or 128 classes. A class
larger than 8 MiB is rejected; larger games produce `converted.dex`, `converted.2.dex`, and so on.
`converted.dex.conf` records `J2ME-Loader-Dex-Count: N`. The runtime loads all parts in numeric order
using the API 3 `DexClassLoader` path separator.

## Consequences

- Conversion memory is bounded by one class plus the current dx structures, and a game conversion
  cannot poison the launcher process after an OOM.
- All parts and the config are assembled in a staging directory before the old game directory is
  atomically replaced, preserving existing config and RMS data on failure.
- Existing single-DEX directories remain compatible because a missing count key means `N=1`.
- The repair cache fingerprints every DEX part, so changing or omitting any part invalidates a
  derived compatibility DEX.
