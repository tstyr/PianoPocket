# PianoPocket 0.21.0 — SFZの奏法と排他グループ

SFZのキースイッチで奏法を切り替えられます。音源パネルから画面鍵盤の奏法も選べます。

排他グループ、4ポールのフィルター、単一CC条件のサンプル選択を追加しました。シークやA–Bリピートでも内部の奏法状態を復元します。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.21.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.21.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.21.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.21.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

123件の自動テスト、Androidビルド、Lint、ネイティブ音声テスト、ASan／UBSanを確認済み。音声処理内の動的確保は0。新機能のAndroid実機試験は未実施です。フィルターは近似処理です。[対応範囲](https://github.com/tstyr/PianoPocket/blob/v0.21.0-alpha/docs/SFZ.md)。
