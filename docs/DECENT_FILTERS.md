# Decent Samplerのローパスフィルター

PianoPocket 0.14.1から、`lowpass_4pl`を読み込めます。

公式の開発者ガイドでは、`lowpass_4pl`は2-pole resonant filterである`lowpass`の旧名として定義されています。名前の末尾から4-poleの別アルゴリズムを仮定せず、既存のローパス処理を使用します。XMLのfrequency・resonance・enabled、チェーン順序、UIコントロールのバインディングを維持します。

| XML | 処理 |
| --- | --- |
| `type="lowpass"` | 既存の2-poleローパス |
| `type="lowpass_4pl"` | 同じローパス。UIでは`lowpass`と表示 |
| `frequency` | カットオフ周波数、既定22000 Hz |
| `resonance` | Q、既定0.7 |
| `enabled` | バイパス、既定true |
| `FX_FILTER_FREQUENCY` / `FX_FILTER_RESONANCE` / `ENABLED` | 対応するつまみの連動 |

DSPはPianoPocketの実装です。カットオフは20 Hz以上かつ端末サンプルレートの49%以下に制限し、リアルタイム変更とバイパスを約15 msで滑らかに反映します。純正Decent Samplerとの録音比較は未実施です。別の未対応エフェクトは置き換えず、その名前を含む日本語のエラーを表示します。`lowpass_1pl`とグループ単位のエフェクトは、この変更では追加していません。

## 検証

- 本番XMLパーサーで、旧名・現行名の既定値／明示パラメータ／バイパス／チェーン順序／バインディングを比較。
- C++の実音声テストで、44.1 / 48 / 96 kHzの低音保持・高域減衰・ステレオ分離・パラメータ変更・バイパスを確認。
- オーディオ処理中の動的確保が0であることを、既存のネイティブテストで確認。
- 自作の6秒ステレオサイン波と2種類のXMLを本番パーサーに通し、C++でそれぞれ10秒のWAVを書き出しました。UIバインディングを反映した音声がバイト単位で一致し、読み込みエラー・キャッシュミスは0でした。[結果](data/legacy-lowpass-summary.json)。

利用者の今回の音源XMLは未提供です。このエラーの再現と修正は自作のプリセットで検証し、その音源全体への対応や実機動作を検証したとは扱いません。

## 参照

[公式ガイドのフィルター定義](https://github.com/DecentSamples/DecentSampler-Developers-Guide/blob/1a1f8535003a04fbbb15f0a01d7b232ccab67987/docs/source/the-effects-element.md#low-pass-band-pass-and-hi-pass-filter)を2026-10-14に再取得しました。資料のSHA-256は `6c72fb783770f48b50e10b53bb6ca2e8994858be42901ca7575b4a38f66b5e7c`。プラグインのソースやアルゴリズムは取り込んでいません。
