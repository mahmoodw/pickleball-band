#!/usr/bin/env python3
"""Render the fixed score vocabulary through the pinned community RVC models.

See docs/VOICE-MODELS.md for model sources and the isolated build environment.
No inference code or model weights run inside the Android application.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys

def number(n):
    small = 'zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen'.split()
    tens = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety']
    return small[n] if n < 20 else tens[n // 10] + (' ' + small[n % 10] if n % 10 else '')

def vocabulary():
    return {**{f'n{n:02}': number(n) for n in range(100)},
            'pickled': 'You just got pickled!', 'turnover': 'Turnover.',
            'correction': 'Correction.', 'game': 'Game.', 'to': 'to',
            'we_win': 'We win.', 'they_win': 'They win.'}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--sample', action='store_true')
    parser.add_argument('--only', help='Comma-separated clip IDs for a render check')
    args = parser.parse_args()
    work, output = args.work_dir.resolve(), args.output.resolve()
    os.environ.setdefault('OMP_NUM_THREADS', '4')
    os.environ.setdefault('NUMBA_NUM_THREADS', '4')
    import numpy as np
    import onnxruntime as ort
    import soundfile as sf
    import soxr
    import torch
    import torchaudio
    from kokoro_onnx import Kokoro
    torch.set_num_threads(4)
    torch.manual_seed(1234)
    np.random.seed(1234)
    applio = work / 'Applio'
    os.chdir(applio)
    sys.path.insert(0, str(applio))
    from rvc.infer.infer import VoiceConverter
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 4
    opts.inter_op_num_threads = 1
    session = ort.InferenceSession(str(work / 'kokoro.onnx'), sess_options=opts, providers=['CPUExecutionProvider'])
    # kokoro-onnx 0.4.9 sends an integer speed for the input_ids export.
    # This model declares float32 speed; preserve the fractional speed here.
    source = Kokoro.from_session(session, str(work / 'voices.bin'))
    def synthesize(phonemes, voice, speed):
        tokens = source.tokenizer.tokenize(phonemes)
        return session.run(None, {'input_ids': np.array([[0, *tokens, 0]], dtype=np.int64),
                                 'style': np.array(voice[len(tokens)], dtype=np.float32),
                                 'speed': np.array([speed], dtype=np.float32)})[0], 24000
    source._create_audio = synthesize
    converter = VoiceConverter()
    converter.config.device = 'cpu'
    bundle = torchaudio.pipelines.WAV2VEC2_ASR_BASE_960H
    aligner = bundle.get_model(dl_kwargs={'model_dir': str(work / 'aligner')})
    labels = {label: i for i,label in enumerate(bundle.get_labels())}
    vocab = vocabulary() if not args.sample else {'sample': 'You just got pickled! Turnover. Four. Two. One.'}
    if args.only:
        vocab = {key: text for key, text in vocab.items() if key in args.only.split(',')}
    profiles = [('morgan', 'Morgan Freeman', 'am_michael', 0.85, 'DeputyRipper/Morgan_Freeman_RVCV2'),
                ('kermit', 'Kermit', 'am_puck', 0.95, 'RosinoSuperRosa/Kermit-The-Frog')]
    for voice, label, base, speed, repo in profiles:
        destination = output / voice
        destination.mkdir(parents=True, exist_ok=True)
        raw = work / 'render-context-v2' / voice
        raw.mkdir(parents=True, exist_ok=True)
        model, index = work / voice / 'model.pth', work / voice / 'model.index'
        manifest = {'version': 1, 'voice': voice, 'label': label, 'sampleRate': 24000,
                    'encoding': 'pcm_s16le', 'channels': 1,
                    'modelSource': f'https://huggingface.co/{repo}',
                    'modelLicenseMetadata': 'openrail', 'modelSha256': sha(model), 'indexSha256': sha(index),
                    'applioCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=applio, text=True).strip(),
                    'sourceModel': 'hexgrad/Kokoro-82M', 'sourceVoice': base, 'sourceSpeed': speed,
                    'rvcPitch': 0, 'rvcIndexRate': 0.75, 'f0Method': 'rmvpe',
                    'context': 'Listen carefully. followed by the clip; source CTC alignment before cropping converted audio',
                    'alignmentModel': 'torchaudio/WAV2VEC2_ASR_BASE_960H', 'clips': {}}
        for key, text in vocab.items():
            source_file, converted = raw / (key + '-source.wav'), raw / (key + '-rvc.wav')
            index_rate, protect = .75, .33
            if voice=='kermit' and key=='game':
                # Stronger consonant preservation keeps the final /m/ distinct.
                index_rate, protect = .25, .0
                converted = raw / (key + '-rvc-articulation.wav')
            if not source_file.exists():
                full_text = text if args.sample else 'Listen carefully. ' + text
                audio, rate = source.create(full_text if full_text.endswith(('.', '!')) else full_text + '.', voice=base, speed=speed, lang='en-us')
                # Padding protects consonants at both edges during RVC conversion.
                sf.write(source_file, np.pad(audio, (int(rate * .12), int(rate * .18))), rate)
            if not args.sample:
                span_file = raw / (key + '-ctc.json')
                if not span_file.exists():
                    original, source_rate = sf.read(source_file, dtype='float32')
                    speech = torch.from_numpy(soxr.resample(original,source_rate,16000)).unsqueeze(0)
                    prefix = 'LISTEN|CAREFULLY|'
                    transcript = prefix + re.sub('[^A-Z ]','',text.upper()).replace(' ','|')
                    target = torch.tensor([[labels[c] for c in transcript]],dtype=torch.int32)
                    with torch.inference_mode():
                        emission,_ = aligner(speech)
                        paths,scores = torchaudio.functional.forced_align(emission.log_softmax(-1),target)
                        spans = torchaudio.functional.merge_tokens(paths[0],scores[0].exp())
                    if len(spans)!=len(transcript):
                        raise RuntimeError(f'Could not align {voice}/{key}')
                    seconds = len(original)/source_rate/emission.shape[1]
                    # Keep the entire final phrase. A small margin protects the
                    # first consonant; never include the preceding word's end.
                    previous_end = spans[len(prefix)-2].end*seconds
                    first_start = spans[len(prefix)].start*seconds
                    crop_start = max(previous_end,first_start-.05)
                    span_file.write_text(json.dumps({'start':crop_start,'text':transcript}))
                crop_start = json.loads(span_file.read_text())['start']
            if not converted.exists():
                converter.convert_audio(str(source_file), str(converted), str(model), str(index),
                                        pitch=0, f0_method='rmvpe', index_rate=index_rate, protect=protect,
                                        volume_envelope=1.0, export_format='WAV')
            audio, rate = sf.read(converted, dtype='float32')
            if not args.sample:
                audio = audio[int(crop_start*rate):]
            audio = soxr.resample(audio, rate, 24000)
            assert audio.ndim == 1 and np.isfinite(audio).all(), key
            audio -= np.mean(audio)
            peak = np.max(np.abs(audio))
            assert peak > .002, f'Silent clip: {voice}/{key}'
            active = np.flatnonzero(np.abs(audio) > max(.003, peak * .015))
            audio = audio[max(0, active[0] - 960):min(len(audio), active[-1] + 1441)]
            voiced = audio[np.abs(audio) > peak * .08]
            rms = np.sqrt(np.mean(voiced ** 2))
            gain = min(4., .14 / max(.001, rms), .88 / np.max(np.abs(audio)))
            audio *= gain
            ramp = min(120, len(audio) // 2)
            audio[:ramp] *= np.linspace(0, 1, ramp)
            audio[-ramp:] *= np.linspace(1, 0, ramp)
            assert .1 < len(audio) / 24000 < (15 if args.sample else 6), f'Unexpected duration: {voice}/{key}'
            pcm = (audio * 32767).round().astype('<i2').tobytes()
            target = destination / (key + '.pcm')
            target.write_bytes(pcm)
            manifest['clips'][key] = {'text': text, 'frames': len(audio), 'sha256': sha(target)}
            if (index_rate,protect)!=(.75,.33):
                manifest['clips'][key].update(rvcIndexRate=index_rate,rvcProtect=protect)
            if args.sample:
                sf.write(destination / 'sample.wav', audio, 24000, subtype='PCM_16')
            print(f'{voice}/{key}: {len(audio)/24000:.2f}s', flush=True)
        (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')

if __name__ == '__main__':
    main()
