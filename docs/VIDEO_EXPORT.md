# 動画書き出し — 0.6.0

**︙ → MIDI / 音声 / 動画を書き出し**でMP4／MOVを選びます。選択したMIDI、または録音してライブラリへ保存した演奏を、現在の音源・マイク音量・エフェクト・音量・発音上限でオフライン演奏し、上から降るロールと鍵盤の動画を保存します。

- 横16:9、720p（1280×720）／1080p（1920×1080）、30／60fps。端末のSurface H.264 Baselineエンコーダーのサイズ・レート・ビットレート対応状況を確認し、非対応の設定は選べません。表示画面用の120fps設定とは独立しています。
- H.264ビットレートは720p30で4Mbps／1080p30で8Mbps、60fpsはその2倍。音声はAAC-LC、128／192／256／320kbps、44.1／48kHzステレオ。OSのエンコーダーが設定を拒否した場合、エラーを表示します。
- ドレミのオン／オフ、横ズーム・表示音域、ロールの表示時間を反映します。不透明のトラック色、白の太字・暗い縁取りのドレミを使用。押鍵→離鍵のロールにし、サステイン音声は維持し、ペダル中は黄色のSUSを表示します。
- 音声の残響追加0〜30秒（既定8秒）を映像の末尾にも含めます。MIDIと音声は同じゼロ時刻から生成し、映像のタイムスタンプをフレーム時計から設定。AACのpriming／paddingによる末尾差は最大数パケットあります。
- 実際のMP4は`isom`、MOVはQuickTimeの`qt  ` brandとmovie/sample-table構造を使います。拡張子の変更だけではありません。AVCDecoderConfigurationRecordとlength-prefixed NALを格納し、B framesを無効にします。
- 動画はロールと鍵盤を再描画します。操作バー・FPS・通知は含めず、画面録画権限も使いません。録音中は書き出せず、録音を停止・保存してから書き出します。ライブ動画録画・トラック編集・カメラ合成は未対応です。

すべてオフライン。描画・EGL・MediaCodec・コンテナ書き込みは専用ワーカーで実行し、Oboeコールバックで実行しません。動画は非可逆圧縮です。音声だけを可逆で保存したい場合はWAV／FLACを使用してください。

## リソースとキャンセル

最長2時間のMIDI＋30秒の残響、各トラック最大50万パケット。映像フレームを全保存せず、Bitmap・GL textureを再利用します。パケットのサイズ・時刻・64bitファイルオフセットは有界のサンプル表に保持。mdatは64bitサイズ、chunk offsetはco64にし、moovを末尾に書きます。ネット配信向けのfast start最適化はしていません。

PCM16 WAV、AACの一時M4A、完成前の動画が端末のcacheへ書かれ、PCM＋動画の見積もり＋AAC＋128MiBの空き容量を確認します。長い1080p60動画は数GB以上になる場合があります。エンコーダーの出力サイズで実際の必要容量は変わります。メモリ・ディスク不足、コーデック停止、タイムスタンプ不整合、フレーム欠落時は成功と扱わずエラーにします。進行状況とキャンセル、成功／失敗時の一時ファイル削除、部分SAFファイルの削除試行に対応。providerが削除を拒否した場合は途中の保存先ファイルが残る可能性があります。

## 検証

- 51 JVM tests PASS。追加3 testsはAnnex B／AVCCパケット、SPS/PPS、正しいブランド・mdat・moov・2トラックのサンプル表・64bitオフセット、不正／逆順時刻・欠落トラック・設定上限を検査します。
- 独立したFFmpeg生成の自作テスト映像・440Hz音声を使用。30fps／48kHz（90映像フレーム）、60fps／44.1kHz（180フレーム）のMP4・MOV計4本をffprobeで検査し、全映像フレームのハッシュとデコードしたFloat32音声全バイトが元のH.264／AACと一致。正しいブランド、H.264、AACステレオ、寸法、FPS、時間を確認。Helsinkiの音声を動画fixtureに使用・配布していません。
- `VideoExporterDeviceTest` は実Android Surface H.264＋AAC生成→MP4／MOV抽出→映像全フレーム復号、ロール描画、キャンセル時削除を検査します。Android test APKはビルド済みですが、**実機／エミュレーター未接続で実行未実施**です。端末で動画書き出しが成功したとは報告しません。GPU上下方向、ドレミ描画、長時間のA/V同期、Apple QuickTime／Android各プレイヤーは実機確認が必要です。

ホスト検証にはFFmpeg（libx264とAAC encoder）・ffprobe・JDK17・通常のAndroidビルド環境が必要です。FFmpegは検証用の外部ツールで、APKにリンク・同梱しません。

```sh
python3 tests/video/verify_movie.py prepare build/video/probe30 --fps 30 --rate 48000
./gradlew verifyMovieMuxer -PpacketFixture="$PWD/build/video/probe30.packets" -PmovieOutput="$PWD/build/video/probe30"
python3 tests/video/verify_movie.py verify build/video/probe30 --fps 30 --rate 48000
python3 tests/video/verify_movie.py prepare build/video/probe60 --fps 60 --rate 44100
./gradlew verifyMovieMuxer -PpacketFixture="$PWD/build/video/probe60.packets" -PmovieOutput="$PWD/build/video/probe60"
python3 tests/video/verify_movie.py verify build/video/probe60 --fps 60 --rate 44100
```

端末接続後に `./gradlew connectedDebugAndroidTest` を実行。自作Pocket Tone＋PocketStudy MIDIで720p30→60、1080p30→60、MP4／MOV、ドレミon/off、拡大表示、SUS、SAF保存・再生・残響・キャンセルを確認します。Helsinkiは利用者所有の実ファイルを読み込み、USB MIDI録音後の動画と音声同期を確認してください。

AndroidのMediaCodec／EGL／GLES APIのみを使い、動画muxer・描画は自作MITです。外部VST・FFmpeg・商用専用ライセンスの依存を追加していません。
