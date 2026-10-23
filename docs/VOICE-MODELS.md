# Morgan Freeman and Kermit announcers

Version 0.1.13 includes audio generated through the two community RVC models requested by the user. The phone's only announcer choices are **Morgan Freeman** and **Kermit**. They replace the pitch presets from 0.1.12.

The APK contains **107 clips per voice**: numbers 0–99, “You just got pickled!”, “Turnover”, “Correction”, “Game”, “to”, “We win” and “They win”. The phone joins the needed clips into one PCM buffer, preserving score order and music handling. No speech model, Android TTS engine, Internet connection or external voice app is needed during play.

## Model provenance

| Voice | RVC model source | Archive SHA-256 |
| --- | --- | --- |
| Morgan Freeman | [DeputyRipper/Morgan_Freeman_RVCV2](https://huggingface.co/DeputyRipper/Morgan_Freeman_RVCV2), `Morgan Freeman.zip` | `7a8fb2803d9be0ba515f8a5b795cd8bbf6652ede10f260267d73129cc908218f` |
| Kermit | [RosinoSuperRosa/Kermit-The-Frog](https://huggingface.co/RosinoSuperRosa/Kermit-The-Frog), `kermitthefrogjim.zip` | `b4190411150db40707ddfca9f40979d504e4b1649facb066df1f35b2ec6b6e4b` |

Each model page declares `license: openrail`; the downloaded archives contain the model checkpoint and retrieval index but no separate full license document. These are community model outputs, not original recordings or officially endorsed voices. The source metadata is reported as published, without claiming ownership of the depicted voice or a broader grant of rights. The model weights and retrieval indexes are **not** included in the app or source archive.

Every voice pack contains `manifest.json` with the exact model/index hashes, inference commit, generation parameters and a hash/frame count for every rendered clip.

## Generation

1. Source speech is synthesized with [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) (Apache-2.0 model) through [kokoro-onnx](https://github.com/thewh1teagle/kokoro-onnx) (MIT). Source voices are `am_michael` for Morgan Freeman and `am_puck` for Kermit. Each word or phrase follows “Listen carefully.” during generation, providing sentence context for short numbers.
2. **Both sources pass through their respective RVC models and retrieval indexes.** [Applio](https://github.com/IAHispano/Applio) commit `c817f1f83f689ef72e0dfeb1c17ae990b9248506` (MIT) runs on CPU, using ContentVec, RMVPE, index rate 0.75 and consonant protection 0.33. Kermit's “Game” uses index rate 0.25 and protection 0.0 to preserve its ending consonant; this override is recorded in the clip manifest. RVC pitch is zero for both voices; no runtime pitch effect is used.
3. Torchaudio's `WAV2VEC2_ASR_BASE_960H` aligns the known source transcript to the audio. The converted audio is cropped at the target phrase boundary, removing “Listen carefully.” and retaining the complete ending. The result is resampled to 24 kHz mono, trimmed with edge padding, normalized with peak headroom and faded at the edges to prevent clicks. Assets use signed 16-bit little-endian PCM.
4. `scripts/verify_voice_packs.py` checks complete vocabulary, hashes, sample counts, non-silent/non-clipped audio and the longest possible call against the playback timeout. APK builds fail if the voice assets are incomplete.

The generator uses a small adapter for kokoro-onnx 0.4.9's `input_ids` model export so its speed input matches the model's float32 schema. It preserves fractional generation speeds rather than passing the library's integer speed array.

### Reproduce

The generated clips are included in the repository and source archive; ordinary APK builds do not need inference dependencies. For regeneration, use an isolated Python 3.12 environment with CPU PyTorch 2.6.0, torchaudio 2.6.0, kokoro-onnx 0.4.9, librosa 0.11.0, numpy 2.2.6, scipy 1.15.3, soundfile 0.13.1, faiss-cpu 1.10.0, transformers 4.48.3, torchcrepe 0.0.23, einops 0.8.1, requests and stftpitchshift. See `scripts/voice-generation-requirements.txt` for the recorded environment.

Prepare a work directory containing:

- `Applio/` checked out at the commit above.
- `morgan/model.pth`, `morgan/model.index`, `kermit/model.pth`, `kermit/model.index`, extracted from the hash-verified archives above.
- `kokoro.onnx` and `voices.bin` from [kokoro-onnx model-files-v1.1](https://github.com/thewh1teagle/kokoro-onnx/releases/tag/model-files-v1.1).
- Applio's ContentVec `config.json`/`pytorch_model.bin` under `Applio/rvc/models/embedders/contentvec/` and `rmvpe.pt` under `Applio/rvc/models/predictors/`, from the [Applio resources](https://huggingface.co/IAHispano/Applio/tree/main/Resources).
- `aligner/wav2vec2_fairseq_base_ls960_asr_ls960.pth` from [Torchaudio's model host](https://download.pytorch.org/torchaudio/models/wav2vec2_fairseq_base_ls960_asr_ls960.pth). Torchaudio can also download this file on the first generation run.

```bash
python scripts/generate_voice_packs.py --work-dir /path/to/work --output android/assets/voices
python scripts/verify_voice_packs.py
```

Use a fresh work directory if changing model files or generation settings; intermediate audio and alignment in `work/render-context-v2/` are cached for resuming interrupted builds. Add `--sample` and a different output directory to generate complete demo sentences instead of the clip vocabulary. `--only n00,n04,pickled` with a separate output directory renders a small, incomplete pack for inspection; incomplete packs cannot be packaged in the APK.

## Playback and validation

`VoiceClips` converts the scorer's announcement text into known clip IDs. Missing or malformed clips fail the call; playback never silently substitutes a system voice. File reads run on a worker, and `ClipPlayback` invalidates old loads and completion callbacks whenever a call is replaced, stopped or the service closes. AudioTrack plays the complete call under the existing announcement focus/volume controller. Calls use a 160 ms pause between clip fragments.

JVM checks cover all 20,000 score/serving-side combinations, number vocabulary, game-result assembly, missing/malformed audio and cancellation races. Assembled score calls are also checked with a separate speech recognizer (faster-whisper using `Systran/faster-whisper-base.en`). This helps find pronunciation and trimming errors; it does not measure voice similarity or guarantee every word is clear. Physical-device playback and perceived voice quality still need user testing.

Packaging generates `morgan-voice-preview.wav` and `kermit-voice-preview.wav` directly from the APK's clips with the same 160 ms gaps: “You just got pickled! Turnover. Four. Two. One.” Both previews are included in the release and its checksums.

Band v0.1.12 is compatible with this phone update. A matching v0.1.13 RPK changes only its version metadata. Workout Lab remains a separate experimental release.
