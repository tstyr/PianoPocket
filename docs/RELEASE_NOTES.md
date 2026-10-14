# PianoPocket 0.18.0 — 音の出力を切り替え

「その他 → 鳴らす音」で内蔵音源・外部MIDI・両方を切り替えできます。設定の音とMIDIの画面からも選べます。

外部MIDIだけで鳴らすときも保存済みの音量を保ち、接続が切れた場合は内蔵音源に戻ります。書き出しには選択した音源と保存済みの音量を使います。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.18.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.18.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.18.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.18.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

Androidビルド・117件の自動テスト・Lintを確認済み。USB／Bluetooth MIDI機器での実機試験は未実施です。切り替え時点で押されている鍵盤は再送せず、以後のイベントを送ります。
