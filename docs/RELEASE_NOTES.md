# PianoPocket 0.6.2-alpha — 安定優先の自動バッファ調整

低遅延／安定優先を切替・保存。安定優先は4バースト以上から開始し、XRun増加時に段階的に最大8へ増量します。経路容量、2秒の間隔、未取得値、カウンターリセットを扱い、演奏中の自動縮小を避けます。

Native tests・ASan/UBSan PASS。詳細はdocs/AUDIO_STABILITY.md。

Androidビルド・Lint・53 JVM tests・APK署名と16KiB整列を確認。実機／エミュレーター未接続。 実機のXRun改善・音質・追加遅延は未測定。バッファ増量は遅延を増やします。

## ダウンロード

Release Assets添付先の既存HTTP 401制限があるため、配布ブランチからダウンロードできます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.2-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.2-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.2-alpha-validation.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.6.2.txt)
