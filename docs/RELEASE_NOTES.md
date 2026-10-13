# PianoPocket 0.8.0-alpha — 速度・A–Bリピート・トラック練習

音程を維持した0.25〜2倍のMIDI速度変更、音声時計に基づくA–Bリピート、トラック表示・ノートミュートを追加。区間のペダル・CC・Pitch Bendを復元し、ライブ演奏チャンネルを保ちます。

59 JVM tests、Native tests、ASan/UBSan、実Helsinkiのホスト音声書き出し回帰PASS。詳しい操作・共有チャンネル・シーク・書き出しの制約はdocs/PRACTICE.md。

Androidビルド・Lint・59 JVM tests・APK署名と16KiB整列を確認。実機／エミュレーター未接続。 操作UIと切替時の聴感は実機未検証。区間前に始まったノートの途中波形は復元しません。練習設定は元MIDIの書き出しには適用しません。

## ダウンロード

Release Assets添付先の既存HTTP 401制限があるため、配布ブランチからダウンロードできます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.8.0-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.8.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.8.0-alpha-validation.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.8.0.txt)
