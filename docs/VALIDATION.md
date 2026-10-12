# Validation — 2026-10-12

## 実行環境

Linux x86-64クラウド環境。Android SDK 36 / Build Tools 36.0.0、NDK 28.2.13676358、CMake 3.22.1、Gradle 8.13、AGP 8.10.1、Temurin JDK 17.0.20.1、Oboe 1.9.3。ホストC++コンパイラはGCC 14.2。Android端末は `adb devices` で0台。エミュレーターも未実行。

## 結果

| チェック | 結果 |
| --- | --- |
| `./gradlew assembleDebug testDebugUnitTest lintDebug` | PASS。ARM64デバッグAPK生成。Lint errors 0 |
| JVM parser / MIDI tests | 10件PASS。XML継承・volume乗算・effect順・binding・release / pedal・未知XML警告・危険パス・DTD拒否・不正range・fragmentation / running status / SysEx |
| JVM ZIP tests | 9件PASS（macOS sidecar除外を追加）。有効ZIP、traversal、absolute path、canonical duplicate、per-file size、total size、file count、compression bomb |
| JVM velocity / tags / menu tests | 3件PASS。合計JVM 22件PASS |
| Release native test suite | PASS。decoder、非同期cache、発音、velocity、sustain、release、自然終了後のrelease、同音FIFO、channel分離、voice stealing、limiter、event overflow、FFT convolution、reverb tail |
| Audio-thread allocation instrumentation | テストしたrender / effect経路の `operator new/new[]` 呼び出し0件。OS内部の挙動や全入力を証明するものではない |
| AddressSanitizer / UndefinedBehaviorSanitizer | native suite PASS、リーク・メモリ境界・未定義動作の指摘なし |
| APK signature | apksigner verify PASS。debug v2署名 |
| ZIP page alignment | `zipalign -c -P 16 4` PASS |
| APK内ELFのLOAD alignment | libpianopocket / libc++_shared / androidx.graphics.path全て `0x4000` |
| 自作fixture生成と監査 | PASS、4 velocity層 / release / pedalを含むZIPとJSON report生成 |
| Helsinki v1.6実音源 | 344 WAVデコード、5プリセット×1320ケースPASS。3秒paced streaming各cache miss=0、I/O errors=0。6番目は配布XMLの余分な終了タグで拒否 |
| 純正IR | 5種の完全delta response、誤差0.0002以内・256 framesの処理遅延を確認 |
| 本番ZIPインポーター | 全2073 entries / 1.53GBを安全展開、実プリセット6件を登録対象として検出 |
| Galaxy S26 / Android 16 | 未接続。起動、発音、タッチ、USB / BLE MIDI未検証 |
| Windows VST3 | Wine / Box64未導入。host起動・pluginロード・MIDI / audio・遅延未検証 |

Lintには依存の新版案内、ARM64のみであることに関するChromeOS案内、API世代・ストレージ容量API等の警告が残っています。エラーを抑制するbaselineは使用していません。

## 0.1.0時点のホスト合成ベンチマーク（参考）

256 frames、48kHz、常駐の自作stereo sample、100 blocks平均。レイテンシ測定ではありません。ストリーミングやHelsinkiの5マイク／IR負荷は含まず、Galaxy S26の性能を表すものではありません。

| Sample voices | 平均ブロック処理時間 |
| --- | --- |
| 32 | 52.75 µs |
| 80 | 126.30 µs |
| 128 | 202.33 µs |

## 要求された完成条件の扱い

1〜8と10のためのAndroidコードは実装し、ビルド・ホストテストで検証しました。ただし「Androidで起動する」「実際に鍵盤／MIDIで鳴る」「再起動後に利用できる」を**実機成功として報告していません**。SAFとライブラリ保存、Android MIDIハードウェア、音声経路は実機試験が必要です。条件9は実ファイルの構造・デコード・ホスト発音を検証し、[結果と残る互換性差](HELSINKI.md)を記録しました。純正との音響比較と実機試験は未実施です。全条件を達成した完成品ではありません。

## 再現コマンド

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
cmake -S native -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native -j 4
ctest --test-dir build/native --output-on-failure
cmake -S native -B build/sanitized -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_CXX_FLAGS='-fsanitize=address,undefined -fno-omit-frame-pointer' \
  -DCMAKE_EXE_LINKER_FLAGS='-fsanitize=address,undefined'
cmake --build build/sanitized -j 4
ASAN_OPTIONS=detect_leaks=1 ctest --test-dir build/sanitized --output-on-failure
apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk
zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
```

## 0.2.0の追加検証

voice-scope table、tagミキサー、CC64のnote 1 / 2マッピング、整数小数frame、IRメニュー、+12dB gain、256/2048 hybrid FFTを追加。ASan/UBSanのnative suiteと実XFマッピングprobeを通過。実サンプルはCIに置かず、利用者が指定するprivateディレクトリから検証します。詳細と再現コマンドは[HELSINKI.md](HELSINKI.md)。

## GitHub公開後のCI状態

main / v0.2.0-alphaのプッシュは成功しました。GitHub Actionsの初回実行は `The job was not started because your account is locked due to a billing issue.` というGitHub側の理由でジョブ開始前に停止しています。ローカルのビルド・テスト成功をホステッドCI成功とは扱いません。[実行記録](https://github.com/tstyr/PianoPocket/actions/runs/38220736938)。本体の不具合ではなく、GitHubアカウント側で解消が必要です。
