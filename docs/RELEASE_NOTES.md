# PianoPocket 0.6.0-alpha — MP4／MOV動画書き出し

現在の音源・マイク・IR・エフェクトの音声と、ロール・鍵盤を合わせてMP4／MOVへ保存。720p／1080p・30／60fps、ドレミ設定・横ズーム・黄色SUSを反映し、残響追加・進行状況・キャンセルに対応。端末で対応する解像度・FPSだけを選べます。

MP4はisom、MOVはQuickTime qtコンテナ。独立したFFmpegによる30fps/48kHz・60fps/44.1kHzの計4本の復号で全映像フレームと音声が一致。詳しい仕様・制約・実機確認はdocs/VIDEO_EXPORT.md。

このAPKには0.4.5〜0.5.0で機能ごとに公開した左下FPS表示、最大120FPS設定、目のアイコンによるUI非表示、発音抜け／ストリーミングノイズ対策も含みます。

Androidビルド・Lint・51 JVM tests・APK署名と16KiB整列を確認。実機／エミュレーター未接続。 端末のSurface H.264＋AAC動画生成・GPU描画・長時間同期・イヤホン音質・120Hz動作は未検証。Android test APKのビルド済み／実行未実施。発音数は有限、既定512／最大1024サンプルボイス。

## ダウンロード

Release Assets添付先の既存HTTP 401制限があるため、配布ブランチからダウンロードできます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.0-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.0-alpha-validation.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.6.0.txt)
