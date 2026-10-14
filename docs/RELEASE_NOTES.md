# PianoPocket 0.13.1 — 再生バーをコンパクトに

- 横画面の再生ボタンとシークバーを1段に統合。通常の再生バーを72dpから48dpに縮め、ロールビューが隠れる範囲を減らしました。
- シークの線は4dp、つまみは細く。ボタンとシーク操作には48dpの高さを確保しています。
- 音源の名前ボタンとミキサーの重複を整理。音源・マイク・エフェクトはミキサーのアイコンから開けます。
- 狭い画面ではシークを常設せず、「その他 → 再生位置」から操作できます。MIDI選択も同じメニューから利用できます。
- 曲名・メッセージの位置はバーの実際の高さに追従。練習の待機表示や書き出し進行表示との重なりを防ぎます。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.13.1-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.13.1-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.13.1-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.13.1.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

94件のJVMテスト・Androidビルド・Lintを通過。実機での画面表示・タッチ操作は未検証です。
