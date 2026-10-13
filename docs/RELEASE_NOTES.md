# PianoPocket 0.10.1 — 音源に応じたメモリ節約

- メモリ予算を上限として扱い、音源に必要な量だけキャッシュを確保するようにしました。
- 小さい音源ではRAM使用量が減り、大きい音源では従来どおり予算内で先読み・LRUを使えます。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.10.1-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.10.1-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.10.1-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.10.1.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

ビルド・自動テストを確認済み。開発環境でのAndroid実機試験は未実施です。
