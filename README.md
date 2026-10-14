# PianoPocket

Androidでピアノを弾く、MIDIを再生する、演奏を残す。
無料・広告なし・アカウント不要。演奏・録音・書き出しは端末内で動作します。音源の追加ダウンロード時だけ通信します。

[**APKをダウンロード**](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.15.0-arm64.apk) · [リリース](https://github.com/tstyr/PianoPocket/releases/tag/v0.15.0-alpha)

Android 10以降・ARM64対応。現在の配布版は **0.15.0 alpha**、デバッグ署名のAPKです。

## はじめる

1. APKをインストールして起動します。
2. 内蔵の **Pocket Upright** が読み込まれます。画面の鍵盤、またはUSB MIDIキーボードで演奏できます。
3. 自分の音源を使うときは、ピアノアイコン →「音源ライブラリ」→「音源をインポート」を選びます。同じアイコンからMIDIライブラリも開けます。

Pocket Uprightは、VSCO 2 Community Editionの実録音を使ったステレオ・アップライトピアノです。3段階のベロシティ、88鍵のマッピングに対応。音源は約33MiBで、別途ダウンロードする必要はありません。削除した場合も「内蔵ピアノを読み込む」で戻せます。

## 高品質ピアノを追加する

音源ライブラリの **Pocket Upright HQ** から直接ダウンロードできます。23収録キー、3段階のベロシティ、24bitステレオ、元の残響を全収録した69サンプルの音源です。約105MiBの通信と約600MiBの空き容量が必要です。初期同梱の軽量版は引き続き使えます。

GitHub上の公開ファイルから取得するため、専用サーバー・APIキー・アカウントは不要です。キャンセル・途中再開・SHA-256検証に対応し、取得後はオフラインで演奏できます。[配布方式・出典](docs/DOWNLOADS.md)。

## 音源を追加する

| 形式 | 読み込み方 |
| --- | --- |
| `.sfz` | SFZファイルを選び、続けてそのファイルが置かれているフォルダーを選択 |
| `.dspreset` | プリセットを選び、続けてそのファイルが置かれているフォルダーを選択 |
| `.zip` / `.dslibrary` | プリセットとサンプルを含むZIPを選択 |

サンプルはWAV・FLAC・OGG Vorbisに対応しています。フォルダーの構成を変えずに読み込んでください。親フォルダーを参照するSFZは、必要なファイルをまとめたZIPでインポートできます。読み込んだ音源は端末内に保存され、次回も利用できます。

Helsinki 1967 ProのDecent Sampler版は、利用者が正規に入手したファイルを読み込む方式です。通常版・XF版、マイクミキサー、リリース・ペダル音、付属IRに対応しています。[互換性の詳細](docs/HELSINKI.md)。Helsinkiの音源・IRは同梱していません。

Decent Samplerのローパスは旧名`lowpass_4pl`にも対応しています。純正との音響的な同一性は保証していません。[フィルターの対応範囲](docs/DECENT_FILTERS.md)。

SFZは基本的なWAVマッピング、ベロシティ、ADSR、チューニング、パン、明示されたループ、ラウンドロビン、ベロシティ曲線、基本フィルター、`#include` / `#define`に対応します。SFZ・Decent Samplerの全機能には対応しておらず、未対応の指定は音源パネルに警告を表示します。SF2、OGG Opusは未対応です。[SFZの対応範囲](docs/SFZ.md)。

## 演奏・MIDI

- 横画面の鍵盤と、上からノートが降るロールビュー。ピンチ操作で鍵盤の幅を変更できます。
- カタカナのドレミ、UIの表示・非表示、FPS表示を切り替えできます。画面更新は最大120fps。
- USB / Bluetooth MIDIの入力選択、サステイン、演奏の録音。
- `.mid` / `.midi` のライブラリ、速度変更、A–Bリピート、トラックごとの表示・ミュート。正しい鍵盤入力を待つ練習、左右別の伴奏、メトロノームも利用できます。
- 「その他 → 転調」でMIDIを半音ずつ±2オクターブまで移動できます。ロール・再生・書き出しに反映し、元のファイルは保持します。ドラムチャンネル、画面鍵盤、リアルタイム入力の音程は変えません。
- 音源ごとの音量・エフェクト設定を保存し、名前を付けてお気に入りに登録できます。

有線・USBオーディオ出力を推奨します。Bluetoothイヤホンでは遅延が大きくなることがあります。バッファや発音数は設定から調整できます。

設定は「表示」「音の設定」「MIDIキーボード」に分かれています。ドレミや横画面の切り替えは「表示」、バッファ・発音数・メモリの調整や診断情報は「詳細設定」から開けます。

## 書き出し

選択した音源でMIDIを音声・動画に書き出せます。

| 種類 | 形式 |
| --- | --- |
| MIDI | MIDI |
| 音声 | WAV 16/24bit、FLAC 24bit、MP3、M4A |
| 動画 | MP4、MOV |

動画はロールビュー付き。短いプレビューで見た目を確認できます。書き出し中は進行状況と残り時間の目安を表示し、背景でも処理を継続します。M4A・動画には端末のAAC / H.264エンコーダーが必要です。

## 開発

Kotlin / Jetpack Compose、C++17、Oboeを使用しています。ビルドにはJDK 17、Android SDK 36、NDK 28.2.13676358、CMake 3.22.1が必要です。

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
cmake -S native -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native -j2
ctest --test-dir build/native --output-on-failure
```

APKは `app/build/outputs/apk/debug/app-debug.apk` に生成されます。同梱音源もリポジトリに含まれています。

各公開版でビルド・自動テストを実行しています。開発環境でのAndroid実機試験は未実施です。端末ごとのレイテンシやBluetooth MIDIの動作は実機で確認してください。[テスト結果](docs/SFZ.md#検証) · [アーキテクチャ](docs/ARCHITECTURE.md)。Windows VSTは独立した[実験資料](experiments/windows-vst/README.md)のみで、アプリの機能には含まれません。

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
