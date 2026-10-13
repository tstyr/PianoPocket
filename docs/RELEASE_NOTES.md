# PianoPocket 0.6.1-alpha — 音声切り分けと診断記録

通常／FXなし／左右別テスト音を比較。60秒の有界診断でXRun・キャッシュミス・ボイス置換・負荷をコピーできます。未取得値とカウンターリセットを区別。

利用者から有線で0.6.0のノイズが残っていないとの報告を受けています。開発環境では実機未接続。詳細はdocs/AUDIO_DIAGNOSTICS.md。Native tests・ASan/UBSan PASS。

Androidビルド・Lint・53 JVM tests・APK署名と16KiB整列を確認。実機／エミュレーター未接続。 診断UIと出力経路の実機比較は未実施。FXなしでも残響維持のためFX処理は継続。

## ダウンロード

Release Assets添付先の既存HTTP 401制限があるため、配布ブランチからダウンロードできます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.1-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.1-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.6.1-alpha-validation.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.6.1.txt)
