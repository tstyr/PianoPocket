# PianoPocket 0.3.0-alpha — MIDI library, falling notes, recording and export

MIDIを選んで、読み込んだピアノ音源で演奏・録音・書き出しできる機能を追加しました。

- MIDI Type 0 / 1インポート、BPM・日付付きカードライブラリ、再起動後の再利用。
- 上から降るロール、トラック色・velocity・ペダル残音、88鍵／2オクターブ、設定からカタカナのドレミをオン／オフ。
- USB / BLEの入力ポート選択、リアルタイムの鍵盤／履歴表示、MIDI録音と保存。
- C++のサンプル時計によるMIDI再生、一時停止・シーク。ファイルとライブ入力のチャンネルを分離。
- MIDI Type 0 / 1、WAV PCM16 / PCM24 / Float32を選択して保存。音声は44.1 / 48 kHz stereo、現在の音源・マイク・IR・エフェクト・音量を反映。
- 書き出し専用サンプラーと同期LRUキャッシュ、追加残響時間、進捗・キャンセル。音源はMIDIファイル自体には含まれません。

Androidビルド・Lint・36 JVM tests・native / ASan / UBSanはPASS。Helsinki v1.6 XFの実ファイルで、ペダル・リリース・IRを含む10秒の音声生成を検証（試験時IR mix=0.35、cache miss / I/O error=0）。ユーザー指定のopenthesia-androidは操作の参考として確認し、GPLコードや素材は取り込んでいません。

**Galaxy S26 / Android 16のUI・USB / BLE録音・SAF保存・レイテンシは実機未検証です。** MIDI編集・Learning Mode・MP3/AAC/FLAC書き出し・MIDI 2.0・GM音色切替は未対応。Windows VSTは別の実験資料のままです。

Pianobookのサンプル・IR・画像・実XML、ユーザーのMIDIは配布しません。Helsinkiは正規に取得した音源をインポートしてください。自作PocketStudy.midとPocketFixture.zipをテスト用に用意しています。

[使い方](https://github.com/tstyr/PianoPocket#readme) / [検証と実機手順](https://github.com/tstyr/PianoPocket/blob/main/docs/SEQUENCER.md)。

## ダウンロード

Assetsへの直接添付は、この環境のアップロード認証エラーで完了していません。配布用release-artifactsブランチから、以下のファイルを取得できます。

- [Android ARM64 APK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.3.0-alpha-arm64-debug.apk)
- [ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.3.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.3.0-alpha-validation.zip)
- [自作テストMIDI](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketStudy.mid)
- [自作テスト音源](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketFixture.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.3.0.txt)
