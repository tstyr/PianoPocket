# PianoPocket

Android向けのオフライン・ネイティブサンプラー、**0.1.0 alpha**。Kotlin / Composeで操作画面を、C++17 / Oboeで音声処理を実装しています。無料・広告なし・課金なし・アカウントなし。INTERNET権限もありません。ユーザー所有のサンプルをアプリの専用領域にインポートします。

**Helsinki 1967 Pro v1.6の配布ファイルは未取得で、互換性は未検証です。Galaxy S26 / Android 16の実機も未接続です。** 本リリースはHelsinki対応完成版ではありません。Pianobookの音源、画像、ロゴやDecent Samplerの実装・プラグインは同梱していません。

## 使い方

1. ARM64デバッグAPKをAndroidにインストールします。初回起動時にアプリ自身が生成する「Pocket Tone — 自作デモ」で発音を確認します。これは合成したテスト音で、Helsinkiのサンプルではありません。
2. ライブラリ → 音源をインポート。ZIP / ZIP形式の `.dslibrary` を選ぶか、`.dspreset` と**そのプリセットが置かれているフォルダー**を順に選びます。SAFのアクセス許可を使用し、広範囲のストレージ権限は要求しません。
3. アーカイブに複数のプリセットがある場合、ライブラリから使うプリセットを選びます。相対パスのWAVとIRが必要です。元ファイルを移動しても、取り込み済みのコピーを再利用できます。
4. 鍵盤は2オクターブ、マルチタッチ。下側を押すほど高いベロシティになります。SustainボタンかMIDI CC64でペダルを操作できます。
5. USB MIDI機器は自動検出。Bluetooth MIDIは設定で権限を許可し、検索した機器を選びます。Bluetooth**オーディオ**とBluetooth **MIDI**は異なります。
6. XMLの未対応機能は演奏画面の互換性レポートと `PianoPocket.XML` ログに表示します。未対応のエフェクト種類、テンポ同期delayなど、適切に再現できないエフェクトは読み込みを拒否します。

有線またはUSBオーディオ出力を推奨します。Bluetoothイヤホンでは遅延が増える場合があります。10ms以下は保証しません。設定の遅延はOboe timestampによる**出力経路の推定値**で、鍵盤から音までの実測値ではありません。

## 実装範囲

| 機能 | 現在の状態 |
| --- | --- |
| WAV mono / stereo | PCM 16 / 24 / 32 bit、Float32。対応サブタイプのWAVE_FORMAT_EXTENSIBLEも可。AIFF / FLAC / RF64未対応 |
| 音声エンジン | Oboe AAudio優先、低遅延・Exclusiveを要求、失敗時Shared。OSが選ぶサンプルレート、Float stereo |
| サンプラー | キー・velocity範囲、rootNote、チューニング、線形補間、pitchKeyTrack、音量・パン、近似の線形ADSR、クリック抑制 |
| 演奏 | Note On / Off、CC64、CC7、CC11、CC120 / 123 / 121、All Notes Off、±2半音Pitch Bend |
| サステイン / リリース | チャンネル別、同音の重複押下、キー状態を音声ボイスと独立管理。`trigger="release"` 対応。releaseTriggerDecay未対応 |
| ペダルノイズ | `onLoCC64 / onHiCC64` の0..63側・64..127側を状態の変化時に発音。ハーフペダルや任意CCトリガーは未対応 |
| ループ | 明示的loopStart / loopEnd、loopEnabled。リリース時にループを終了。WAV内蔵マーカー、ループクロスフェード未対応 |
| 発音数 | 32〜128 sample voices、既定80。最小エンベロープ値／古いボイスからスティーリング。マイク・velocityレイヤーも1 voiceずつ消費 |
| 大容量サンプル | WAVごとの先頭8192 frames事前読込＋4096 frameページの256ページLRU、専用ワーカーストリーミング。ミス時はフェード付き無音で継続し、ミス数とI/Oエラーを表示 |
| メモリ | WAV先頭＋ページキャッシュの予算32〜512 MiB、既定256。超過時ロードを中断して旧音源を保持。DSP・UI・一時ロード領域は別途メモリを消費 |
| マイク / グループ | XMLにある実グループを表示し、個別音量を調整。Helsinkiの5系統という名称や構造は固定していない |
| Reverb / Delay / Chorus | 公式XMLパラメータを解析し、独自の近似DSP。純正と同じ音やアルゴリズムは保証しない |
| Convolution | WAV IR、stereoまたはmonoから展開、256 frame均等分割FFT。最大262144出力frames。超過は拒否、切り詰めない。追加遅延256 frames |
| Filter / EQ / Gain | lowpass / highpass / bandpass / peak / notch / low_shelf / high_shelf、2-pole biquad。GainのdB / linear単位に対応 |
| エフェクトチェーン | instrument階層のXML順、最大8段、バイパスと15msパラメータスムージング、ノート後の残響を保持 |
| UIバインディング | instrumentエフェクトおよびグループAMP_VOLUMEへの数値index・linearバインディングの部分対応 |
| ライブラリ | ローカルコピー、原子的index更新、容量・最終使用時刻・選択・削除・再スキャン。再起動時に前回音源をロード |
| ZIP保護 | 絶対パス・親移動・重複パス拒否、20000 entries / 512 MiB per file / 8 GiB総展開サイズ、圧縮比制限、空き容量確認、失敗時ロールバック |
| メトリクス | sample resident bytes、DSPコールバックの時間予算率、XRun、キャッシュミス、イベントキュー欠落、出力遅延推定 |

## 制限と未対応

- Helsinki v1.6 XFのvelocityクロスフェード、マイク構成、ペダル処理、純正IRの選択と初期値は**実XMLの解析待ち**です。一般的なvelocity switchingの実装を、Helsinki XF検証済みとは扱いません。
- グループ・サンプル階層のeffects、modulators、velocity_binding、任意のMIDIバインディング、tags、table / fixed_value翻訳、menu / button / UI画像などは警告付きで未対応。未対応のグループDSPがあるプリセットは純正の音にはなりません。
- ADSRの曲線形状、再打鍵・サステイン共鳴、リリース速度／経過時間による減衰、ハーフペダルは純正と一致しません。
- 一部の新しいエフェクト属性（例：delay feedbackCutoff）は警告付きで未対応です。公式に存在する全属性の互換実装ではありません。
- `.dslibrary` はZIPベースの平文アーカイブだけに対応。暗号化ライブラリは非対応。`.sfz` / `.sf2` はまだ非対応です。
- WAV参照はプリセット配下の相対パスのみ。`../Samples` のような親方向の参照は拒否します。必要なら利用者のコピー上でプリセットとSamplesを同一のルートに配置してください。
- キャッシュ不足やストレージ速度不足で欠落が発生する可能性があります。5マイク・長いIR・80 voicesで安定動作するかは実機で確認が必要です。
- MIDIの入力timestampに合わせたサンプル単位スケジュールは未実装。入力を次の音声ブロックで処理します。
- アプリはバックグラウンドで停止します。音声経路が切断された場合は設定の「オーディオを再開」を使用します。自動再接続・バックグラウンド演奏サービスは未実装。
- UIテーマはダークのみ。設定の永続化は前回音源、発音数、バッファ、メモリ予算。エフェクト／マイクのユーザープリセット保存は未実装。
- Android実機での起動・タッチ・USB / BLE MIDI・Helsinki演奏は未検証です。APK生成やJVMテストはこれらの成功を意味しません。

## ビルド

JDK 17、Android SDK 36、Build Tools 36.0.0、NDK 28.2.13676358、CMake 3.22.1を用意します。Google Oboe 1.9.3のビルドに必要なソースは `third_party/oboe` に含めてあります。ライセンスも保持しています。

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1'
# ANDROID_HOMEを設定するか、local.propertiesにsdk.dirを記載
./gradlew assembleDebug testDebugUnitTest lintDebug
# app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

デバッグAPKはARM64のみ。Android 10以上をminSdkとし、Android 16をtargetSdkにしています。デバッグ署名で、ストア配布向けの署名・パッケージではありません。NDK r28の16KB対応C++ runtimeを使用し、ネイティブライブラリも16KBページに合わせます。

## テスト

```sh
cmake -S native -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native -j 4
ctest --test-dir build/native --output-on-failure
./gradlew testDebugUnitTest lintDebug
python3 tools/generate_fixture.py artifacts/fixture
python3 tools/inspect_instrument.py artifacts/fixture --output artifacts/fixture-audit.json
```

生成した `artifacts/PocketFixture.zip` は自作音のみで、4段階のvelocity、release、pedalを実機で確認するための素材です。[検証結果](docs/VALIDATION.md)、[Helsinki検証手順](docs/HELSINKI.md)、[アーキテクチャ](docs/ARCHITECTURE.md)を参照してください。

## Windows VST実験

[experiments/windows-vst](experiments/windows-vst/README.md) にWine / Box64 / Windows VST3ホストの調査と実測手順、独立したメトリクス収集スクリプトがあります。Wine、Box64、Winlator、Windowsホスト、Decent Sampler VSTのコードやバイナリはAPKに組み込んでいません。この環境ではWine / Box64は未導入で、VSTの起動・MIDI・音声出力は未検証です。実験が失敗しても本体とは独立しています。

## ライセンス

PianoPocketの自作コード・合成fixtureはMIT。[LICENSE](LICENSE)を参照。サンプルライブラリの利用条件はそれぞれの配布元に従ってください。Decent Samplerという名称は対応ファイル形式の説明で使用しており、公式製品や関連アプリを意味しません。

| 依存 | バージョン | ライセンス |
| --- | --- | --- |
| Google Oboe | 1.9.3 | Apache-2.0、ライセンスをvendorディレクトリに保存 |
| AndroidX / Jetpack Compose / Material3 | build.gradle.kts参照 | Apache-2.0 |
| Kotlin / kotlinx.coroutines | 2.1.21 / 1.10.2 | Apache-2.0 |
| Gradle / Android Gradle Plugin | 8.13 / 8.10.1 | Apache-2.0、ビルドツール |
| Android NDK libc++ | r28c | Apache-2.0 with LLVM exception、その他NDK同梱NOTICEに従う |
| JUnit 4 | 4.13.2 | EPL-1.0、テストのみ |
| kXML2 | 2.3.0 | BSD-3-Clause、JVMテストのみ |
| Hamcrest | JUnitの推移依存 | BSD-3-Clause、テストのみ |

商用専用ライセンスが必要な依存は導入していません。Wine / WinlatorはLGPL-2.1系、Box64はMITですが、これらは実験対象であり本体の依存ではありません。[公式資料と確認日](docs/REFERENCES.md)。

## リリースとGitHub

GitHub Actionsはネイティブテスト、JVMテスト、Lint、デバッグAPK生成を実行します。`docs/RELEASE_NOTES.md` にalphaリリース文面を準備しました。本作業時点では `tstyr/PianoPocket` の新規作成をGitHub連携権限が拒否したため、プッシュ・GitHub Releaseは未実施です。リポジトリ作成または既存のプッシュ先指定が必要です。
