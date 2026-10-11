#!/usr/bin/env python3
"""Generate original, redistributable synthetic WAV fixtures. No Pianobook content."""
import math,struct,wave,zipfile,argparse
from pathlib import Path
def generate(root):
    root.mkdir(parents=True,exist_ok=True)
    for name,amp,frequency in [('soft',.12,261.625565),('medium',.22,261.625565),('hard',.3,261.625565),('forte',.38,261.625565),('release',.05,784),('pedal',.03,95)]:
        with wave.open(str(root/(name+'.wav')),'wb') as w:
            w.setparams((2,2,48000,0,'NONE','not compressed'))
            frames=96000 if name not in ('release','pedal') else 12000
            w.writeframes(b''.join(struct.pack('<hh',int(amp*32767*math.sin(2*math.pi*frequency*i/48000)*math.exp(-i/24000)),int(amp*.8*32767*math.sin(2*math.pi*frequency*i/48000)*math.exp(-i/24000))) for i in range(frames)))
    zones=''.join(f'<sample path="{name}.wav" rootNote="60" loNote="24" hiNote="108" loVel="{lo}" hiVel="{hi}" />' for name,lo,hi in [('soft',1,31),('medium',32,63),('hard',64,95),('forte',96,127)])
    xml=f'''<DecentSampler name="PianoPocket original test fixture"><groups><group name="Synthetic Stereo" release="0.15">{zones}</group><group name="Release" trigger="release"><sample path="release.wav" rootNote="60" /></group><group name="Pedal" pitchKeyTrack="0" onLoCC64="64" onHiCC64="127"><sample path="pedal.wav" rootNote="60" /></group></groups><effects><effect type="reverb" roomSize="0.7" damping="0.3" wetLevel="0.25" /></effects></DecentSampler>'''
    (root/'PocketFixture.dspreset').write_text(xml)
    archive=root.parent/'PocketFixture.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(root.iterdir()):z.write(p,p.name)
    return archive
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args();print(generate(a.output))
