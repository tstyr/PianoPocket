# SoundFont

音源ライブラリからSF2ファイルを選ぶと、含まれる音色をBank・Program番号付きで追加します。音色を選んで画面鍵盤やMIDIから演奏できます。元のファイルと展開済みWAVを端末内に保存するため、再起動後も使えます。

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
