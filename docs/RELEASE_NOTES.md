# PianoPocket 0.4.2-alpha — MIDI選択・ドレミ表示

- MIDI追加の選択フィルターをMIDI用に変更。読み込み前に.mid / .midiの拡張子を確認し、それ以外を拒否します。
- ドレミを不透明な太い白文字＋暗い縁取りに変更。
- 鍵盤のファを一行の横書きに戻し、他の白鍵と同じ文字サイズ・高さ・ベースラインに統一。名前が長い場合は横幅だけを調整。
- ロールのノートも不透明にし、名前を下端側へ配置。velocityは音声に維持します。
- SUS表示・サステイン音声・FLAC / MP3 / M4A書き出しは維持。

Androidビルド・42 JVM tests・Lint errors0・署名・16KiB整列を確認。旧版と同じ署名で更新できます。新しい文字・ファイルピッカーは実機未検証です。SAFはproviderのMIMEで絞るため、汎用binary MIMEで登録されたMIDIは表示されない可能性があります。M4Aの端末試験も未実行です。エンコーダーの対応ソースをソースZIPに含めています。

[操作と実機確認](https://github.com/tstyr/PianoPocket/blob/main/docs/PERFORMANCE_UI.md)

## ダウンロード

Assetsアップロード先のHTTP 401制限は未解消のため、配布用release-artifactsブランチから取得できます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.2-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.2-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.2-alpha-validation.zip)
- [自作テストMIDI](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketStudy.mid)
- [自作テスト音源](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketFixture.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.4.2.txt)
