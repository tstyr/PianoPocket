# PianoPocket 0.16.0 — 転調と外部MIDI出力

- 「その他 → 転調」で半音ずつ±24半音。再生・ロール・練習・MIDI／音声／動画の書き出しに反映します。
- 「その他 → MIDI出力」、または「設定 → MIDI入出力」で、USB／Bluetooth MIDI機器の送信先を選べます。MIDI再生・画面鍵盤を送信し、入力キーボードの転送も切り替えできます。
- ノート・ベロシティ・CC・Pitch Bend・Program Change・pressureを送信。ファイルのチャンネルを維持し、画面鍵盤の送信チャンネルを選べます。
- 一時停止・シーク・リピート・曲の切り替え・接続解除・背景移行で残音を止めます。シークとリピートではバンク・音色・ペダル等の状態を復元します。
- 接続先を変えたときの古いイベントを除外し、出力先から戻る入力の再送を抑止。MIDI送信があふれても、内蔵音源の再生を維持します。

USB機器を接続、またはBluetooth MIDIを検索・接続してから送信先を選んでください。外部MIDI出力は起動時にオフです。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.16.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.16.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.16.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.16.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

110件のJVMテスト・Androidビルド・Lint・ネイティブ音声／MIDIテスト・ASan/UBSanを通過。音声処理中の動的確保は0。USB／Bluetooth MIDI機器での実機送信と遅延・同期の測定は未実施です。SysEx・MIDI Clock・MIDI 2.0 UMPは送信しません。
