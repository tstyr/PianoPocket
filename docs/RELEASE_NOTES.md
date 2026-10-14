# PianoPocket 0.15.0 — MIDIの転調に対応

- 「その他 → 転調」で、MIDIを半音ずつ±24半音まで変更できます。1オクターブ単位の変更・元の音程へ戻す操作もあります。
- 再生音、ロール、鍵盤の再生表示、練習の待機キー、MIDI・音声・動画の書き出しに同じ転調を反映します。
- ドラムの10チャンネルは転調せず、ノートオフ・ポリプレッシャー・調号はノートと合わせて移動します。
- 元のMIDIファイルとテンポ・時間は保持。別のMIDIを選ぶと転調は0に戻ります。画面鍵盤・リアルタイム入力は元の音程です。MIDI音域外へ移るノートは除外し、警告を表示します。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.15.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.15.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.15.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.15.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

104件のJVMテスト・Androidビルド・Lintを通過。Android実機での画面・演奏操作は未検証です。
