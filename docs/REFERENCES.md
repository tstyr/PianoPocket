# Sources checked on 2026-10-11

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
