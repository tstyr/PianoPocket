# Helsinki 1967 Pro v1.6 — actual-file compatibility report

2026-10-12、利用者が提供した **Helsinki1967 DecentSampler v.1.6.zip** を取得・解析し、本番KotlinパーサーとC++エンジンで検証しました。音源・IR・画像・実XMLはソース／APK／GitHub Releaseに含めません。

- Archive: 885,711,602 bytes、2073 entries、展開1,533,399,826 bytes。
- SHA-256: `23d97066addc749e00a293995bcb581fa79a955197f28aef1537f4273768fe49`。
- 実WAV: 344。うちIR 5、音源WAV 339。プリセットが参照する音源は338、NOPEDALは336。
- 341 WAVが48kHz stereo PCM24、残りはIRの44.1kHz PCM24、48kHz PCM16、96kHz PCM24。
- 6プリセットのうち5つはパース・ホスト発音検証PASS。1つは配布XMLの構文エラー。

**Android端末は未接続です。Galaxy S26の起動、USB / BLE MIDI、エンドツーエンドレイテンシ、純正Decent Samplerとの録音比較は未検証です。** この報告は構造・デコード・ホスト音声処理の検証です。

## 配布プリセット

| ファイル | Regions / groups | 結果 |
| --- | --- | --- |
| Helsinki1967.dspreset | 338 / 5 | パース・ホスト発音PASS |
| Helsinki1967 NOPEDAL.dspreset | 336 / 4 | パース・ホスト発音PASS |
| Helsinki 1967 XF.dspreset | 338 / 14 | パース・XF table・ホスト発音PASS |
| Helsinki 1967 XF NOPEDAL.dspreset | 336 / 13 | パース・XF table・ホスト発音PASS |
| Helsinki1967 Multi Out.dspreset | 338 / 5 | PASS、AUXは非対応でステレオへ混合 |
| Helsinki1967 Multi Out NOPEDAL.dspreset | — | **83行目の余分な `</effects>` で拒否**。原本を自動改変しない |

本番SafeArchiveでZIP全体を展開しました。`__MACOSX` と `._` を登録対象から除き、正しい6プリセットを表示します。

## Velocityとマイク

通常版のattack範囲は **1–33 / 34–77 / 78–110 / 111–127**。ファイル名の数字とは異なるため、実XMLを優先します。57 / OM / ROOMが各103 zones、RELOMが27、pedalが2。キー範囲は21–108（88鍵）。

XF版は3マイク×4層の12 attack groups。範囲は **1–43 / 25–87 / 69–120 / 102–127**。`midiVelocity scope="voice" modAmount="1"` からgroup AMP_VOLUMEへの `multiply / table` bindingを12本使用します。

テーブル座標をそのまま解析し、velocity / 127を入力として区分線形補間し、128値のカーブに準備します。Note On時にそのvoiceへ乗算し、他の音に影響させません。mic57 / micOM / micROOMタグで4層をまとめて調整し、release / pedalも実タグを解決します。純正の位相やラウドネスとの比較は未実施です。

初期値はRoom=1、57=1、OM=1、Release=0.25、Pedal noise=0.25、Master=1、Reverb Mix=0。XMLのpolyphonyタグは **70** でした。配布READMEの80より実XMLを優先し、初期sample-voice capを70にします。タグごとのpoolや純正のvoice計数方式は完全再現していません。設定で変更できます。

## ペダル

- DOWNはrootNote / key rangeが1、onLoCC64=64 / onHiCC64=127。
- UPはrootNote / key rangeが2、onLoCC64=0 / onHiCC64=63。
- ampVelTrack=0で、ノイズ音量はnote velocityと独立。
- CC専用lookupに登録してキー範囲から独立させ、原音ピッチで再生。
- 64境界を越えたときだけ発音し、継続的な半踏みCCによる再発音を抑制。純正とは意図的な差があり、配布READMEにも連続ペダルによるグリッチとNOPEDAL版の説明があります。
- release trigger、0.6秒release、sustain保持を実装。曲線形状、releaseTriggerDecay、共鳴、半踏み減衰は純正と同一の保証なし。
- Multi Outの整数小数表記start / end（`0.000`等）に対応。endがWAV frame countならEOFとして扱います。

## 純正リバーブ設定

チェーンは **Convolution → Gain +12dB**。Gainの12は約3.981倍へ変換します。一般的なalgorithmic reverbではありません。

IRメニューは1-based value=1、初期Lady Chapel。Reverb Mix=0をDSP初期値へ反映します。

| 選択名 | 入力 | 48kHz frames / 長さ |
| --- | --- | --- |
| Lady Chapel | 44.1kHz stereo PCM24 | 288000 / 6.000秒 |
| Usina del Arte | 48kHz stereo PCM16 | 93453 / 1.947秒 |
| School Hall | 96kHz stereo PCM24 | 63199 / 1.317秒 |
| Small Room | 48kHz stereo PCM24 | 129920 / 2.707秒 |
| Plate | 48kHz stereo PCM24 | 157824 / 3.288秒 |

IRを完全に読み込み、OS sample rateへ線形変換。256 frame早期／2048 frame後期の分割FFTで、追加遅延256 frames（48kHzで約5.33ms）。全5 IRのdelta responseを最後まで比較し、読み込んだIRに対して256 frame差・誤差0.0002以内を確認。IR正規化、dryの遅延補償、純正resampler／convolverとの同一性は未検証です。

IR変更はworkerでDSP準備後、短時間streamを停止して交換。旧IRのtailは切替時に引き継ぎません。通常のnote-off後のtailは保持します。IRのCC BY 4.0／作者creditsは利用者の配布物に残し、アプリには同梱しません。

## ホスト検証

88鍵×15 velocities=1320ケースを各プリセットで実行し、5プリセット合計6600。最初4096 framesでfinite・full-scale以内・非無音を確認。XF最大6 attack sample voices、通常版最大3を観測。先頭＋cache residentは30,413,440〜30,544,528 bytes（約29MiB）で、DSP・UI・一時メモリは含みません。

各3秒のpaced sustain / streamingでcache misses=0、read errors=0。高速offline matrix／tailではworkerが実時間に追いつかずmissが生じますが、実機の音切れ率とは扱いません。5マイク／70 voices／IR mix>0の長時間実機ストレスは未実施です。

## 再検証

```sh
python3 tools/inspect_instrument.py /path/to/Helsinki --output artifacts/xml-audit.json
./gradlew inspectOwnedPresets -PinstrumentDir=/path/to/Helsinki \
  -PreportDir=/path/to/private-report -PownedArchive=/path/to/Helsinki-v1.6.zip
cmake -S native -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native -j 4
build/native/owned_preset_probe '/path/to/private-report/Helsinki 1967 XF.dspreset.mapping'
```

構造監査の出力には実XMLが入るため、共有前に利用条件を確認してください。production parser reportとnative metricsは設定の概要だけを含みます。実XML原本、音声、利用者のDrive URLは公開しません。

実機では通常版→XF、各マイク単独／混合、CC64 0→63→64→127→80→63→0、長押し・再起動・USB hotplug・BLEを確認します。32 / 64 / 70 / 80 sample voices、IRなし／ありで各5分、XRun・cache miss・PSS・DSP予算率・熱状態を記録。外部基準信号と端末音声を同時録音してMIDI-to-audioの中央値・p95を測り、Oboe推定値と区別してください。
