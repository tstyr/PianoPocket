# PianoPocket 0.24.1 — SoundFont対応と音程の調整

SF2・SF3の読み込み、SFZの奏法・排他グループ、再生速度・ミュート・A–B区間を反映した書き出し、トラック別の音源選択に対応しています。

SoundFontでルート音程に既定値−1が明示されている場合、録音したサンプルの音程を使うよう修正しました。以前の版で該当するSoundFontを読み込んだ場合は、再インポートしてください。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.24.1-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.24.1-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.24.1-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.24.1.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

136件の自動テスト、Androidビルド、Lintを確認済み。SF2・SF3の136音色の解析とネイティブ音声生成を確認しています。0.23.0でネイティブ音声テスト・ASan／UBSanを通過しており、以降ネイティブコードは変更していません。追加機能のAndroid実機試験は未実施です。[SoundFontの対応範囲](https://github.com/tstyr/PianoPocket/blob/v0.24.1-alpha/docs/SOUNDFONT.md)・[トラック別音源](https://github.com/tstyr/PianoPocket/blob/v0.24.1-alpha/docs/TRACK_INSTRUMENTS.md)。
