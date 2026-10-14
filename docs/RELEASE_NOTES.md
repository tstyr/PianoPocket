# PianoPocket 0.25.0 — スマホで使えるMIDI編集

MIDIライブラリの「⋮ → 編集」または演奏画面の「その他 → MIDIを編集」から、音符を移動・削除し、長さとベロシティを調整できます。縦横両対応、2本指の拡大・縮小、トラック絞り込み、グリッドへの吸着、64操作の取り消し・やり直しに対応しています。

編集結果はコピーとして保存し、元のMIDIとペダル・テンポ・その他のイベントを保持します。同音程・同チャンネルを複数トラックで使ったときのロールのノート終了も修正しました。

## ダウンロード

- [APKをダウンロード（Android ARM64）](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.25.0-arm64.apk)
- [ソースコード ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.25.0-source.zip)
- [テスト結果 ZIP](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/PianoPocket-0.25.0-validation.zip)
- [SHA-256](https://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/SHA256SUMS-0.25.0.txt)

Android 10以降・ARM64用。デバッグ署名の試用版です。

141件のJVMテスト、Androidビルド、Lintを確認済み。実機でのタッチ操作は未検証です。音符の新規追加・一括編集・編集画面での試聴・途中の自動保存は未対応です。[MIDI編集の使い方](https://github.com/tstyr/PianoPocket/blob/v0.25.0-alpha/docs/MIDI_EDITOR.md)。
