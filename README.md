# PianoPocket distribution files

Verified ARM64 debug APKs, source archives, validation reports and original test material.

Latest: [PianoPocket 0.4.2-alpha](https://github.com/tstyr/PianoPocket/releases/tag/v0.4.2-alpha).

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.2-alpha-arm64-debug.apk)
- [Source](PianoPocket-0.4.2-alpha-source.zip)
- [Validation](PianoPocket-0.4.2-alpha-validation.zip)
- [Original MIDI study](PocketStudy.mid)
- [Original sample fixture](PocketFixture.zip)
- [SHA-256 checksums](SHA256SUMS-0.4.2.txt)

Older 0.2.0, 0.3.0, 0.3.1, 0.4.0 and 0.4.1 files remain available. No user MIDI, Helsinki/Pianobook samples, IRs, images or original presets are distributed. Source development is on main; this separate branch hosts packages only. The APK uses the same debug signing certificate as 0.2.0 / 0.3.1 and can be installed as an update. Physical Android device/SAF/MIDI/latency verification remains outstanding.

Source archive includes corresponding LAME LGPL encoder sources, portable build config and unsigned-shift fixes, plus libFLAC BSD sources. The APK embeds encoder license notices and links LAME dynamically; modification / relink instructions are in docs/EXPORT_FORMATS.md. FLAC / MP3 independently decoded locally; Android AAC / M4A test compiled but not executed (no device).
