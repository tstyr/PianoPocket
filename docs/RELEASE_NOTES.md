# PianoPocket 0.2.0-alpha — Helsinki v1.6 native compatibility

ユーザー提供のHelsinki 1967 Pro Decent Sampler版v1.6を実際に解析し、通常版／XF版をネイティブエンジンでホスト検証しました。

- voice-scope velocity tableによるXF、タグ単位のマイクミキサー、Release / Pedal Noise、instrument Master。
- CC64をkey rangeから独立させ、連続ペダルCCによるノイズ再発音を抑制。
- 5種の純正IR選択、Convolution→+12dB Gain、初期Reverb Mix=0をXMLから反映。
- 6秒IRを切り詰めず読込。256 / 2048 frame分割FFT、追加遅延256 frames。
- macOS sidecarを音源として登録しない。Multi Out NOPEDALの配布XMLエラーを明示。
- 344 WAV、5プリセット×1320発音ケース、5 IRの完全delta response、paced streaming、回帰・メモリ検査。

**Galaxy S26 / Android 16の起動・タッチ・USB / BLE MIDI・レイテンシは実機未検証。純正プラグインとの音響的同一性は保証しません。** Multi Outはステレオ混合のみ、Windows VSTは独立した調査・実験手順までです。

Pianobookのサンプル・IR・画像・実XMLはAPK／ソース／Releaseに含めません。利用者が正規に取得してインポートしてください。デバッグ署名ARM64 APK、ソースZIP、自作fixture、検証概要、SHA-256を配布します。

詳細: [README](https://github.com/tstyr/PianoPocket#readme)、[実ファイル検証](https://github.com/tstyr/PianoPocket/blob/main/docs/HELSINKI.md)、[検証結果](https://github.com/tstyr/PianoPocket/blob/main/docs/VALIDATION.md)。

## ダウンロード

Releaseへの直接添付はアップロード先の認証エラーになったため、同じGitHubリポジトリのrelease-artifactsブランチに配布ファイルを保存しています。以下から取得できます。

- [Android ARM64 デバッグAPK](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.2.0-alpha-arm64-debug.apk)
- [ソースZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.2.0-alpha-source.zip)
- [検証結果ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.2.0-alpha-validation.zip)
- [自作テスト音源](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PocketFixture.zip)
- [SHA-256一覧](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.2.0.txt)
