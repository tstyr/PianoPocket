#!/usr/bin/env python3
"""Generate an original scale/chord study, including tempo, sustain and running status.
No third-party musical work or performance is used. MIT, like PianoPocket.
"""
from pathlib import Path
import struct

def vlq(value):
    result = bytearray([value & 127])
    while value > 127:
        value >>= 7
        result.insert(0, 128 | (value & 127))
    return bytes(result)

def track(events, end):
    data = bytearray()
    previous = 0
    for tick, payload in sorted(events, key=lambda item: item[0]):
        data.extend(vlq(tick-previous) + payload)
        previous = tick
    data.extend(vlq(end-previous) + b'\xff\x2f\x00')
    return b'MTrk' + struct.pack('>I', len(data)) + data

def main():
    conductor = [(0,b'\xff\x03\x0cPocket Study'),(0,b'\xff\x51\x03\x07\xa1\x20'),(1920,b'\xff\x51\x03\x06\x1a\x80')]
    performance = [(0,bytes([0xb0,64,127])),(3600,bytes([0xb0,64,0]))]
    for index, key in enumerate([60,62,64,65,67,69,71,72,72,71,69,67,65,64,62,60]):
        performance.extend([(index*240,bytes([0x90,key,[33,77,110,127][index%4]])),(index*240+180,bytes([0x80,key,0]))])
    for key in [48,55,60]:
        performance.extend([(3840,bytes([0x90,key,90])),(4800,bytes([0x80,key,0]))])
    # Header ppqn=480; duration=5.2 sec, followed by a final rest through tick 5760.
    data=b'MThd'+struct.pack('>IHHH',6,1,2,480)+track(conductor,5760)+track(performance,5760)
    path=Path(__file__).resolve().parents[1]/'tests/fixtures/PocketStudy.mid'
    path.write_bytes(data)
    print(f'{path.name}: {len(data)} bytes, 19 original notes, two tempos')
if __name__ == '__main__':
    main()
