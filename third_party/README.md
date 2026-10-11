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
