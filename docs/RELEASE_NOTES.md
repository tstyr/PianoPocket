# PianoPocket 0.1.0-alpha — native sampler preview

ユーザー所有のDecent SamplerプリセットとWAVを、Androidのネイティブ音声エンジンで演奏する初期alphaです。

- Kotlin / Compose、ARM64、Android 16 target、Oboe低遅延経路。
- ステレオWAV、velocity mapping、ADSR、sustain、release、マルチタッチ鍵盤、USB / BLE MIDI処理。
- SAF / bounded ZIP import、永続ライブラリ、サンプル先頭preload＋非同期LRU streaming。
- instrument effect chain: 近似Reverb / Delay / Chorus、Filter / EQ / Gain、分割FFT Convolution。
- 独立したWindows VST調査・probe。Windows VST実行は未検証。

**Helsinki v1.6の実配布ファイルとGalaxy S26実機は未検証。XF、純正IR設定、純正エフェクト音の完全再現を主張しません。** 未対応XMLは警告またはロードエラーとして表示します。

添付予定: デバッグ署名ARM64 APK、ソースアーカイブ、自作検証fixture、SHA-256一覧。Pianobook音源を含みません。テストの具体的な結果は [VALIDATION.md](VALIDATION.md) を参照。
