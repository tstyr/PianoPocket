# Reverb clipping fix — 0.3.1 alpha

## Report and cause

The user reported severe distortion when raising Helsinki1967's Reverb Mix / IR Mix, with School Hall selected and Mix about 0.71. Lowering Master reduced the problem but also made the piano quiet and left some distortion. The supplied Android screen showed 48 kHz, 96 frames/burst, 8 bursts, XRun 0, DSP 13.5%, sample resident 29.1 MiB, PSS 177.8 MiB and no read errors. These are user observations; no Android device is attached to the development environment.

The native stress test reproduced clipping using the owned Helsinki v1.6 normal preset, SchoolHall.wav, velocity 127, Master 1, Output 0.7 and the preset's convolution → +12 dB Gain chain. Mix=0.71 produced 16,098 near-full-scale samples even for one B4 note, and 19,853 for a four-note chord, over six seconds of stereo output (576,000 samples). The old output protector bounded values with a memoryless tanh knee. This prevented numeric overflow, but flattened loud waveforms and generated harmonics; finite/bounded output tests did not detect this audible failure.

The IR loader was feeding raw recorded IR levels into convolution, although the five provided IRs have very different energy and frequency gains. School Hall's large wet-path gain and the subsequent +12 dB stage repeatedly drove the waveshaper into saturation. The official effect reference specifies mix/irFile and mix=1 as wet-only; it does not publish the original normalization algorithm. [Official source checked](https://github.com/DecentSamples/DecentSampler-Developers-Guide/blob/1a1f8535003a04fbbb15f0a01d7b232ccab67987/docs/source/the-effects-element.md#convolution-effect).

## Changes

- Compute one linked-stereo IR gain on the preparation worker: `min(1, 0.125 / max(channel L2 norms))`. Apply it only to the convolved wet signal before the documented linear dry/wet blend. Quiet IRs are never boosted, stereo balance and the entire IR length remain intact. The raw Convolver still reproduces its IR exactly; this gain belongs to the reverb Effect. This is PianoPocket's documented approximation, not a claim of Decent Sampler's unpublished algorithm. Import displays a compatibility warning.
- Replace tanh wave shaping with a linked stereo sample-peak limiter, ceiling 0.95, approximately 1ms lookahead, 0.1ms gain attack, 30ms gain hold and 80ms release. The hold keeps the gain from following the zero crossings of low piano notes. Fixed buffers and a monotonic peak deque are used; no callback allocations, locks, file I/O or UI calls. The final ceiling clamp covers rounding/extreme transients. This is sample-peak protection, not a true-peak guarantee or a guarantee of transparent sound under arbitrarily large overload.
- Add pre-limiter peak, gain-reduction dB and lookahead time to Settings. Oboe's timestamp latency remains the output-path estimate; the limiter adds approximately 1ms internally. Convolution's 256-frame wet-path latency is unchanged. WAV export discards the lookahead preroll and renders the corresponding extra frames, preserving MIDI onset position and the requested file length.
- Where an XML control already binds an effect parameter, display that control once. Helsinki's Reverb Mix controls IR Mix; the effect section now says to use Reverb Mix rather than offering a duplicate slider with potentially stale UI values.

## Verification

- Real IRs: Lady Chapel, Usina, School Hall, Small Room, Plate × Mix 0 / 0.3 / 0.71 / 1 × solo / four-note chord / twelve-note chord: **60 cases pass**, six seconds each. Velocity 127, Master 1, Output 0.7, max 128 sample voices. All output finite and at/below 0.95; cache misses and read errors 0.
- School Hall, Mix 0.71: solo peak 0.503916 with no limiting; four-note chord pre-limiter peak 1.27036, about 2.52 dB maximum reduction; twelve-note chord pre-limiter peak 2.09544, about 6.87 dB reduction. No samples above 0.98. Reduced near-full-scale counts alone are not evidence of low distortion; the sine tests below check harmonic generation separately.
- Steady overload tests use amplitude-4 sine waves at 55Hz and 1kHz, not instrument audio. The third-harmonic/fundamental ratio is below 0.1% for the new limiter at both frequencies; the previous waveshaper's 1kHz ratio is about 30.7%. See the exact host measurements in the [JSON report](data/reverb-clipping-summary.json). Sub-ceiling signals remain sample-identical after the fixed delay; channel balance is preserved; first extreme transients are bounded and gain recovers after overload.
- Native regression and ASan/UBSan pass, including IR energy attenuation, no quiet-IR boost, zero-wet dry transparency, sample streaming, playback, six WAV format/rate combinations and export cancellation. Render/effect heap allocation count remains zero.
- Helsinki XF + real IR: 10-second PCM24/48kHz stereo WAV export passes with nonzero release/reverb tail, no cache misses or read errors. Rendered audio remains private.
- `assembleDebug testDebugUnitTest lintDebug`: PASS, 36 JVM tests. APK signature and 16KiB package/native alignment checked. No Helsinki audio/IR/XML or user MIDI is included.

## Retest on the user's phone

Install 0.3.1 over 0.3, select the existing Helsinki1967 preset and School Hall. Use the usual Master setting (host tests use 1) and Output (host tests use 0.7), then compare Mix 0, 0.3, 0.71 and 1 with single notes and chords. Check Settings' gain-reduction meter alongside XRun/read errors. If reduction stays large during heavy chords, reducing Output gives the limiter more headroom. Development-host tests do not verify the actual phone's speakers, headphones, output effects or timing; final listening confirmation on the user's device is still needed.

To repeat with legally owned files, use the production parser exporter described in HELSINKI.md, then run:

```sh
cmake --build build/native -j 2
build/native/owned_preset_probe '<Helsinki1967.dspreset.mapping>' --reverb-stress '<IR/SchoolHall.wav>' 0.71 chord
```

The mapping contains private sample paths and must not be published. Only metrics are distributed. No new external dependency or commercial license was introduced.
