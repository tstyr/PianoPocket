#!/usr/bin/env python3
"""Prepare the CC0 Pocket Upright bank. Requires Python 3 and host ffmpeg."""
import argparse
import array
import concurrent.futures
import gzip
import hashlib
import json
import pathlib
import subprocess
import urllib.parse
import urllib.request

SOURCE = "https://github.com/sgossner/VSCO-2-CE"
REVISION = "440300901dfe9275fd84e0b7763af1f8443ae62e"
RAW = f"https://raw.githubusercontent.com/sgossner/VSCO-2-CE/{REVISION}/"
INDICES = list(range(0, 45, 4))
ROOTS = [21 + 2 * index if index != 44 else 108 for index in INDICES]
LAYERS = [(1, 0, 60, 23), (2, 61, 110, 10), (3, 111, 127, 2)]


def ffmpeg(*args):
    return subprocess.run(["ffmpeg", "-v", "error", "-nostdin", *args], check=True, capture_output=True).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("app/src/main/assets/instruments/PocketUpright"))
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    (args.output / "Samples").mkdir(parents=True, exist_ok=True)
    names = [f"Player_dyn{layer}_rr1_{index:03}.wav" for layer, _, _, _ in LAYERS for index in INDICES]

    def download(name):
        target = args.cache / name
        if not target.exists():
            url = RAW + urllib.parse.quote("Keys/Upright Piano/" + name, safe="/")
            with urllib.request.urlopen(url, timeout=90) as response:
                data = response.read(16 * 1024 * 1024)
            assert data[:4] == b"RIFF", name
            target.write_bytes(data)
        return target

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        sources = list(pool.map(download, names))
    print(f"Downloaded {len(sources)} pinned-source samples", flush=True)

    # Preserve the relative gains in the upstream mapping, with one shared headroom adjustment.
    peaks = []
    for source in sources:
        values = array.array("f", ffmpeg("-i", str(source), "-t", "12", "-f", "f32le", "-"))
        layer = int(source.name[10])
        gain_db = LAYERS[layer - 1][3]
        peaks.append(max(abs(value) for value in values) * 10 ** (gain_db / 20))
    adjustment = .70 / max(peaks)
    manifest = {"source": SOURCE, "revision": REVISION, "license": "CC0-1.0", "sampleRate": 44100,
                "channels": 2, "bits": 16, "maxDurationSeconds": 12, "sharedGain": adjustment, "samples": []}
    sfz = ["// Pocket Upright — VSCO 2 CE / Simon Dalzell, Ivy Audio / Versilian Studios", "// CC0-1.0; see LICENSE.txt and NOTICE.txt",
           "<control> default_path=Samples/", "<global> ampeg_attack=0.003 ampeg_release=0.6 amp_veltrack=100 loop_mode=no_loop"]
    total = 0
    for layer, low_velocity, high_velocity, gain_db in LAYERS:
        sfz += [f"<group> group_label={'Soft' if layer == 1 else 'Medium' if layer == 2 else 'Hard'} lovel={low_velocity} hivel={high_velocity}"]
        for position, (index, root) in enumerate(zip(INDICES, ROOTS)):
            source = args.cache / f"Player_dyn{layer}_rr1_{index:03}.wav"
            duration = min(12, float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(source)])))
            gain = 10 ** (gain_db / 20) * adjustment
            wav = ffmpeg("-i", str(source), "-map_metadata", "-1", "-t", str(duration), "-af",
                         f"volume={gain:.12g},afade=t=out:st={max(0, duration-.15):.9f}:d=0.15",
                         "-ar", "44100", "-ac", "2", "-c:a", "pcm_s16le", "-fflags", "+bitexact", "-f", "wav", "-")
            # Pipe-written RIFF lengths are unspecified; write the true sizes for strict WAV readers.
            data_start = wav.index(b"data")
            wav = wav[:4] + (len(wav)-8).to_bytes(4, "little") + wav[8:data_start+4] + (len(wav)-data_start-8).to_bytes(4,"little") + wav[data_start+8:]
            name = f"P{layer}_{root:03}.wav"
            # .gz is automatically inflated by aapt2. A neutral suffix preserves packing in the APK.
            (args.output / "Samples" / (name + ".pack")).write_bytes(gzip.compress(wav, compresslevel=9, mtime=0))
            total += len(wav)
            low = 21 if position == 0 else (ROOTS[position - 1] + root) // 2 + 1
            high = 108 if position == len(ROOTS)-1 else (root + ROOTS[position + 1]) // 2
            sfz += [f"<region> sample={name} lokey={low} hikey={high} pitch_keycenter={root}"]
            manifest["samples"].append({"file": "Samples/"+name, "sourceFile": source.name,
                "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(), "sha256": hashlib.sha256(wav).hexdigest(), "bytes": len(wav), "duration": duration})
    (args.output / "Pocket Upright.sfz").write_text("\n".join(sfz)+"\n")
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2)+"\n")
    (args.output / "bundled-id.txt").write_text("pocket-upright-v1\n")
    with urllib.request.urlopen(RAW + "LICENSE", timeout=60) as response:
        (args.output / "LICENSE.txt").write_bytes(response.read())
    (args.output / "NOTICE.txt").write_text(
        "Pocket Upright\n\nSamples: VSCO 2 Community Edition, CC0 1.0 Universal.\n"
        "Upright Piano recorded by Simon Dalzell / Ivy Audio, winter 2015–16.\n"
        "Distributed by Versilian Studios / Sam Gossner.\n"
        "https://vis.versilstudios.net/vsco-community.html\n" + SOURCE + "\nRevision: " + REVISION + "\n\n"
        "Upstream mapping: SFZ branch, revision 6dd651d55dde97fd4028699be9d4481f26917891.\n\n"
        "PianoPocket adaptation: 12 sampled keys across A0–C8, three velocity layers,\n"
        "44.1 kHz stereo PCM16. Original layer gains (23 / 10 / 2 dB) followed by\n"
        "one shared headroom adjustment, tails limited to 12 seconds and faded over\n"
        "the final 150 ms. Original round-robin variants are not included.\n"
        "WAVs are losslessly gzip-packed in the APK and unpacked on installation.\n"
        "SFZ mapping and preparation script: MIT, see project LICENSE.\n"
        "Source and converted sample SHA-256 hashes are in manifest.json.\n"
        "No Helsinki / Pianobook samples are included.\n")
    print(f"Bank: {total/1048576:.1f} MiB WAV; packed: {sum(f.stat().st_size for f in args.output.rglob('*') if f.is_file())/1048576:.1f} MiB", flush=True)


if __name__ == "__main__":
    main()
