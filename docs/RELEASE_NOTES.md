# PianoPocket 0.27.0 — アプリ内からAPKを更新

「設定 → アプリについて → 更新を確認」から、新しい版の変更点を見てAPKを取得できます。GitHubから直接ダウンロードし、通信中断後の再開、通知での進捗・キャンセルに対応しています。取得済みAPKは再起動後も使えます。

サイズ・SHA-256・アプリID・バージョンと現在のアプリの署名を検証し、Androidの画面からインストールします。更新確認と取得は手動です。音源・MIDI・保存設定は更新後も引き継ぎます。

スマホ向けMIDI編集と、音声・動画の書き出し音量の自動調整も含まれています。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.27.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.27.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.27.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.27.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

151件のJVMテスト、Androidビルド、Lintを確認済み。本番のHTTPS取得処理で公開APKのサイズ・SHA-256を検証しました。実機での提供元の許可・インストール画面は未検証です。[更新の使い方](https://github.com/tstyr/PianoPocket/blob/v0.27.0-alpha/docs/APP_UPDATES.md)。

Audio to MIDIは実現性を調査し、公式AIモデルのPCでの推論を確認しました。今回のAPKにはAI変換機能は含めていません。[調査結果](https://github.com/tstyr/PianoPocket/blob/v0.27.0-alpha/docs/AUDIO_TO_MIDI.md)。
