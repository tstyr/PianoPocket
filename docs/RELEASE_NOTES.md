# PianoPocket 0.19.0 — バックグラウンド演奏

設定 →「音の設定」→「アプリを閉じても演奏」をオンにすると、画面オフや他のアプリを開いている間もMIDI再生・入力キーボードの演奏・録音を続けられます。通知から再生・一時停止・終了できます。初期設定はオフです。

曲ごとの設定保存と、内蔵音源・外部MIDI・両方の出力切り替えも含まれます。READMEと過去のリリース説明は、使い方と変更点を中心に整理しました。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.19.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.19.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.19.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.19.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

117件の自動テスト、Androidビルド、Lintを確認済み。背景での継続・録音保存を検査する端末テストAPKも作成していますが、開発環境でのAndroid実機試験は未実施です。通話などの音声割り込みやイヤホンの取り外しでは演奏を停止します。OSによる強制停止後の自動再開には対応していません。使い方と確認手順は[背景演奏](https://github.com/tstyr/PianoPocket/blob/v0.19.0-alpha/docs/BACKGROUND_PLAYBACK.md)をご覧ください。
