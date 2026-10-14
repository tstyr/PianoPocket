# PianoPocket 0.16.1 — 転調とMIDI出力・書き出しの調整

- 「その他 → 転調」で半音ずつ±24半音。再生・ロール・練習・MIDI／音声／動画の書き出しに反映します。
- 「その他 → MIDI出力」、または「設定 → MIDI入出力」でUSB／Bluetooth MIDI機器への送信先を選べます。入力キーボードの転送も切り替えできます。
- オクターブだけ転調したとき、元のMIDIの調号をそのまま保つよう修正しました。C♯の調がD♭表記に置き換わる等の不要な変更を防ぎます。

外部MIDI出力は起動時にオフです。MIDIファイルのチャンネルを維持し、画面鍵盤の送信チャンネルを選べます。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.16.1-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.16.1-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.16.1-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.16.1.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

111件のJVMテスト・Androidビルド・Lintを通過。0.16.0でネイティブ音声／MIDIテスト・ASan/UBSanを通過しており、この版のネイティブコードは同一です。USB／Bluetooth MIDI機器での実機送信と遅延・同期の測定は未実施です。SysEx・MIDI Clock・MIDI 2.0 UMPは送信しません。
