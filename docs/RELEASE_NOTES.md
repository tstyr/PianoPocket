# PianoPocket 0.24.0 — SF3圧縮SoundFontに対応

音源ライブラリからSF3を読み込めます。Vorbis圧縮サンプルを準備時に展開し、音色を選んでオフラインで演奏できます。SF2と同じトラック別音源選択・音声／動画書き出しを使えます。

SF2対応、SFZの奏法・排他グループ、再生設定を反映した書き出し、トラック別音源も含まれます。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.24.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.24.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.24.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.24.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

135件の自動テスト、Androidビルド、Lintを確認済み。検証用に生成したSF3の520個のVorbisサンプルを本番デコーダーで復号し、136音色の解析とピアノの音声生成を確認しました。SF3は展開用の空き容量が必要です。追加機能のAndroid実機試験は未実施です。[対応範囲](https://github.com/tstyr/PianoPocket/blob/v0.24.0-alpha/docs/SOUNDFONT.md)。
