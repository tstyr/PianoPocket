# Helsinki 1967 Pro v1.6 verification

## 現時点の事実

2026-10-11に[Pianobook配布ページ](https://www.pianobook.co.uk/packs/helsinki-1967-pro/)の公開説明を確認しました。4 velocity layers、release、pedal noise、SM57 / OM1 / Roomの収録説明、Decent Sampler向けIRを確認しています。2026-09-04のv1.6更新には「Helsinki 1967 XF」と旧クロスフェードなし版の選択肢、切替点で小さな位相問題の可能性が記載されています。

これらは**公開説明の確認**であり、実配布XMLの解析ではありません。ダウンロードボタンはログインを要求しました。この環境にはユーザー所有の `.dspreset` / `.dslibrary` / WAV / IRはなく、Pianobookへのログインも行っていません。従って、v1.6のXFやペダル仕様を仮定で固定していません。

## 必要な実ファイル

- 正規に取得・展開した**Decent Sampler版v1.6**の全フォルダー。
- 通常版とXF版の `.dspreset`、参照する全WAV。
- IRフォルダー、ライセンス・credits・README。
- 可能ならv1.6配布アーカイブのSHA-256と入手日。音源の再配布は不要です。
- 比較用Decent Sampler実行環境とMIDI入力または同一MIDIシーケンス。

## XML監査

```sh
python3 tools/inspect_instrument.py /path/to/user-owned/Helsinki1967Pro \
  --output artifacts/helsinki-v1.6-audit.json
```

このツールはグループの**実名**と属性、velocity範囲、グループ・global effect順序、全UI binding、MIDI設定、modulators、未対応mapping属性、欠落ファイル、XML hashを出力します。レポートにはXML設定が入るため、共有前にライブラリ利用条件を確認してください。サンプル音声はコピーしません。

通常版・XF版について次を照合します。

1. 4層の切替値とloVel / hiVelの重複、ampVelTrack、各sample / group / global volume。レイヤー境界の値を推測しない。
2. XFがmodulator、velocity_binding、translation table、その他どの機構で実現されているか。現在の未対応項目なら、必要な公式パラメータを追加してから比較する。
3. SM57 / OM1 / Room / Release / Pedal Noiseの実グループ数、名前、UI knobのtarget index、初期値、ミュート処理。複数groupが1マイクに対応する場合はまとめ方を実XMLから決める。
4. `trigger="release"`、releaseTriggerDecay、CC64トリガーの範囲、note / velocity条件、ampEnvEnabled、pitchKeyTrack。半踏み時のCC64反復と上昇／下降の挙動。
5. IRの実パス・フォーマット・長さ、メニューによるIR切替、convolution mixと有効状態、ReverbのroomSize / damping / wetLevel、XMLチェーン順序。
6. ループ、複数output / buses、マイクpurge / enabled等。未対応機能はすべてレポートに記録する。

## 実機と比較

| 検証 | 手順と判定 |
| --- | --- |
| 起動・インポート | Galaxy S26 Android 16にAPKを入れ、通常版をロード。全missing referencesゼロ、警告を保存 |
| マッピング | 最低音・最高音・各ゾーン境界を弾く。無音・別音への誤配置がない |
| Dynamics | 同一noteをvelocity 1..127で録音。実XMLの各層境界±2を重点比較。音量・音色・位相の不連続を確認 |
| Release | 短音・2秒・長押しのnote-off、ペダル保持中のnote-off、sample自然終了後のnote-offを比較 |
| Pedal | CC64: 0→63→64→80→127→80→64→63→0。ノイズの再発音回数・キー保持・ペダル解除時のreleaseを比較 |
| Microphones | 各マイク単独と混合。名前ではなくXML target groupで確認 |
| Reverb | 同一音・同一IR・同一mixを録音。tail、処理順序、追加遅延を比較。独自Reverbとの差は近似として記録 |
| MIDI | USB hotplug、複数channel、note-on velocity0、CC7 / 11 / 64 / 123、pitch bend、BLEを別々に確認 |
| Persistence | アプリ終了・再起動後、前回プリセットが同じサンプルで演奏可能。元のSAFファイルを移動しても動作 |
| Performance | 32 / 64 / 80 sample voices、5マイク有効、IRなし／ありで各5分。XRun、cache miss、read errors、PSS、DSP予算率を記録 |

## レイテンシの測定

有線・USB・内蔵・Bluetooth出力を別に扱います。Oboeの推定出力値をエンドツーエンド実測値として報告しません。MIDI送信時刻を外部測定系で基準信号に記録し、端末からのアナログ出力を別チャンネルに録音して、発音開始までの差を複数回測定します。最小・中央値・p95、バッファ、sample rate、出力機器、MIDI機器、IR処理の有無を記録します。Convolutionは256 output framesの処理遅延が追加されます。

公開ページにはIRの一部にCC BY 4.0のcreditsがありますが、PianoPocketにはいずれのIRも同梱しません。利用者は配布物内の実ライセンスに従ってください。
