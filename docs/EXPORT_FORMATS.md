# 音声書き出し — 0.4.1

︙ → **MIDI / 音声 / 動画を書き出し**で選択します。現在の音源・マイク・IR・エフェクト・Output・発音数を反映したオフライン演奏を保存します。44.1 / 48 kHz stereo、残響0〜30秒に対応。サステインは音声とMIDIに維持し、ロールの表示長とは独立しています。

| 形式 | 実装・設定 |
| --- | --- |
| WAV | PCM16 / PCM24 / Float32、既存のC++ renderer |
| FLAC | libFLAC 1.4.3、24-bit lossless、圧縮レベル5。自動検証付き |
| MP3 | LAME 3.100、CBR 128 / 192 / 256 / 320 kbps、既定256。Xing/LAMEヘッダーにdelay/paddingを記録 |
| M4A | Android MediaCodec AAC-LC + MediaMuxer MP4。128 / 192 / 256 / 320 kbps、既定256。AAC encoderがない端末では無効表示 |
| MIDI | Type 0 / 1。サンプル・エフェクトの音は含まれません |
| MP4 / MOV | 0.6.0で動画追加。Android H.264 + AAC、720p / 1080p・30 / 60fps、正しいisom / qtコンテナ。[詳細](VIDEO_EXPORT.md) |

FLAC / WAVは可逆、MP3 / AACは非可逆です。圧縮音声もインターネット・API・アカウントを使いません。ファイルの拡張子だけを変える処理ではなく、実際に各コーデックでエンコードします。読み込み用サンプル形式は従来どおりWAVで、FLAC / MP3 / M4Aのサンプルインポートは未対応です。

C++でPCM WAVを一時生成後、専用ワーカーで圧縮し、SAFの保存先へコピー。M4Aの中間PCMは16-bit、MP3 / FLACは24-bit。音声コールバック内ではエンコード・ファイルI/O・Codec APIを呼びません。処理段階ごとの進行状況とキャンセルに対応。途中の一時ファイルを削除し、保存途中のSAFファイルも削除を試みます。保存先providerが削除を拒否した場合は空／途中のファイルが残る可能性があります。

空き容量は中間PCMと圧縮ファイル両方の保守的な見積もりに128 MiBを追加して確認。長時間の書き出しは十分な端末ストレージが必要です。OS/vendorのAAC encoderが特定のレート・bitrateを拒否する場合はエラーを表示し、WAV / FLAC / MP3へ切り替えられます。AACのdelay/paddingの再生処理は端末・プレイヤーに依存します。

## 検証と残る確認

- 自作ステレオ440/660 Hzの3秒音声、44.1 / 48 kHz、PCM16 / PCM24を入力し、独立したFFmpegで復号。FLAC4ケースはPCM全バイト一致、MP3 16ケースは全フレーム数一致、チャンネル別波形相関0.99超。FLAC / MP3キャンセル時の途中ファイル削除も確認。
- Helsinki v1.6 XFの実音源・IRによる10秒／48000 Hz／PCM24のホスト演奏を同じエンコーダーで保存。FLACは全PCM一致、MP3 4bitratesは480000 framesを復号。原音・書き出し音声は公開しません。
- M4Aは `AacExporterDeviceTest` で44.1 / 48 kHzの実MediaCodecエンコード → MP4 track検査 → AACデコードと音声RMS検査、およびキャンセル削除を用意。Android test APKをビルドしますが、開発環境には実機／エミュレーターがないため**実行は未実施**です。M4A出力成功を確認済みとは報告しません。
- 端末を接続した環境で `./gradlew connectedDebugAndroidTest` を実行。Galaxy S26上でSAF保存・再生、FLACの24-bit／ステレオ、MP3 / M4Aの再生・残響・時間、キャンセル・空き容量エラーを確認してください。
- ホストでは `cmake -S native -B build/native -DCMAKE_BUILD_TYPE=Release && cmake --build build/native -j2` の後、`python tests/performance/verify_exports.py --probe build/native/codec_probe` を実行できます。FFmpeg / ffprobeは検証環境のツールで、APKには含みません。

## ライセンスと対応ソース

**LAME 3.100** ([公式サイト](https://lame.sourceforge.io/)) はLGPL-2.0-or-later。`libmp3lame.so`として動的リンクし、エンコーダーの全コンパイル対象ソース・ヘッダー、移植用CMake/config、[COPYING](../third_party/lame/COPYING)を同じリポジトリ・各リリースのソースZIPで提供します。LGPLに基づく改変・ライブラリ差し替え・再リンクのためのデバッグ／リバースエンジニアリングを禁止する追加制限は付けません。PianoPocketのMITライセンスはLAMEのライセンスを置き換えません。

LAME upstreamの負数の左シフト（VbrTag.cのbit mask、quantize.cのscale計算）と、bitstream.cのbit packingの符号付き左シフトを、同じbit mask／整数乗算／unsigned packingへ修正。変更はソースと[vendor記録](../third_party/README.md)に含めます。元ファイルとの差分・更新にはこの変更を保持してください。

**libFLAC 1.4.3** ([公式サイト](https://xiph.org/flac/)) のlibFLACはBSD-3-Clause。[COPYING.Xiph](../third_party/flac/COPYING.Xiph)を保持し、元のソースとCMakeを収録。FLAC++やGPLのコマンドラインプログラムはAPKにリンクしません。**AAC / MP4**はAndroid OSのAPIで、外部コーデック実装を同梱しません。

実際のライセンス本文はAPK内 `assets/encoder-licenses.txt` に含み、**設定 → エンコーダーのライセンス・ソース**からオフラインで確認できます。

### LAMEを改変・差し替える手順

ソースZIPを展開し、`third_party/lame/libmp3lame`を編集して通常の`./gradlew assembleDebug`を実行すると、改変した共有ライブラリをリンクしたアプリをビルドできます。独自署名でのインストールでは旧署名のアプリへ上書きできません。データを保持した並行試験は `app/build.gradle.kts` のapplicationIdを別名へ変更して行えます。

共有ライブラリ単体の再ビルドも可能です（SDK / NDKの場所は利用者環境に設定）：

```sh
cmake -S third_party/lame -B build/lame-replacement \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_SDK_ROOT/ndk/28.2.13676358/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29
cmake --build build/lame-replacement --target mp3lame -j2
```

生成した `libmp3lame.so` はAPK内 `lib/arm64-v8a/libmp3lame.so` の互換ABI共有ライブラリと差し替えられます。APKを再構成した後はAndroid build-toolsの`zipalign -P 16`と`apksigner`で自身のキーを使って署名してください。16KiB ELF整列を維持します。アプリ・ライブラリ全体をソースから再ビルドする方法を推奨します。
