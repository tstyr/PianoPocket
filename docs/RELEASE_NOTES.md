# PianoPocket 0.4.1-alpha — SUS / ドレミ / FLAC・MP3・M4A

- ロールは鍵盤を押している区間だけ表示し、サステインの尾を描きません。音声とMIDIのペダルは維持します。
- サステイン中はSUSが黄色。画面ペダル・USB/BLE MIDI・MIDIファイルのCC64に連動。
- 88鍵で極小になっていたドレミを拡大し、ファ／♯を縦配置。設定でオン／オフ可能です。
- FLAC24、MP3、M4A（AAC-LC）を追加。MP3 / M4Aは128〜320 kbps、44.1 / 48 kHzを選択できます。M4Aは端末のAAC encoderがない場合は無効になります。
- LAMEは共有ライブラリとしてリンクし、LGPL本文・対応ソース・改変／差し替え手順を提供。libFLACはBSD-3-Clause。

Androidビルド・Lint errors 0・41 JVM tests・native / ASan / UBSan PASS。独立したFFmpegで20 codec round tripsを通過。実Helsinki XFのFLACは元のPCMに完全一致、MP3は4bitratesで再生時間を確認しました。音源・IR・原XMLや実音源から書き出した音声は同梱していません。

M4AのMediaCodec端末テストは作成・ビルド済みですが、端末未接続で未実行です。新しいドレミ・SUS表示とAndroid保存も実機未検証。旧版と同じ署名で上書き更新できます。

[形式・ライセンス・検証手順](https://github.com/tstyr/PianoPocket/blob/main/docs/EXPORT_FORMATS.md)

## ダウンロード

配布用release-artifactsブランチから取得できます。Assetsアップロード先のHTTP 401制限は未解消のため、直接添付は未完了です。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.1-alpha-arm64-debug.apk)
- [対応ソースZIP（エンコーダー含む）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.1-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.4.1-alpha-validation.zip)
- [自作テストMIDI](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketStudy.mid)
- [自作テスト音源](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketFixture.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.4.1.txt)
