# Vendored dependency

`oboe/` is Google Oboe **1.9.3**, downloaded from
https://github.com/google/oboe/archive/refs/tags/1.9.3.tar.gz
on 2026-10-11.

Only the build-relevant CMakeLists, src, include, AUTHORS, README, license
and license marker are retained. Samples, apps, docs and tests were omitted.
There are no semantic changes to the upstream source. Application warning
flags are scoped to pocket_core instead of being propagated to Oboe, to
avoid turning upstream unused-parameter warnings into build failures.

See [oboe/LICENSE](oboe/LICENSE) for Apache License 2.0. PianoPocket's MIT
license does not replace this license.


## Export encoders (2026-10-12)

- `flac/`: libFLAC **1.4.3**, https://github.com/xiph/flac, pinned tag/commit `28e4f0528c76b296c561e922ba67d43751990599`. Retain build files, public headers, libFLAC and required share source directories; programs / tests / examples / docs disabled and omitted. libFLAC has BSD-3-Clause license (`COPYING.Xiph`); libFLAC++ and GPL programs are not linked. No semantic changes to libFLAC source.
- `lame/`: LAME **3.100** official release tar from https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz, SHA-256 `ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e`. LGPL-2.0-or-later (`COPYING`), dynamically linked `libmp3lame.so`. Retain all compiled encoder sources and headers; frontend, decoder and optional assembly/SIMD are not linked. Add `CMakeLists.txt` and ARM64 / Linux x86_64 portable `config.h`. UBSan fixes: VbrTag.c `~(-1 << n)` -> `~(~0u << n)`; quantize.c negative `s << shift` -> `s * (1 << shift)` (s in -4..-1, shift in 1..2). bitstream.c putbits2 / putbits_noheaders shifts cast val to unsigned before packing, avoiding signed overflow and preserving the resulting output bytes. Preserve the intended bit mask and gain values without undefined behavior. Encoder tests use self-generated tones.

APK carries license text in assets/encoder-licenses.txt and a settings viewer. Full corresponding buildable encoder source and relink / replacement instructions are provided in every source ZIP and [EXPORT_FORMATS.md](../docs/EXPORT_FORMATS.md). There is no separate paid or proprietary codec dependency.

## Bundled piano

Pocket Upright uses VSCO 2 Community Edition's Upright Piano recordings by Simon Dalzell / Ivy Audio, distributed by Versilian Studios / Sam Gossner under CC0 1.0 Universal. The sample bank, full license and attribution are in `app/src/main/assets/instruments/PocketUpright`. The adaptation uses 12 sampled keys and three velocity layers. See [source, changes and reproduction](../docs/BUNDLED_PIANO.md). No Pianobook recordings or user-imported instruments are distributed.
