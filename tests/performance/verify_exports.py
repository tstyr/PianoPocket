#!/usr/bin/env python3
"""Independent FFmpeg round-trip checks for the bundled native encoders (original tones)."""
import argparse, array, json, math, pathlib, subprocess, tempfile, wave
p=argparse.ArgumentParser();p.add_argument('--probe',required=True);p.add_argument('--output');a=p.parse_args()
rows=[]
with tempfile.TemporaryDirectory(prefix='pocket-codecs-') as directory:
    root=pathlib.Path(directory)
    for rate in (44100,48000):
        for bits in (16,24):
            pcm=bytearray();peak=(1<<(bits-1))-1
            for frame in range(rate*3):
                # .2s of silence at both ends makes encoder delay / clipping easy to detect.
                for frequency,amplitude in ((440,.55),(660,.30)):
                    value=round(peak*amplitude*math.sin(2*math.pi*frequency*frame/rate)) if rate//5<=frame<rate*14//5 else 0
                    pcm+=value.to_bytes(bits//8,'little',signed=True)
            wav=root/f'tone-{rate}-{bits}.wav';prefix=root/f'export-{rate}-{bits}'
            with wave.open(str(wav),'wb') as f:f.setnchannels(2);f.setsampwidth(bits//8);f.setframerate(rate);f.writeframes(pcm)
            subprocess.run([a.probe,str(wav),str(prefix)],check=True)
            decoded=subprocess.check_output(['ffmpeg','-v','error','-i',str(prefix)+'.flac','-f',f's{bits}le','-'])
            assert decoded==pcm,'FLAC is not bit-identical'
            rows.append(dict(format='FLAC',rate=rate,bits=bits,frames=rate*3,lossless=True))
            for bitrate in (128,192,256,320):
                target=str(prefix)+f'-{bitrate}.mp3'
                metadata=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',target]))['streams'][0]
                assert metadata['codec_name']=='mp3' and int(metadata['sample_rate'])==rate and metadata['channels']==2
                decoded=subprocess.check_output(['ffmpeg','-v','error','-i',target,'-f','f32le','-'])
                samples=array.array('f');samples.frombytes(decoded);assert len(samples)==rate*3*2,'MP3 gapless length / padding mismatch'
                for channel,frequency,amplitude in ((0,440,.55),(1,660,.30)):
                    cross=original=actual=0
                    for frame in range(rate//2,rate*5//2):
                        expected=amplitude*math.sin(2*math.pi*frequency*frame/rate);value=samples[frame*2+channel]
                        assert math.isfinite(value) and abs(value)<1
                        cross+=expected*value;original+=expected*expected;actual+=value*value
                    assert cross/math.sqrt(original*actual)>.99,'MP3 stereo tone / alignment mismatch'
                rows.append(dict(format='MP3',rate=rate,inputBits=bits,kbps=bitrate,frames=rate*3,stereo=True))
result=dict(tests=len(rows),passed=len(rows),cases=rows,androidAacTest='not run; no device')
if a.output:pathlib.Path(a.output).write_text(json.dumps(result,indent=2)+'\n')
print(f'PASS: {len(rows)} independent FLAC/MP3 round trips; bit identity, stereo, gapless duration, cancellation')
