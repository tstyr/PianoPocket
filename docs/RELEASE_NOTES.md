# PianoPocket 0.11.0 — SFZ互換性と圧縮サンプル

- SFZのラウンドロビン、ランダム選択、独自ベロシティ曲線、負のベロシティ追従に対応しました。
- 基本フィルターを追加。近似アルゴリズムであることは音源パネルに表示します。
- FLAC・OGG Vorbisサンプルを準備時にWAVへ展開し、既存の低遅延エンジンで演奏できます。
- SFZの再生終了位置を1フレーム正確に扱うよう修正しました。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.11.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.11.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.11.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.11.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

未対応の指定は警告表示。OGG Opus・キー切替・LFOは未対応です。ビルドと自動テストを確認済み、開発環境でのAndroid実機試験は未実施です。
