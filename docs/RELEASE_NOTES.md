# PianoPocket 0.4.0-alpha — Landscape performance view

横画面のロールと鍵盤を中心に、演奏画面を再構成しました。カタカナのドレミを維持し、基本操作をアイコンにまとめました。

- 横画面優先・全画面表示。ロール上の小さな操作バー、下部の88鍵／2オクターブ鍵盤。
- 再生・録音・全音停止・音源パネルをアイコンで操作。48dpのタッチ領域と長押し説明。︙から設定・書き出しへ。
- 音源パネルは「ミキサー / エフェクト / 情報」。実XMLのマイクを縦フェーダーに並べ、IR・Reverb Mixとチェーンの有効／無効を整理。鍵盤の位置を変えずに操作できます。
- ドレミのオン／オフと横画面優先の設定を保存。ライブラリは自動回転へ対応。
- MIDI再生・ライブ録音・WAV/MIDI書き出し、0.3.1の音割れ対策を維持。

Windows VSTホストは未実装です。今回の音源パネルは動作するネイティブサンプラーの操作です。Helsinkiの音源・IR・XML原本は同梱していません。

Android ARM64ビルド、36 JVM tests、native suite、Lint errors 0、v2署名・16KiB整列を確認。旧版と同じ署名で上書き更新できます。新画面の表示・タッチ・USB/BLE・実測レイテンシは開発環境で実機未検証です。

[操作・実機確認手順](https://github.com/tstyr/PianoPocket/blob/main/docs/PERFORMANCE_UI.md)

## ダウンロード

配布用release-artifactsブランチから取得できます。Assetsアップロード先の認証エラーが継続しており、直接添付は未完了です。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.0-alpha-arm64-debug.apk)
- [ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.0-alpha-validation.zip)
- [自作テストMIDI](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketStudy.mid)
- [自作テスト音源](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketFixture.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.4.0.txt)
