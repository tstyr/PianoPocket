# PianoPocket 0.23.0 — トラック別の音源選択

「その他 → 練習・メトロノーム」のトラック欄から、各トラックの音源やSoundFont音色を選べます。ピアノ・弦・ドラムを別々に鳴らし、割り当てを曲ごとに保存します。

同じチャンネル・音程でもノートオフをトラック別に管理します。音源ごとのマイク・エフェクトを分け、音声・動画の書き出しにも反映します。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.23.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.23.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.23.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.23.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

132件の自動テスト、Androidビルド、Lint、ネイティブ音声テスト、ASan／UBSanを確認済み。音声処理内の動的確保は0。主音源に加えて8種類まで。同じ音源は共有できます。新機能のAndroid実機試験は未実施です。[使い方・上限](https://github.com/tstyr/PianoPocket/blob/v0.23.0-alpha/docs/TRACK_INSTRUMENTS.md)。
