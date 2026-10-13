# SFZ音源の読み込み

音源ライブラリでSFZファイルを選び、そのSFZが置かれたフォルダーを選択します。サンプルとincludeファイルをアプリ専用領域へコピーします。ZIPにまとめた音源も読み込めます。

## 対応範囲

| 設定 | 対応 |
| --- | --- |
| ヘッダー | `<control>` / `<global>` / `<master>` / `<group>` / `<region>`、階層ごとの継承と上書き |
| サンプル | `sample` / `default_path`、相対パス、空白・日本語・引用符・Windows区切りのパス。WAVのみ |
| キー | `key` / `lokey` / `hikey` / `pitch_keycenter`、数値とC4などの音名。C4=60 |
| ベロシティ | `lovel` / `hivel` / `amp_veltrack`（0–100） |
| 音量・定位 | `volume`（dB） / `amplitude`（%） / `pan`（−100〜100） |
| ピッチ | `transpose` / `tune` / `pitch_keytrack`。`pitch_keycenter=-1` でキートラッキングを無効化 |
| エンベロープ | `ampeg_attack` / `ampeg_decay` / `ampeg_sustain`（%） / `ampeg_release` |
| 再生範囲 | `offset` / `end`。SFZのinclusive endをエンジンのexclusive endへ変換 |
| 再生モード | `no_loop` / `one_shot` / `loop_sustain` / `loop_continuous` |
| ループ | 明示された `loop_start` / `loop_end`。continuousはリリース中もループを保持 |
| トリガー・ペダル | `trigger=attack` / `release`、MIDI CC64、`locc64` / `hicc64` |
| クロスフェード | `xfin_lovel` / `xfin_hivel` / `xfout_lovel` / `xfout_hivel`。gain曲線のみ、power曲線は近似として警告 |
| テキスト | 行・ブロックコメント、`#include`、`#define`とマクロ展開 |

SFZの既定 `amp_veltrack=100` は音量を `(velocity / 127)²` で変化させます。Decent Samplerの既存カーブを変えず、SFZにだけ適用します。キー追従を無効にしてもチューニングは適用されます。

## 制限

フィルター、LFO、任意CCのモジュレーション、CCでの発音、キー切替、ランダム選択、ラウンドロビン、負の `amp_veltrack`、OGG / FLAC、埋め込みWAVループマーカーには未対応です。未対応opcode・ヘッダーは音源パネルと `PianoPocket.Import` ログに警告を出します。

ランダム・シーケンス・キー切替・未対応CC条件を持つリージョンは、複数候補が同時発音することを避けるためスキップします。フィルターなどの未対応処理は適用されません。再生可能なリージョンが残らない場合は読み込みエラーになります。

リリースサンプルは押鍵時のベロシティで、サステイン解除後に発音します。SFZの `rt_decay` や `release_key` による厳密な離鍵挙動は未対応で、警告を出します。短いエンベロープにもクリック防止用の最小フェードを入れます。MIDIピッチベンドはアプリ共通の±2半音です。

`default_path` とサンプル参照はメインSFZを基準とし、include参照は記述されたファイルの場所を基準に解決します。親参照はインポート済み音源フォルダーの内側に限ります。SFZ単体の読み込みで上位フォルダーが必要な場合は、必要なフォルダー構成をZIPにまとめてください。

SFZは1ファイル4MiB、includeは64回・深さ16、全ソースは64MiB、展開後8MiB、リージョン30,000、グループ256まで。絶対パス、ドライブ名、音源ルートを抜ける親参照、include循環、マクロ循環を拒否します。ZIPの既存の展開サイズ・ファイル数・圧縮率の制限も適用します。解析・コピー・展開はバックグラウンドで行います。

仕様の参照: [SFZ Format](https://sfzformat.com/) / [amp_veltrack](https://sfzformat.com/opcodes/amp_veltrack/) / [pitch_keycenter](https://sfzformat.com/opcodes/pitch_keycenter/)。SFZ全仕様の互換実装ではありません。

## 検証

0.10.0で以下を実行しました。

- JVMテスト77件。SFZの継承・マクロ・include・パス・ループ・警告・資源制限と、内蔵音源の全88鍵 × ベロシティ1–127の割り当てを確認。
- Pocket Uprightの実WAV36ファイルのSHA-256、ステレオPCM16・44.1kHzを確認。
- 本番SFZパーサーの出力をC++エンジンへ読み込み、1,320のノート・ベロシティケースを発音。読み込みエラー0、3秒のペース付きストリーミングでキャッシュ欠落0。
- 実音源の48kHzステレオWAV24書き出し。480,000フレーム、読み込みエラー0。
- C++テストとASan / UBSan。ワンショットの離鍵・停止、SFZのベロシティ曲線、連続ループ、独立したチューニングを確認。テストしたオーディオ処理で動的確保0。
- Android APK・端末テストAPKのビルド、Lint。

Android実機・エミュレーターはこの開発環境に接続していません。内蔵音源の初回展開・永続化・削除後の復元を確認する端末テストはコンパイル済みですが、実行は未実施です。

端末では、初回にPocket Uprightが鳴ること、サステインと同時発音、SFZとフォルダーの選択、ZIPに含まれる親参照、アプリ再起動後の再選択、同じ音源での音声・動画の書き出しを確認してください。
