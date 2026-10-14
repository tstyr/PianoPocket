# PianoPocket

Androidでピアノを弾く、MIDIを聴く、演奏を録る。
無料・広告なし・アカウント不要。音源の追加ダウンロードを除き、オフラインで使えます。

[**APKをダウンロード**](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.26.0-arm64.apk) · [リリース](https://github.com/tstyr/PianoPocket/releases/tag/v0.26.0-alpha)

Android 10以降・ARM64対応。現在は **0.26.0 alpha**、デバッグ署名の試用版です。

## はじめる

1. APKをインストールして起動します。内蔵ピアノ **Pocket Upright** が読み込まれます。
2. 画面の鍵盤をタッチするか、USB MIDIキーボードを接続して演奏します。
3. ピアノアイコンから「MIDIライブラリ」または「音源ライブラリ」を開きます。

MIDIライブラリに `.mid` / `.midi` を追加すると、降ってくるノートを見ながら再生・練習できます。ドレミの表示は設定で切り替えできます。画面は横向きで使うと鍵盤が広くなり、ピンチ操作で音域を拡大できます。

曲ごとに音源、転調、再生速度、リピート区間、鍵盤の表示範囲、トラック設定、再開位置を自動保存します。次に開いたときは停止状態で復元します。

## 演奏と練習

- USB / Bluetooth MIDIの入力と出力、サステイン、MIDI録音。
- スマホ向けのMIDI編集。音符の移動・長さ・強さ・削除、ピンチ拡大と取り消しに対応し、編集したコピーを保存できます。[使い方](docs/MIDI_EDITOR.md)。
- 再生速度の変更、±2オクターブの転調、A–Bリピート、トラックのミュート。
- トラックごとの音源選択。ピアノ・弦・ドラムを別々の音色で鳴らせます。
- 正しい鍵盤を押すまで待つ練習、左右別の伴奏、メトロノーム。
- マイクミキサー、エフェクト、音源設定のお気に入り。SFZの奏法は音源パネルやキースイッチで選べます。
- ロールビューとドレミの切り替え、操作ボタンの非表示、最大120fpsの画面更新。

転調はMIDI再生と書き出しに反映します。ドラムチャンネル、画面鍵盤、入力キーボードの音程は変わりません。[MIDI出力の使い方](docs/MIDI_OUTPUT.md)。

「その他 → 練習・メトロノーム」のトラック欄で、各トラックの音源を選べます。指定しなければ主音源を使います。[使い方と上限](docs/TRACK_INSTRUMENTS.md)。

「その他 → 鳴らす音」で内蔵音源・外部MIDI・両方を切り替えできます。外部MIDIの送信先は「設定 → MIDI入出力」で選びます。接続が切れたときは内蔵音源に戻ります。書き出しは選択した音源と保存済みの音量を使います。

設定 →「音の設定」で **アプリを閉じても演奏** をオンにすると、画面オフや他のアプリを開いている間もMIDI再生・入力キーボードの演奏・録音を続けられます。通知から再生・一時停止・終了できます。通話などの割り込みやイヤホンの取り外しで演奏を停止します。

有線・USBイヤホンをおすすめします。Bluetoothイヤホンは音が遅れることがあります。

## 音源を追加する

音源ライブラリから **SFZ、SF2 / SF3、Decent Samplerプリセット、ZIP** を読み込めます。

| 形式 | 選ぶもの |
| --- | --- |
| `.sfz` / `.dspreset` | プリセットを選び、続けて同じフォルダーを選択 |
| `.sf2` / `.sf3` | SoundFontファイルを選択。収録された音色をライブラリに追加 |
| `.zip` / `.dslibrary` | プリセットとサンプルをまとめたZIP |

フォルダー構成は変えずに読み込んでください。インポートした音源は端末内に保存されます。サンプルはWAV・FLAC・OGG Vorbisに対応。未対応の設定は警告を表示します。SF2・SF3は音色ごとに選択できます。一部の変調・エフェクトは未対応です。[SoundFontの対応範囲](docs/SOUNDFONT.md)。

**Helsinki 1967 Pro** は、正規に入手したDecent Sampler版を読み込んで利用できます。通常版・XF版、マイク、リリース・ペダル音、付属IRに対応しています。Helsinkiの音源・IRは同梱していません。[互換性](docs/HELSINKI.md) · [SFZの対応範囲](docs/SFZ.md)。

内蔵ピアノは約33MiBの軽量版です。音源ライブラリの **Pocket Upright HQ** から、残響を長く収録した約105MiBの版もダウンロードできます。約600MiBの空き容量が必要です。取得後はオフラインで使えます。[音源の出典](docs/BUNDLED_PIANO.md)。

## 書き出し

録音した演奏や読み込んだMIDIを、選択した音源で書き出せます。

| 種類 | 形式 |
| --- | --- |
| MIDI | MIDI Type 0 / 1 |
| 音声 | WAV、FLAC、MP3、M4A |
| 動画 | MP4、MOV |

書き出し画面で再生速度・ミュートの反映と、曲全体／A–B区間を選べます。MIDI・音声・動画に同じ設定を反映します。

音声・動画の「音量を自動調整」で、曲の強弱を保ちながら書き出し音量を整えられます。[音量調整の仕様](docs/EXPORT_VOLUME.md)。

動画にはロールビューと鍵盤を表示します。短いプレビューで見た目を確認できます。書き出しはアプリを閉じても継続し、通知から進行確認・キャンセルできます。M4A・動画には端末の対応エンコーダーが必要です。[形式と設定](docs/EXPORT_FORMATS.md)。

## 開発・動作確認

Kotlin / Jetpack ComposeとC++ / Oboeで実装しています。[ビルド手順](docs/DEVELOPMENT.md) · [設計](docs/ARCHITECTURE.md)。

ビルドと自動テストを実施しています。利用者によるGalaxy S26での動作確認報告があります。開発環境に実機は接続されていないため、新機能や端末ごとの音声遅延、USB / Bluetooth MIDIは別途確認が必要です。

## ライセンス

PianoPocketのコードは[MIT](LICENSE)。内蔵ピアノのサンプルは[CC0 1.0](app/src/main/assets/instruments/PocketUpright/LICENSE.txt)、録音はSimon Dalzell / Ivy Audio、配布はVersilian Studios / Sam Gossnerです。[音源の出典・変更内容](docs/BUNDLED_PIANO.md)。

| ライブラリ | ライセンス |
| --- | --- |
| Oboe、AndroidX / Compose、Kotlin / Coroutines | Apache-2.0 |
| libFLAC | BSD-3-Clause |
| stb_vorbis | MIT（選択） |
| LAME（共有ライブラリ） | LGPL-2.0-or-later |
| NDK libc++ | Apache-2.0 with LLVM exception |

各ライセンス・対応するソースは[third_party](third_party/README.md)に、エンコーダーの差し替え方法は[書き出し形式の資料](docs/EXPORT_FORMATS.md)に記載しています。音源を追加したり、演奏を公開したりする際は、その音源の利用条件も確認してください。
