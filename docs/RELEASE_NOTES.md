# PianoPocket 0.3.1-alpha — Helsinki reverb clipping fix

Helsinki1967のSchool Hall / Reverb Mix 0.71で発生した音割れを実IRで再現し、修正しました。IRの大きい増幅と後段の+12dB Gainで、旧tanh出力保護が波形をつぶしていたことが原因です。

- IRのWet音量をステレオのバランスを保って補正。マスター全体を下げる代わりに、IR側の過大な増幅を抑制します。
- 波形を変形するtanh方式を、ステレオ連動の先読みリミッターへ変更。低音の周期内で音量が揺れないよう保持処理を追加。
- Settingsに保護前ピーク・減衰dB・先読み時間を表示。内部の追加遅延は約1msです。
- Reverb Mixと重複するIR Mixスライダーを整理し、1つのコントロールで調整。
- WAV書き出しでは先読み遅延を補償し、ノートの開始位置・ファイル長を維持。

実IR5種 × Mix 0 / 0.3 / 0.71 / 1 × 単音 / 4音 / 12音の60ケースで検証。School Hall / Mix 0.71は単音で出力保護にかからず、4音は最大約2.52dBの音量調整で保護。native / ASan / UBSan、36 JVM tests、Androidビルド・LintはPASS。修正版のユーザー端末での聴感確認・実測レイテンシは未検証です。

IR正規化はPianoPocketの近似方式です。Decent Sampler純正の内部方式との一致は未確認で、インポート時に警告します。MIDIライブラリ・ロール・録音・各種書き出しは0.3から維持しています。Helsinki音源・IR・原本XML、ユーザーMIDIは同梱していません。

[修正内容・検証・実機での比較手順](https://github.com/tstyr/PianoPocket/blob/main/docs/REVERB_FIX.md)
