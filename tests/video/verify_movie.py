#!/usr/bin/env python3
"""Original synthetic fixture and independent FFmpeg/ffprobe validation; not an APK dependency."""
import argparse, hashlib, json, pathlib, struct, subprocess


def run(args):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout


def prepare(prefix, fps, rate):
    prefix.parent.mkdir(parents=True, exist_ok=True)
    h264 = pathlib.Path(str(prefix) + '.h264')
    aac = pathlib.Path(str(prefix) + '.aac')
    run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i', f'testsrc2=size=320x180:rate={fps}',
         '-t', '3', '-c:v', 'libx264', '-profile:v', 'baseline', '-pix_fmt', 'yuv420p', '-bf', '0',
         '-g', str(fps * 2), '-f', 'h264', str(h264)])
    run(['ffmpeg', '-v', 'error', '-y', '-f', 'lavfi', '-i', f'sine=frequency=440:sample_rate={rate}',
         '-t', '3', '-ac', '2', '-c:a', 'aac', '-b:a', '192k', '-f', 'adts', str(aac)])
    data = h264.read_bytes()
    packets = json.loads(run(['ffprobe', '-v', 'error', '-show_packets', '-show_entries', 'packet=pos,size',
                              '-of', 'json', str(h264)]))['packets']
    video = [data[int(p['pos']):int(p['pos']) + int(p['size'])] for p in packets]
    data = aac.read_bytes(); audio = []; at = 0
    while at < len(data):
        assert data[at] == 255 and data[at + 1] & 0xf6 == 0xf0
        header = 7 if data[at + 1] & 1 else 9
        size = ((data[at + 3] & 3) << 11) | (data[at + 4] << 3) | (data[at + 5] >> 5)
        assert header < size <= len(data) - at
        assert data[at + 6] & 3 == 0
        audio.append(data[at + header:at + size]); at += size
    frequency_index = {48000: 3, 44100: 4}[rate]
    with pathlib.Path(str(prefix) + '.packets').open('wb') as out:
        out.write(struct.pack('>5i', 0x56504b54, fps, 320, 180, rate))
        for track in (video, audio):
            out.write(struct.pack('>i', len(track)))
            for packet in track:
                out.write(struct.pack('>i', len(packet))); out.write(packet)
        out.write(struct.pack('>H', (2 << 11) | (frequency_index << 7) | (2 << 3)))
    print(json.dumps({'fixture': str(prefix), 'fps': fps, 'rate': rate, 'videoFrames': len(video), 'audioPackets': len(audio)}))


def verify(prefix, fps, rate):
    def hashes(path):
        data = run(['ffmpeg', '-v', 'error', '-i', str(path), '-map', '0:v:0', '-f', 'framemd5', '-'])
        return [line.rsplit(b',', 1)[1].strip() for line in data.splitlines() if line and not line.startswith(b'#')]
    expected_video = hashes(str(prefix) + '.h264')
    expected_audio = run(['ffmpeg', '-v', 'error', '-i', str(prefix) + '.aac', '-f', 'f32le', '-acodec', 'pcm_f32le', '-'])
    for extension, brand in (('mp4', 'isom'), ('mov', 'qt  ')):
        movie = pathlib.Path(str(prefix) + '.' + extension)
        info = json.loads(run(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-count_frames', '-of', 'json', str(movie)]))
        assert info['format']['tags']['major_brand'] == brand, info
        assert len(info['streams']) == 2
        audio = next(s for s in info['streams'] if s['codec_type'] == 'audio')
        video = next(s for s in info['streams'] if s['codec_type'] == 'video')
        assert (video['codec_name'], video['width'], video['height'], int(video['nb_read_frames'])) == ('h264', 320, 180, fps * 3)
        assert video['r_frame_rate'] == f'{fps}/1'
        assert (audio['codec_name'], int(audio['sample_rate']), audio['channels']) == ('aac', rate, 2)
        assert 3 <= float(info['format']['duration']) < 3.1
        assert hashes(movie) == expected_video, 'Decoded video frames differ'
        decoded_audio = run(['ffmpeg', '-v', 'error', '-i', str(movie), '-map', '0:a:0', '-f', 'f32le', '-acodec', 'pcm_f32le', '-'])
        assert decoded_audio == expected_audio, 'Decoded audio samples differ'
        print(json.dumps({'container': extension, 'brand': brand, 'frames': int(video['nb_read_frames']),
                          'fps': video['r_frame_rate'], 'audioRate': rate, 'duration': info['format']['duration'],
                          'identicalDecodedVideoAndAudio': True, 'sha256': hashlib.sha256(movie.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('mode', choices=['prepare', 'verify'])
    parser.add_argument('prefix', type=pathlib.Path); parser.add_argument('--fps', type=int, choices=[30, 60], default=30)
    parser.add_argument('--rate', type=int, choices=[44100, 48000], default=48000)
    args = parser.parse_args()
    (prepare if args.mode == 'prepare' else verify)(args.prefix, args.fps, args.rate)
