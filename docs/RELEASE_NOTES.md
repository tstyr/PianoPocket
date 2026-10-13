# PianoPocket 0.10.0 — SFZと内蔵ピアノ

- SFZ音源を読み込めるようになりました。キー・ベロシティのマッピング、エンベロープ、チューニング、ループ、include・マクロに対応しています。
- 内蔵ピアノ「Pocket Upright」を追加。CC0の実録音、ステレオ、3段階のベロシティで、初回起動から演奏できます。
- 音源ライブラリから内蔵ピアノを復元できます。既存の音源と保存設定は引き続き使えます。
- READMEと音源の読み込み案内を整理しました。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.10.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.10.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.10.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.10.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

SFZの未対応設定は音源パネルに警告を表示します。[対応範囲](https://github.com/tstyr/PianoPocket/blob/v0.10.0-alpha/docs/SFZ.md)・[内蔵音源の出典](https://github.com/tstyr/PianoPocket/blob/v0.10.0-alpha/docs/BUNDLED_PIANO.md)。ビルドと自動テストは確認済み、開発環境でのAndroid実機試験は未実施です。
