#!/usr/bin/env python3
"""Fail the build if either model voice is missing, damaged, silent or too long."""
import hashlib
import json
from pathlib import Path
import struct
import wave
from generate_voice_packs import vocabulary

def verify(root):
    expected=set(vocabulary())
    for voice in ('morgan','kermit'):
        folder=root/voice
        data=json.loads((folder/'manifest.json').read_text())
        assert data['voice']==voice and data['version']==1
        assert data['sampleRate']==24000 and data['channels']==1 and data['encoding']=='pcm_s16le'
        assert set(data['clips'])==expected, f'Incomplete {voice} vocabulary'
        assert {p.stem for p in folder.glob('*.pcm')}==expected
        durations={}
        for key,meta in data['clips'].items():
            raw=(folder/(key+'.pcm')).read_bytes()
            assert raw and len(raw)%2==0 and len(raw)==meta['frames']*2, f'{voice}/{key}: invalid PCM'
            assert hashlib.sha256(raw).hexdigest()==meta['sha256'], f'{voice}/{key}: checksum'
            peak=max(abs(s[0]) for s in struct.iter_unpack('<h',raw))
            assert 100<peak<30000, f'{voice}/{key}: silent or clipped'
            durations[key]=len(raw)/48000
            assert .1<durations[key]<6
        longest=max(durations[f'n{i:02}'] for i in range(100))
        # Longest permitted syntax including celebration, game result and team.
        assert durations['pickled']+durations['game']+2*longest+durations['to']+max(durations['we_win'],durations['they_win'])+.8<11
        assert durations['pickled']+durations['turnover']+2*longest+max(durations['n01'],durations['n02'])+.64<11
        print(f'{voice}: {len(expected)} verified model-generated clips')

def previews(root, output):
    """Render the same clip order and 160 ms gaps used by the Android player."""
    output.mkdir(parents=True, exist_ok=True)
    for voice in ('morgan', 'kermit'):
        clips = [(root/voice/(key+'.pcm')).read_bytes()
                 for key in ('pickled', 'turnover', 'n04', 'n02', 'n01')]
        with wave.open(str(output/(voice+'-voice-preview.wav')), 'wb') as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(24000)
            wav.writeframes(bytes(7680).join(clips))

if __name__=='__main__':
    verify(Path(__file__).resolve().parents[1]/'android/assets/voices')
