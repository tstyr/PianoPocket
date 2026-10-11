#!/usr/bin/env python3
"""Isolated optional Wine/Box64 runner. Never imported by Android application."""
import argparse,json,os,signal,subprocess,time,shutil
from pathlib import Path
def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=Path('vst-probe.json'));p.add_argument('--seconds',type=int,default=30);p.add_argument('command',nargs=argparse.REMAINDER);a=p.parse_args()
    cmd=a.command[1:] if a.command[:1]==['--'] else a.command
    report={'available':{n:shutil.which(n) for n in ('box64','wine','wine64')},'command':cmd,'measurements':[],'audio_latency_ms':None,'midi_verified':False,'audio_verified':False}
    if cmd:
        process=subprocess.Popen(cmd,start_new_session=True);report['pid']=process.pid;started=time.monotonic()
        try:
            while process.poll() is None and time.monotonic()-started<a.seconds:
                # ps captures child processes too. RSS/CPU are process metrics, not loopback latency.
                report['measurements'].append({'seconds':round(time.monotonic()-started,2),'ps':subprocess.check_output(['ps','-eo','pid,ppid,pcpu,rss,comm'],text=True)})
                time.sleep(1)
        finally:
            if process.poll() is None:
                os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=5)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
            report['exit_code']=process.returncode
    else:report['status']='capability inspection only; host was not executed'
    a.output.write_text(json.dumps(report,indent=2)+'\n');print(a.output)
if __name__=='__main__':main()
