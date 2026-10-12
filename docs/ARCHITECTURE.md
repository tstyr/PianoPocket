# Architecture and realtime contract

`MainActivity → Compose / PocketViewModel → LibraryRepository / DspresetParser → JNI → AudioEngine → SamplerEngine → Effects → Oboe`

## Ownership

- Kotlinのファイル処理・XML解析・WAV準備はViewModelが所有する単一ワーカー上で実行。JNIのstart / stop / install / statsも同じワーカーで順序付ける。
- 音源はstaging transactionで構築。ロード失敗時はstagingを破棄し、演奏中の音源を変更しない。commit時にOboeを停止・closeしてコールバックを終了させ、bankを交換。古いbank、cache worker、DSP bufferの破棄は非オーディオスレッド。
- sample vector、zone lookup、WAV先頭、effect chainはcommit前に準備。コールバック中は変更しない。オーディオ経路再開後はプリセットを再準備し、OS sample rateに合わせる。
- Main / Android MIDIなど複数のイベント送信元は**送信側だけ**mutexで直列化し、固定長SPSCキューへpush。音声側はmutexを取らずpop。満杯時は欠落カウント＋panic flagでstuck notesを防ぐ。

## Streaming

- 波形先頭8192 framesを常駐させ、発音開始のディスク待ちを避ける。
- 予算に応じた256〜8192ページ（offlineは最大512）、各4096 frames＋補間用1 frame、Float stereo。callbackはページのreaderカウンターをCASで取得し、workerが上書きしない間だけ参照する。
- workerはreader=0を-1にCASしてページを確保。ディスク読み取り・decode後release publication。音声側はブロックやページ境界ごとに短い参照を取得し、待たない。
- オーディオ→cache workerの別SPSCキューで現在位置の先4096 / 8192 framesをprefetch。最終参照epochによるLRU。
- 4候補のハッシュディレクトリー、優先要求キューと重複抑制を使用。ミス時は直前のサンプル値からゲインを滑らかに変更して演奏を継続。速度の遅いストレージで完全無欠落を保証する仕組みではない。ミス・I/OエラーはUIに公開。
- キャッシュ予算はsample heads＋ページ＋ディレクトリーだけを対象にする。DSP、Convolutionのスペクトル、UI、旧bankとstagingの同時存在、decode用一時メモリは別。実機でPSSを測定する。

## Render

`bounded event drain → fixed voices → pitch / interpolation / envelope / per-channel & group gain → XML-order global effects → smooth master → linked stereo lookahead limiter`

事前確保の最大1024 voices、既定上限512。新しいMIDIノートに割り当てたレイヤーを同じノートの次のレイヤーでスティールしない。channelは外部MIDI 0..15、タッチ鍵盤は内部channel16、ファイル再生は内部channel17..32（ファイル内MIDI 0..15に対応）。キー状態はボイスとは別で保持し、WAVが自然終了した後もnote-offのrelease triggerを発音できる。同音の重複押下はchannel / noteにつき最大4個のFIFO状態で管理。

Audio callbackではnew/delete、vector resizing、XML、file I/O、JNI、UI更新、ログ、mutex、worker waitを実行しない。コールバック時間測定はsteady_clock、カウンターはatomic。ARM64のatomicはlock-freeな型を使用。

Convolutionは独自のradix-2 FFT。IRの最初2048 framesを256 frame partition / 512 point FFTで処理し、後期部分を2048 frame partition / 4096 point FFTで処理。後期出力を256 frames遅らせて早期成分に合わせ、全体の追加遅延は256 frames。ゼロ入力の履歴はFFT／積和を省略し、dry mix=0時も安定した負荷で処理する。IRスペクトル・履歴・overlap bufferは事前確保。最大IRでもCPU負荷が大きいため、設定のDSP予算率で評価する。ドライ経路は追加遅延を補償しないので、mix中の位相関係は純正と一致しない場合がある。

グローバルDSPは無発音時も実行してtailを保持。Reverb、Delay、Chorusは独自の近似実装。BiquadはRBJ型の式。パラメータとバイパスを15msでスムージングする。XML順序は保持するが、グループDSPと高度な制御は未対応で警告する。

voice-scope MIDI velocityのXML tableはKotlin parserで128値のカーブに準備し、native Instrumentのimmutable groupVelocityへ転送。Note On時のvoice gainだけに乗算し、他の音のvelocityやtagのマイク音量を変更しない。CC64トリガーはloNote / hiNoteから独立したlookupへ登録。IR menu変更は新DSPをworkerで準備してからstreamを短時間停止し、effectを交換して再開する。切替時に旧IRのtailは引き継がない。

## Sequencer and export

`SAF → MidiLibrary → MidiFileCodec / MidiSong → native immutable TimedEvent vector → sampler frame cursor → block splits → normal voice/effects render`

SMF parsing、tempo integration、NoteIndex構築、library persistence、recordingのfinalizationはワーカーで実行します。Android MIDI receiverは選択したポート・チャンネルをフィルターし、live note履歴とrecord bufferを短い同期区間で更新します。これらのロックをC++ audio callbackは使用しません。UIの30〜120fps上限付きvsync更新（非表示時の100ms更新）はnativeのatomic frame / playing / overloadとライブ履歴を読み取ります。録音にAndroidの単調増加timestampを使い、未指定・未来・逆順は現在時刻／最後の時刻へ補正。録音停止時は保留キー・ペダルを閉じます。

timelineの交換・シークはstreamをstop / closeしてからワーカーで行います。callbackではprepared timelineから発音イベントをdispatchし、その時刻までの区間だけrenderします。Liveイベントは次のblockで処理します。2048 live events＋2048 timeline eventsまでの有界処理。高密度timelineはfail-safe pause＋file channel All Sound Off、UIへ警告。シークは過去CC / bendをワーカー上で走査して復元し、過去ノートのサンプル中間位置再構築は行いません。ファイルのpause / seek / endは外部MIDI・画面鍵盤のチャンネルに影響しません。

WAVは別のInstrument / SamplerEngineを同じmapping・選択IR・現在のgroup gain / master / output / voice limitで準備します。offlineフラグのSampleCacheはworkerを起動せず、renderするバックグラウンドスレッド自身がページを同期decodeします。これはOboe経路に渡しません。従って書き出し速度による非同期cache missは発生せず、I/Oエラー時は失敗して一時WAVを消します。Float内部処理後、RIFF WAV PCM16 / PCM24 / Float32をストリーム書込みします。export状態、キャンセル、進行度はatomic。SAFへのcopyでもキャンセルを確認し、不完全な保存先は可能なら削除します。


## 0.3.1 reverb gain and output protection

The Effect preparation worker computes a single stereo-linked IR-energy gain, `min(1, .125 / max(channel L2))`, applied only to convolution wet output. Raw IR decode and mathematical Convolver are unchanged. This gain policy is an approximation; the official format does not publish the original algorithm, so import warns.

OutputLimiter uses fixed 513-frame stereo storage and a fixed monotonic-deque peak buffer, a ~1ms sample-peak lookahead, 0.1ms attack / 30ms hold / 80ms release. The hold avoids sub-cycle gain modulation of bass tones. Stereo uses the same gain. No heap allocation or lock occurs during process. Ceiling is .95, and quiet audio is identical after the delay when no recent overload is releasing. Atomic peak/reduction meters are read outside the callback. The stream's timestamp estimate excludes the added internal lookahead; Settings lists it separately. OfflineRenderer processes the corresponding extra frames and discards preroll, keeping the requested file length and MIDI alignment. [Diagnosis and validation](REVERB_FIX.md).

## Video export (0.6.0)

`offline sampler → PCM16 WAV → Android AAC/M4A → AAC packets` + `MIDI keyIndex → opaque Bitmap → worker EGL surface → MediaCodec AVC` → `MovieMuxer (isom MP4 / qt QuickTime MOV)` → SAF。音声と映像は同じゼロ起点のサンプル／フレーム時計を使い、B framesなし。500000 packets/track・2時間＋30秒上限。エンコード、描画、コンテナ書き込みはaudio callback外。キャンセル・失敗は中間ファイルを削除。[形式・制約・端末確認](VIDEO_EXPORT.md)。
