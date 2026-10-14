# PianoPocket 0.22.0 — 再生設定を反映した書き出し

書き出し画面で、現在の再生速度とミュートを反映できます。曲全体とA–B区間を選べます。MIDI・音声・動画に同じ設定を使い、元のMIDIは保持します。

区間前のペダルや音色を引き継ぎ、境界で残音を閉じます。動画プレビューにも同じ設定を反映します。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.22.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.22.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.22.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.22.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

128件の自動テスト、Androidビルド、Lintを確認済み。区間途中から続く音は、元の波形位置へシークせず区間先頭で再発音します。新しい書き出し画面・音声・動画のAndroid実機試験は未実施です。
