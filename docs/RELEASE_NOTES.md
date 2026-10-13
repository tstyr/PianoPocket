# PianoPocket 0.7.0-alpha — 音源設定の自動保存・お気に入り

マイク音量、XMLコントロール、エフェクト、IR、Output、発音上限を音源ごとに自動保存・復元。名前付きお気に入り32件を保存・呼び出し・削除でき、初期値へ戻せます。構成変更時の分離、原子的書込み、破損退避に対応。

57 JVM tests PASS。詳しい保存範囲と手順はdocs/SAVED_SOUNDS.md。

Androidビルド・Lint・57 JVM tests・APK署名と16KiB整列を確認。実機／エミュレーター未接続。 再起動と設定復元の実機UI・聴感テストは未実施。お気に入り呼び出しは音源の再準備を伴います。

## ダウンロード

Release Assets添付先の既存HTTP 401制限があるため、配布ブランチからダウンロードできます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.7.0-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.7.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.7.0-alpha-validation.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.7.0.txt)
