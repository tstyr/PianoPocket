# PianoPocket 0.14.1 — 旧名のローパスフィルターに対応

- Decent Sampler音源の `lowpass_4pl` に対応しました。これを含むプリセットで「Unsupported effect」と表示され、読み込めなかった問題を修正しています。
- 公式仕様では `lowpass_4pl` は `lowpass` の旧名です。既存のローパス処理を使用し、frequency・resonance・enabled、エフェクト順序、つまみの連動を維持します。
- 別の未対応エフェクトは、その名前を含む日本語のエラーを表示します。
- 本番パーサーで旧名と現行名を比較し、自作サンプルで書き出したWAVがバイト単位で一致することを確認しました。

更新後、エラーになった音源をもう一度読み込んでください。インポート自体が完了していない場合は、ファイルを選び直してください。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.14.1-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.14.1-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.14.1-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.14.1.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

97件のJVMテスト・Androidビルド・Lint・ネイティブ音声テスト・ASan/UBSanを通過。今回の音源の実ファイル全体とAndroid実機は未検証です。DSPはPianoPocketの実装で、純正との録音比較は未実施です。
