# PianoPocket 0.9.0-alpha — 背景書き出し・残り時間・5秒プレビュー

書き出しをプロセス所有の通知付きサービスへ移行。画面／ViewModelから独立したジョブ、通知とアプリ内キャンセル、工程別ETA、5秒＋1秒tailのMP4プレビューを追加。形式・解像度などを保持して本番書き出しへ戻れます。演奏bankを解放してメモリを抑え、終了後に音源設定・曲内CCを復元します。

62 JVM tests、Native tests・ASan/UBSan、Androidビルド・Lint・実機テストAPKビルドPASS。Activity破棄後の実動画生成とキャンセルを検査するAndroid testsは用意済み／未実行。詳細はdocs/BACKGROUND_EXPORT.md。

今回選択された改善1・2・3・4・6を、0.6.1（診断）、0.6.2（安定優先）、0.7.0（音源設定）、0.8.0（練習）、0.9.0（書き出し）としてそれぞれコミット・プッシュ・公開しました。

Androidビルド・Lint・62 JVM tests・APK署名と16KiB整列を確認。実機／エミュレーター未接続。 通知・画面オフ・タスク除去・実Codec・VideoViewの実機成功は未確認。OS／省電力による終了・強制停止を超えて継続を保証せず、プロセス死亡後の自動再開は未対応。プレビューの長音再構築は近似。

## ダウンロード

Release Assets添付先の既存HTTP 401制限があるため、配布ブランチからダウンロードできます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.9.0-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.9.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.9.0-alpha-validation.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.9.0.txt)
