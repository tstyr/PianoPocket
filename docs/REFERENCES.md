# Sources checked on 2026-10-11

2026-10-13: [SFZ Format](https://sfzformat.com/)のヘッダー、pitch_keycenter、amp_veltrack、loop_mode、endを参照。内蔵音源は [VSCO 2 CE](https://github.com/sgossner/VSCO-2-CE/tree/440300901dfe9275fd84e0b7763af1f8443ae62e) のCC0 LICENSEとUpright Piano / Info.txtの録音者・再配布許可を確認。[出典と変換](BUNDLED_PIANO.md)。

| 資料 | 用途 |
| --- | --- |
| [Decent Sampler developer guide](https://decentsampler-developers-guide.readthedocs.io/en/latest/index.html) | 公式形式説明。取得時の表示は1.36.1。Helsinki v1.6のXMLそのものではない |
| [groups / sample](https://decentsampler-developers-guide.readthedocs.io/en/latest/the-groups-element.html) | volumeの乗算、pan継承、tuningの半音単位、キー・velocity、CC条件、release、envelope、loop |
| [effects](https://decentsampler-developers-guide.readthedocs.io/en/latest/the-effects-element.html) | 各エフェクト属性、既定値、gainのdB / linear、convolutionのirFile |
| [bindings](https://decentsampler-developers-guide.readthedocs.io/en/latest/appendix-b-the-binding-element.html) | FX_REVERB_*、FX_MIX、FX_MOD_DEPTH、FX_MOD_RATE、FX_FEEDBACK、FX_WET_LEVEL、AMP_VOLUME等 |
| [Helsinki 1967 Pro](https://www.pianobook.co.uk/packs/helsinki-1967-pro/) | 公開release notes、v1.6のXF、velocity layers、ペダル、IR credits。ログインが必要な配布物は未取得 |
| [Oboe 1.9.3](https://github.com/google/oboe/tree/1.9.3) | 実ソースとApache-2.0 LICENSEを取得。ビルドに必要な部分をvendor化 |
| [Android MIDI](https://developer.android.com/reference/android/media/midi/MidiManager) | MidiManager / MidiReceiver / Bluetooth MIDI API |
| [Android SAF](https://developer.android.com/guide/topics/providers/document-provider) | 利用者によるファイル・フォルダー選択 |
| [Android 16KB page sizes](https://developer.android.com/guide/practices/page-sizes) | APKとELF alignment、NDK対応 |
| [Winlator](https://github.com/brunodev85/winlator) | 独立したWindows互換実験の参考。root LICENSEを確認 |
| [Wine](https://github.com/wine-mirror/wine) / [Box64](https://github.com/ptitSeb/box64) | ライセンス・実験環境の検討。実行は未実施 |

XML形式のサポートは、自作パーサーと自作DSPによるものです。Decent Samplerのソース、独自アルゴリズム、商標画像、配布プラグインは使用していません。公式ドキュメントに記載されるパラメータを受け付けても、音響的同一性は保証しません。

2026-10-14: 公式ガイドのGitHubソースを再取得し、`lowpass_4pl`が`lowpass`の旧名であることを確認。[実装と検証](DECENT_FILTERS.md)。

2026-10-12追記: 利用者提供のv1.6実ZIPを取得し、[midiVelocity modulators](https://decentsampler-developers-guide.readthedocs.io/en/latest/the-modulators-element.html)、table補間、tag AMP_VOLUME、menuの1-based valueとFX_IR_FILEを公式資料で再照合。配布物のREADMEとIR Creditsも確認。音源・IR・画像・XML原本は公開していません。

2026-10-12 MIDI拡張: [tstyr/openthesia-android](https://github.com/tstyr/openthesia-android/tree/576acf862cf7bcbe7c27ffc4c4385d7d271f8f59)のREADME、PianoRollCanvas、PlaybackController、MIDI models、root GPL-3.0 LICENSEを確認。Falling Notes、88鍵、ポート選択の動作を参考にし、ソースは取り込んでいません。今回のSMF codec、interval segment tree、recording、render / key geometry / transport / WAV writerは独自のMITコードです。新しい外部依存はありません。

2026-10-12 0.3.1音割れ調査: [公式guideのGitHubソース](https://github.com/DecentSamples/DecentSampler-Developers-Guide/blob/1a1f8535003a04fbbb15f0a01d7b232ccab67987/docs/source/the-effects-element.md#convolution-effect)を確認。convolutionのmix / irFileとwet-only / dry-onlyの定義を確認し、IR正規化方式は公開されていないためPianoPocketのL2音量補正を近似として警告・文書化。追加のDSPライブラリは導入していません。
