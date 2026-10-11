# Windows VST3 experiment — isolated from the Android sampler

**実行環境未準備・VST未起動・MIDI／音声出力／遅延は未検証。** このフォルダーは調査と実験支援コードです。PianoPocketのビルドには含まれず、動くWindows VST互換機能としてUIに表示しません。

## 実現性

Galaxy S26のARM64上でWindows x86-64 VST3を実行する場合、Android対応のWineユーザーランド、Box64によるx86-64変換、Windows VST3ホスト、Decent Samplerの正規Windowsプラグインが必要です。Wineだけではx86-64 ISAの変換は行えません。Wine / Box64の組み合わせでも、AndroidのBionicとLinuxのglibc、32-bit依存、グラフィックス、MIDI・音声デバイスの橋渡しを解決する必要があります。

[Winlator](https://github.com/brunodev85/winlator)はWine / Box64を用いた既存の参考実装です。まずその既存ディストリビューションを別アプリとして用い、実測を行うのが現実的です。WineやBox64を自作しない、ネイティブエンジンに同居させない方針です。Windowsプラグインがロードできても、USB MIDIのWindows host側への橋渡しや低遅延オーディオが使えることは別途確認が必要です。

| 検証項目 | 状態 |
| --- | --- |
| この作業環境のWine / Box64 | `probe.py`で調査、両方未導入 |
| Winlator Android実行 | 未実施、Android実機なし |
| Windows VST3ホスト起動 | 未実施 |
| Decent Sampler VST3ロード | 未実施、Windowsプラグイン未取得 |
| MIDI / audio routing | 未実施 |
| latency / CPU / RAM | 実測値なし |

## 実験手順

1. 正規Winlatorを独立した実験端末／プロファイルで準備。バージョン、Wine / Box64設定、端末、Android版を記録。
2. 利用者が入手したWindows x64 VST3ホストを起動。最初にプラグインなしのaudio testを行い、Windows hostの音声出力経路を確認。
3. Decent Samplerの正規Windows x64 VST3を利用者がインストール。ホストのプラグイン検索で認識するか確認。プラグイン自体はこのリポジトリへ保存しない。
4. Helsinkiの前に自作fixtureでnote on/off。ホストのMIDIモニターにUSB入力が届くか確認。届かない場合、Android側MIDIをWineへ渡す橋渡しの研究を独立して行う。未接続を接続成功扱いしない。
5. Helsinki通常版→XF版、effect bypass→reverb / IR有効。32 / 64 / 80 voicesを別々に試験。
6. host outputを外部録音してMIDI-to-audio遅延の中央値とp95を測る。単にバッファ設定を遅延値として記録しない。
7. host / Wine / Box64のCPU、PSS / RSS、thermal throttling、XRun／音切れを5分以上採取し、同じ端末のPianoPocketネイティブ版と比較。

Linux ARM64のWine / Box64環境では次の独立probeを使用できます。WinlatorのAndroid実行環境へそのまま適用できるとは限りません。

```sh
# capability inspectionのみ
python3 experiments/windows-vst/probe.py --output artifacts/vst-capabilities.json
# ユーザーが準備した実行コマンドを30秒監視。最後にプロセスグループを終了する
python3 experiments/windows-vst/probe.py --seconds 30 --output artifacts/vst-probe.json \
  -- box64 /path/to/wine64 /path/to/UserOwnedHost.exe
```

probeは `ps` のCPU / RSSを時刻付きで保存します。Windows host起動成功、プラグイン認識、MIDI、発音は自動判定しません。`audio_latency_ms` は未測定時 `null` のままにします。`ps`には他のプロセス名も含まれるため、共有前に確認してください。

## ライセンス

- Wine: LGPL-2.1-or-later。[upstream LICENSE](https://github.com/wine-mirror/wine/blob/master/LICENSE)
- Box64: MIT。[upstream LICENSE](https://github.com/ptitSeb/box64/blob/main/LICENSE)
- Winlator: root LICENSEはLGPL-2.1。配布に含まれる各コンポーネントの条件も別途必要。[upstream LICENSE](https://github.com/brunodev85/winlator/blob/main/LICENSE)
- Windowsホスト、Decent Sampler VST、サンプル音源: 各配布元のライセンス／EULAを利用者が確認。無料配布を再配布許諾やオープンソースと同一視しない。

本リポジトリではWine / Box64 / Winlatorのコードをコピー・改変・バイナリ再配布していません。将来同梱する場合は対応するsource提供、変更箇所、NOTICE、各依存のライセンスを揃えてから行います。
