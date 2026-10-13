#!/usr/bin/env python3
"""Generate the optional CC0 Pocket Upright HQ package; ffmpeg is a host tool."""
import argparse,tempfile
import pathlib,subprocess,urllib.request,urllib.parse,concurrent.futures,json,hashlib,array,zipfile,shutil
REV='440300901dfe9275fd84e0b7763af1f8443ae62e'
RAW=f'https://raw.githubusercontent.com/sgossner/VSCO-2-CE/{REV}/'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cache',type=pathlib.Path,required=True)
parser.add_argument('--output',type=pathlib.Path,required=True)
parser.add_argument('--distribution',type=pathlib.Path,required=True)
args=parser.parse_args()
cache=args.cache;cache.mkdir(parents=True,exist_ok=True)
output=args.output;(output/'Samples').mkdir(parents=True,exist_ok=True)
indices=list(range(0,45,2));roots=[21+2*i if i!=44 else 108 for i in indices]
layers=[(1,0,60,23),(2,61,110,10),(3,111,127,2)]
names=[f'Player_dyn{layer}_rr1_{i:03}.wav' for layer,*_ in layers for i in indices]
def download(name):
 p=cache/name
 if not p.exists():
  with urllib.request.urlopen(RAW+urllib.parse.quote('Keys/Upright Piano/'+name,safe='/'),timeout=90) as r:p.write_bytes(r.read(16*1024*1024))
 assert p.read_bytes()[:4]==b'RIFF';return p
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:sources=list(pool.map(download,names))
print('HQ source samples:',len(sources),flush=True)
peaks=[]
for p in sources:
 values=array.array('f',subprocess.check_output(['ffmpeg','-v','error','-i',str(p),'-f','f32le','-']))
 peaks.append(max(abs(v) for v in values)*10**(layers[int(p.name[10])-1][3]/20))
adjust=.70/max(peaks)
manifest={'id':'pocket-upright-hq-v1','name':'Pocket Upright HQ','revision':REV,'license':'CC0-1.0','sampleRate':44100,'channels':2,'bits':24,'sampledKeys':23,'velocityLayers':3,'sharedGain':adjust,'samples':[]}
sfz=['// Pocket Upright HQ — VSCO 2 CE / Simon Dalzell, Ivy Audio / Versilian Studios','// CC0-1.0; see LICENSE.txt and NOTICE.txt','<control> default_path=Samples/','<global> ampeg_attack=0.003 ampeg_release=0.6 amp_veltrack=100 loop_mode=no_loop']
for layer,lovel,hivel,db in layers:
 sfz += [f'<group> group_label={"Soft" if layer==1 else "Medium" if layer==2 else "Hard"} lovel={lovel} hivel={hivel}']
 for pos,(i,root) in enumerate(zip(indices,roots)):
  source=cache/f'Player_dyn{layer}_rr1_{i:03}.wav';name=f'HQ{layer}_{root:03}.flac';target=output/'Samples'/name
  subprocess.run(['ffmpeg','-v','error','-y','-i',str(source),'-map_metadata','-1','-af',f'volume={10**(db/20)*adjust:.12g}','-sample_fmt','s32','-c:a','flac','-compression_level','8',str(target)],check=True)
  meta=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','stream=sample_rate,channels,bits_per_raw_sample,duration','-of','json',str(target)]))['streams'][0]
  assert int(meta['channels'])==2 and int(meta['sample_rate'])==44100 and int(meta['bits_per_raw_sample'])==24
  low=21 if pos==0 else (roots[pos-1]+root)//2+1;high=108 if pos==len(roots)-1 else (root+roots[pos+1])//2
  sfz += [f'<region> sample={name} lokey={low} hikey={high} pitch_keycenter={root}']
  manifest['samples'].append({'file':'Samples/'+name,'sourceFile':source.name,'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'bytes':target.stat().st_size,'duration':float(meta['duration'])})
(output/'Pocket Upright HQ.sfz').write_text('\n'.join(sfz)+'\n')
(output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
(output/'download-id.txt').write_text('pocket-upright-hq-v1\n')
shutil.copy2(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/instruments/PocketUpright/LICENSE.txt',output/'LICENSE.txt')
(output/'NOTICE.txt').write_text('Pocket Upright HQ\n\nVSCO 2 Community Edition / Upright Piano\nRecorded by Simon Dalzell / Ivy Audio, winter 2015–16.\nDistributed by Versilian Studios / Sam Gossner.\nCC0 1.0 Universal, full license in LICENSE.txt.\nhttps://vis.versilstudios.net/vsco-community.html\nhttps://github.com/sgossner/VSCO-2-CE\nRevision: '+REV+'\n\nPianoPocket adaptation: all 23 sampled keys and three velocity layers,\n69 stereo 44.1 kHz PCM24 FLAC samples, full original tails.\nOriginal layer gains 23 / 10 / 2 dB plus a shared headroom adjustment.\nNo round robin / dedicated release samples. SFZ mapping and script: MIT.\nNo Pianobook recordings are included.\n')
archive=args.output.parent/'PocketUprightHQ-1.0.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_STORED) as z:
 for p in sorted(output.rglob('*')):
  if p.is_file():z.write(p,p.relative_to(output).as_posix())
parts=[];folder=args.distribution;folder.mkdir(exist_ok=True)
with archive.open('rb') as stream:
 while data:=stream.read(40*1024*1024):
  name=f'PocketUprightHQ-1.0.zip.part{len(parts)+1:02}';(folder/name).write_bytes(data);parts.append({'name':name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
catalog={'id':'pocket-upright-hq-v1','name':'Pocket Upright HQ','description':'23収録キー・3段階ベロシティ・24bitステレオ・元の残響を全収録','license':'CC0-1.0','source':'https://github.com/sgossner/VSCO-2-CE','revision':REV,'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'expandedBytes':sum(p.stat().st_size for p in output.rglob('*') if p.is_file()),'parts':parts}
(folder/'PocketUprightHQ-1.0.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2))
print(json.dumps({'sizeMiB':round(archive.stat().st_size/1048576,1),'parts':len(parts),'samples':69,'maxTail':max(s['duration'] for s in manifest['samples'])}),flush=True)
