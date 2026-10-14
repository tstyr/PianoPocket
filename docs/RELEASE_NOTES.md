# PianoPocket 0.20.0 — SoundFont SF2に対応

音源ライブラリからSF2を読み込めます。含まれる音色をBank・Program番号付きで追加し、画面鍵盤やMIDIで演奏できます。インポート後はオフラインで利用できます。

Galaxy S26での利用者による動作確認報告をREADMEに追記しました。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.20.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.20.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.20.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.20.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

Androidビルド・120件の自動テスト・Lintを確認済み。TimGM6mbの136音色を解析し、展開したピアノでネイティブ音声生成も確認しました。SF2の変調や内蔵エフェクトには未対応部分があり、警告を表示します。SF2機能のAndroid実機試験は未実施です。[対応範囲](https://github.com/tstyr/PianoPocket/blob/v0.20.0-alpha/docs/SOUNDFONT.md)。
