# PianoPocket 0.13.0 — 高品質ピアノの直接ダウンロード

- 音源ライブラリから「Pocket Upright HQ」を直接ダウンロードできます。専用サーバー・APIキー・アカウントは不要です。
- 23収録キー、3段階のベロシティ、69サンプル、24bitステレオ、元の残響を全収録。約105MiB、CC0ライセンスです。
- 背景での取得、キャンセル、途中再開、容量確認、各ファイルとパッケージ全体のSHA-256検証に対応しました。
- 取得後はオフラインで利用でき、既存の軽量版と音源も維持します。音源容量には展開済みWAVを含め、音源削除時はRAMも解放します。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.13.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.13.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.13.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.13.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

取得時だけ通信します。背景取得・回線切替・通知の実機試験は未実施です。公開データの再取得・SHA-256、94件の自動テスト、ビルド、実音源のホスト発音・80音同時発音を確認しました。[配布方式と出典](https://github.com/tstyr/PianoPocket/blob/v0.13.0-alpha/docs/DOWNLOADS.md)。
