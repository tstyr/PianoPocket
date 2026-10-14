# 開発環境

JDK 17、Android SDK 36、NDK 28.2.13676358、CMake 3.22.1を使用します。SDKの場所を `local.properties` の `sdk.dir` に指定してください。

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
cmake -S native -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native -j2
ctest --test-dir build/native --output-on-failure
```

APKは `app/build/outputs/apk/debug/app-debug.apk` に生成されます。Androidの端末テストは別途接続した端末で `./gradlew connectedDebugAndroidTest` を実行します。複数のネイティブテスト実行は共通の一時ファイルを使うため、同時に走らせないでください。

## 資料

- [検証記録](VALIDATION.md)
- [バージョンごとの変更・検証](INCREMENTAL_RELEASES.md)
- [Decent Samplerフィルター](DECENT_FILTERS.md)
- [音源のダウンロード方式](DOWNLOADS.md)
- [Windows VSTの実現性調査](../experiments/windows-vst/README.md)（アプリ機能には含まれません）

Helsinkiの検証には利用者が正規に入手した配布ファイルが必要です。[対象ファイルと手順](HELSINKI.md)。その音源、IR、個人の演奏はリポジトリやAPKに含めないでください。
