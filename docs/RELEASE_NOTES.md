# PianoPocket 0.26.0 — 書き出し音量の自動調整

音声・動画の書き出しに「音量を自動調整」を追加しました。曲全体を解析し、左右のバランスと強弱を保った一定倍率で調整します。WAV・FLAC・MP3・M4A・MP4・MOVに対応し、設定を保存します。

Float32で生成してから調整し、整数形式はディザを加えて一度だけ変換します。増幅は最大+12 dB、ほぼ無音のファイルは増幅しません。進捗表示・キャンセル・一時ファイル削除に対応しています。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.26.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.26.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.26.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.26.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

146件のJVMテスト、Androidビルド、Lintを確認済み。自作和音で16/24/32bit変換と本番FLAC・MP3エンコーダーを検証し、FLACのPCM一致と全MP3ビットレートのデコードを確認しました。Android AAC・動画の実機試験は未実施です。LUFS正規化・規格準拠True Peak測定ではなく、元音源の歪み除去は行いません。[音量調整の仕様](https://github.com/tstyr/PianoPocket/blob/v0.26.0-alpha/docs/EXPORT_VOLUME.md)。
