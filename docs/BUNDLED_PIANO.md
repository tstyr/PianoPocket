# Pocket Upright

初回起動時に使える、実録音のアップライトピアノ。SFZとWAVとしてアプリ内にインストールされます。

| 項目 | 内容 |
| --- | --- |
| 元音源 | VSCO 2 Community Edition / Upright Piano |
| 録音 | Simon Dalzell / Ivy Audio、2015–2016年冬 |
| 配布 | Versilian Studios / Sam Gossner |
| サンプルのライセンス | CC0 1.0 Universal |
| 形式 | ステレオ、44.1kHz、PCM16 WAV |
| 範囲 | A0–C8 / MIDI 21–108 |
| 構成 | 12収録キー × 3ベロシティ = 36 WAV |
| 容量 | 圧縮時33.2MiB、インストール後57.2MiB |

[公式案内](https://vis.versilstudios.net/vsco-community.html) · [元のリポジトリ](https://github.com/sgossner/VSCO-2-CE) · [ライセンス全文](../app/src/main/assets/instruments/PocketUpright/LICENSE.txt)

元リポジトリのコミット `440300901dfe9275fd84e0b7763af1f8443ae62e` の `Keys/Upright Piano` を使用しています。同フォルダーの `Info.txt` に録音者と再配布許可が記載され、リポジトリのLICENSEはCC0です。Pianobookの音源は含みません。

## モバイル向けの調整

- 収録キーを選び、隣接キーへピッチ補正して88鍵に割り当てています。元音源全体の密度を再現するものではありません。
- 3段階のベロシティは元の範囲（0–60 / 61–110 / 111–127）を使用。元マッピングのレイヤーゲイン（23 / 10 / 2 dB）を適用し、全体に共通の音量補正を加えています。
- ステレオと44.1kHzを維持し、PCM24からPCM16へ変換しました。
- 長い残響は最大12秒まで収録し、末尾150msをフェードします。ループ・リリース専用サンプル・ラウンドロビンはありません。
- WAVはgzipで可逆圧縮し、`.pack` という名前でAPKへ格納します。初回に展開するため、演奏中にgzipを解凍することはありません。

SFZマッピングと準備スクリプトはMITライセンスです。全サンプルの出典ファイル名、元ファイル・変換後WAVのSHA-256、変換値は [manifest.json](../app/src/main/assets/instruments/PocketUpright/manifest.json) に残しています。アプリの設定からも出典とCC0全文を確認できます。

## 音源の再生成

通常のアプリビルドで音源を取得し直す必要はありません。変換をやり直す場合だけ、Python 3とffmpeg / ffprobeを用意します。

```sh
python tools/prepare_bundled_piano.py --cache /tmp/pocket-upright-source
```

スクリプトは固定コミットから36ファイルを取得し、SFZ・圧縮WAV・出典・ライセンスを生成します。元録音は再取得可能で、利用条件はCC0のままです。
