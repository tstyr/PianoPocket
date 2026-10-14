# SoundFont

音源ライブラリからSF2・SF3ファイルを選ぶと、含まれる音色をBank・Program番号付きで追加します。音色を選んで画面鍵盤やMIDIから演奏できます。元のファイルと展開済みWAVを端末内に保存するため、再起動後も使えます。

## 対応範囲

SF2 2.xのRIFF/hydraテーブル、プリセット・楽器のグローバル／ローカルゾーン、キー・ベロシティ範囲、16bit PCM、左右サンプル、オフセット、ループ、ルート音程・補正、パン、音量、基本ADSRとローパスを読み込みます。ステレオの左右は元のゾーンのパンで再生します。右チャンネルから左への共有ピッチ変調は未対応です。

エンベロープ、ベロシティ応答、フィルターはPianoPocketの近似DSPです。カスタムモジュレーター・既定のSoundFont変調・LFO・Delay/Hold・内蔵コーラス／リバーブは未対応です。24bit拡張は上位16bitで読みます。未対応のgeneratorは音色の警告として表示・ログ記録します。Bank Select／Program Changeによる自動音色変更は行わず、ライブラリで指定した音色を使います。

最大512MiBの入力、2048プリセット、4096の展開サンプル、2GiBの追加PCMに制限します。RIFF範囲・テーブルサイズ・索引・ステレオリンク・サンプル／ループ境界を検査し、ROMサンプルや不正ファイルは拒否します。大容量ファイルの展開には元ファイル以外の空き容量が必要です。

## 検証・出典

- [SoundFont 2仕様書](https://www.synthfont.com/sfspec24.pdf)のRIFF、hydra、generator、sample headerを参照。パーサーは独自のMITコードです。
- 自作の正弦波SF2で音色分離、ピッチ、ベロシティ範囲、ループ、ADSR、音量、WAV生成、破損ファイルの拒否をJVMテストします。
- TimGM6mbの実ファイルを開発環境で展開し、136音色・2063リージョン・520サンプルの参照と本番SFZパーサーの読み込みを確認しました。TimGM6mbはGPL-2のためAPK・リポジトリには含めません。
- 展開したTimGM6mbのピアノをネイティブエンジンで10秒書き出し、非ゼロの音声、cache miss 0、読み込みエラー0を確認しました。
- Android実機でのSF2読み込みと演奏は未検証です。

```sh
./gradlew inspectSoundFont -PsoundFontFile=/path/to/owned.sf2 -PsoundFontOutput=/path/to/private/output
```

展開したプリセット・サンプルの再配布には元の音源の利用条件が適用されます。

## SF3

SF3のVorbis圧縮サンプルに対応します。SoundFontの音色・マッピングはSF2と同じ扱いです。インポート時に圧縮データのバイト範囲を取り出し、既存のstb_vorbisでFloat32 WAVへ展開します。再生中はVorbisを復号しません。ZIP内のSF3も読み込めます。

圧縮された原ファイルは小さくなりますが、展開後のWAVと元ファイルの両方を保存します。必要な空き容量は圧縮サイズより大きくなり、SF2よりアプリ内の保存容量が小さくなるとは限りません。1サンプルの展開は512MiB、SoundFontの展開PCM合計は2GiBまでです。再生範囲・ループは復号後のフレームで検査します。ROM・Vorbis以外の圧縮は未対応です。

[MuseScore sf3convert](https://github.com/musescore/sftools)のbyte offset・loop frame・sample typeの形式と、[FluidSynth](https://github.com/FluidSynth/fluidsynth)のSF3形式の扱いを参照しています。これらの実装コードは取り込んでいません。新しい商用ライセンスやアプリ依存はありません。

構造テストでは独自のデコーダー注入を使い、サンプル範囲、1回だけの復号、共有WAV、不正データ・展開上限を検査します。実際のVorbis復号は別途、本番ネイティブデコーダーを使って検証します。

```sh
./gradlew inspectSoundFont -PsoundFontFile=/path/to/owned.sf3 \
  -PsoundFontOutput=/path/to/private/output \
  -Psf3Decoder=/path/to/build/native/sample_decode_probe
```

検証用にTimGM6mbからVorbis圧縮のSF3を生成し、本番デコーダーで136音色・2063リージョン・520サンプルを復号・解析しました。展開したピアノの10秒書き出しで非ゼロの音声、cache miss 0、読み込みエラー0を確認しています。この派生音源と書き出し音声は配布しません。SF3のAndroid実機試験は未実施です。

0.24.1では、`overridingRootKey=-1`を仕様どおり「サンプルのOriginal Keyを使う」として扱います。以前の版で該当するSoundFontを読み込んだ場合は、再インポートしてください。
