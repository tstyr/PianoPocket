#!/usr/bin/env python3
"""Inspect user-owned presets without copying samples or assuming Helsinki structure."""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

KNOWN = {'path','name','rootNote','loNote','hiNote','loVel','hiVel','volume','pan','globalVolume','groupVolume','globalPan','groupPan','tuning','groupTuning','globalTuning','attack','decay','sustain','release','ampVelTrack','pitchKeyTrack','trigger','start','end','loopStart','loopEnd','loopEnabled','loopDuringRelease','enabled','onLoCC64','onHiCC64','loCC64','hiCC64'}
def inspect(path):
    raw=path.read_bytes()
    if len(raw)>4*1024*1024 or b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():
        raise ValueError('Oversize XML or forbidden DTD/entity')
    root=ET.fromstring(raw)
    if root.tag!='DecentSampler': raise ValueError('Not a Decent Sampler preset')
    groups=[];missing=[];unsupported=[]
    for i,g in enumerate(root.findall('./groups/group')):
        samples=g.findall('sample')
        ranges=sorted({(s.get('loVel',g.get('loVel','0')),s.get('hiVel',g.get('hiVel','127'))) for s in samples})
        for s in samples:
            ref=s.get('path','').replace('\\','/')
            if not ref or ref.startswith('/') or ':' in ref or '..' in ref.split('/'):
                missing.append({'group':i,'path':ref,'error':'unsafe reference'});continue
            target=(path.parent/ref).resolve()
            if not target.is_relative_to(path.parent.resolve()) or not target.is_file():missing.append({'group':i,'path':ref,'error':'missing file'})
        groups.append({'index':i,'attributes':g.attrib,'samples':len(samples),'velocity_ranges':ranges,'sample_attributes':sorted({k for s in samples for k in s.attrib}), 'effects':[e.attrib for e in g.findall('./effects/effect')]})
    for node in root.iter():
        if node.tag in ('groups','group','sample'):
            for k,v in node.attrib.items():
                if k not in KNOWN:unsupported.append({'element':node.tag,'attribute':k,'value':v})
    effects=[e.attrib for e in root.findall('./effects/effect')]
    for e in effects:
        if e.get('irFile') and not (path.parent/e['irFile'].replace('\\','/')).is_file():missing.append({'irFile':e['irFile'],'error':'missing IR'})
    return {'preset':path.name,'sha256':hashlib.sha256(raw).hexdigest(),'root_attributes':root.attrib,'groups':groups,'global_effect_chain':effects,'controls':[{'element':n.tag,'attributes':n.attrib,'bindings':[b.attrib for b in n.findall('binding')]} for n in root.iter() if n.tag in ('control','labeled-knob','menu','button')],'midi':[ET.tostring(n,encoding='unicode') for n in root.findall('midi')],'modulators':[ET.tostring(n,encoding='unicode') for n in root.findall('modulators')],'missing_references':missing,'unsupported_mapping_attributes':unsupported,'warning':'Structural audit only. This is not an audio compatibility or latency test.'}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('instrument',type=Path);parser.add_argument('--output',type=Path,required=True);a=parser.parse_args()
    files=sorted(a.instrument.rglob('*.dspreset')) if a.instrument.is_dir() else [a.instrument]
    if not files:parser.error('No .dspreset files found')
    report={'inspected_files':len(files),'presets':[inspect(p) for p in files]}
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(f'Wrote {a.output}: {len(files)} preset(s)')
if __name__=='__main__':main()
