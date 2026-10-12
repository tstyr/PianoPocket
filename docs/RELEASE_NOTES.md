# PianoPocket 0.5.0-alpha — 多層音源の発音抜け・ノイズ対策

同一ノートのマイク／ベロシティレイヤーが互いを奪う不具合を修正。既定512・最大1024サンプルボイス、メモリ予算に応じたLRU、優先読み込み・重複要求抑制、キャッシュ欠落／サンプル終端のフェードを追加。設定にスティール数・要求欠落・キャッシュページ数を表示します。

実Helsinki XFマッピングを使用したホスト80鍵テストで発音欠落・スティール・キャッシュミス・読み込みエラー0。Native tests、ASan/UBSan PASS。詳細はdocs/AUDIO_DENSITY_FIX.md。

Androidビルド・Lint・48 JVM tests・APK署名と16KiB整列を確認。実機／エミュレーター未接続。 イヤホン音質・Android端末上の高負荷演奏は未検証。発音数は有限で、上限超過時はボイススティールします。

## ダウンロード

Release Assets添付先の既存HTTP 401制限があるため、配布ブランチからダウンロードできます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.5.0-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.5.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.5.0-alpha-validation.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.5.0.txt)
