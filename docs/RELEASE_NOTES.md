# PianoPocket 0.14.0 — ライブラリと設定を使いやすく

- ピアノアイコンから、MIDI・音源のどちらのライブラリへも進めます。演奏画面、ライブラリ、設定で同じメニューを使えます。
- 設定の最初の画面は「表示」「音の設定」「MIDIキーボード」「アプリについて」と「詳細設定」に整理しました。
- ドレミ・ロール・横画面・FPSは「表示」。音の出方は「反応を優先」「音切れを抑える」の2つから選べます。
- バッファ・発音数・メモリ、動作状況、音の診断は「詳細設定」から必要な画面だけ開けます。
- 長い説明や診断値の常設をやめ、普段の設定は短い言葉で表示。操作結果のメッセージは一時的に表示します。
- 戻るボタン・Androidの戻る操作は1つ前の設定画面へ戻ります。画面の切り替え時はスクロールを先頭へ戻します。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.14.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.14.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.14.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.14.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

94件のJVMテスト・Androidビルド・Lintを通過。実機での画面表示・タッチ操作は未検証です。
