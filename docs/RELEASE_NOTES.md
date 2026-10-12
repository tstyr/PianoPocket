# PianoPocket 0.4.4-alpha — 落ちるドレミ・連動ピンチズーム

- ロールのドレミを画面下端へ貼り付けず、ノートと一緒に落ちて消えるように変更。鍵盤のドレミ表示は維持。
- ロール上のピンチアウト／インで横幅を1〜6.5倍に拡大・縮小。鍵盤も同じ倍率と音域へ連動します。
- 拡大後はロールを横ドラッグして音域を移動。拡大アイコンで88鍵へ復帰、矢印で倍率を維持して横移動。
- 鍵盤のマルチタッチ和音と競合しないよう、ピンチ操作はロール上で行います。
- 不透明な白文字＋縁取り、SUS、音声サステイン、MIDI選択制限と各エクスポート形式を維持。

Androidビルド・45 JVM tests・Lint errors0・署名・16KiB整列を確認。拡大後のキー座標・タッチ判定・範囲制限をテストしました。新しいピンチ・文字の動きは実機未検証です。M4Aの端末試験も未実行です。旧版と同じ署名で更新できます。

[操作と実機確認](https://github.com/tstyr/PianoPocket/blob/main/docs/PERFORMANCE_UI.md)

## ダウンロード

Assets添付先のHTTP 401制限が継続しているため、配布用release-artifactsブランチから取得できます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.4-alpha-arm64-debug.apk)
- [対応ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.4-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.4-alpha-validation.zip)
- [自作テストMIDI](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketStudy.mid)
- [自作テスト音源](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketFixture.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.4.4.txt)
