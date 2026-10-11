# Architecture and realtime contract

`MainActivity → Compose / PocketViewModel → LibraryRepository / DspresetParser → JNI → AudioEngine → SamplerEngine → Effects → Oboe`

## Ownership

- Kotlinのファイル処理・XML解析・WAV準備はViewModelが所有する単一ワーカー上で実行。JNIのstart / stop / install / statsも同じワーカーで順序付ける。
- 音源はstaging transactionで構築。ロード失敗時はstagingを破棄し、演奏中の音源を変更しない。commit時にOboeを停止・closeしてコールバックを終了させ、bankを交換。古いbank、cache worker、DSP bufferの破棄は非オーディオスレッド。
- sample vector、zone lookup、WAV先頭、effect chainはcommit前に準備。コールバック中は変更しない。オーディオ経路再開後はプリセットを再準備し、OS sample rateに合わせる。
- Main / Android MIDIなど複数のイベント送信元は**送信側だけ**mutexで直列化し、固定長SPSCキューへpush。音声側はmutexを取らずpop。満杯時は欠落カウント＋panic flagでstuck notesを防ぐ。

## Streaming

- 波形先頭8192 framesを常駐させ、発音開始のディスク待ちを避ける。
- 256固定ページ、各4096 frames＋補間用1 frame、Float stereo。callbackはページのreaderカウンターをCASで取得し、workerが上書きしない間だけ参照する。
- workerはreader=0を-1にCASしてページを確保。ディスク読み取り・decode後release publication。音声側はブロックやページ境界ごとに短い参照を取得し、待たない。
- オーディオ→cache workerの別SPSCキューで現在位置の先4096 / 8192 framesをprefetch。最終参照epochによるLRU。
- ミス時はその区間を無音にし、ゲインを滑らかに変更して演奏を継続。速度の遅いストレージで完全無欠落を保証する仕組みではない。ミス・I/OエラーはUIに公開。
- キャッシュ予算はsample heads＋ページだけを対象にする。DSP、Convolutionのスペクトル、UI、旧bankとstagingの同時存在、decode用一時メモリは別。実機でPSSを測定する。

## Render

`bounded event drain → fixed voices → pitch / interpolation / envelope / per-channel & group gain → XML-order global effects → smooth master → soft clipping protection`

固定128 voices。channelはMIDI 0..15、タッチ鍵盤は内部channel16。キー状態はボイスとは別で保持し、WAVが自然終了した後もnote-offのrelease triggerを発音できる。同音の重複押下はchannel / noteにつき最大4個のFIFO状態で管理。

Audio callbackではnew/delete、vector resizing、XML、file I/O、JNI、UI更新、ログ、mutex、worker waitを実行しない。コールバック時間測定はsteady_clock、カウンターはatomic。ARM64のatomicはlock-freeな型を使用。

Convolutionは独自のradix-2 FFT、256 frame uniform partitions、512 point FFT。IRスペクトル・履歴・overlap bufferは事前確保。最大IRでもCPU負荷が大きいため、設定のDSP予算率で評価する。ドライ経路は追加遅延を補償しないので、mix中の位相関係は純正と一致しない場合がある。

グローバルDSPは無発音時も実行してtailを保持。Reverb、Delay、Chorusは独自の近似実装。BiquadはRBJ型の式。パラメータとバイパスを15msでスムージングする。XML順序は保持するが、グループDSPと高度な制御は未対応で警告する。
