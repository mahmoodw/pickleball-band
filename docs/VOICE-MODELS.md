# Downloadable announcer voice models

Research date: 2026-10-23. The user requested Morgan Freeman and Kermit voices.

## Candidates actually found

| Candidate | Download and format | Published terms and integration status |
| --- | --- | --- |
| [RosinoSuperRosa/Kermit-The-Frog](https://huggingface.co/RosinoSuperRosa/Kermit-The-Frog) | `kermitthefrogjim.zip`, 58,085,361 bytes. Downloaded and inspected: one `.pth` model and one RVC retrieval `.index`. | Model card contains only `license: openrail`. No full license text, usage instructions, model provenance or audio sample in the archive. Not executed or bundled. |
| [DeputyRipper/Morgan_Freeman_RVCV2](https://huggingface.co/DeputyRipper/Morgan_Freeman_RVCV2) | `Morgan Freeman.zip`, 90,920,796 bytes. Downloaded and inspected: one `.pth` model and one RVC retrieval `.index`. | Model card contains only `license: openrail`. No full license text or usage instructions in the archive. Not executed or bundled. |
| [drewThomasson/Morgan_freeman_xtts_model](https://huggingface.co/drewThomasson/Morgan_freeman_xtts_model) | XTTS-v2 fine-tune, model archives, reference clips and generated examples. Model card and file listing inspected; weights not downloaded. | Despite a MIT metadata tag, the author explicitly notes XTTS's noncommercial restriction. The [base model license](https://huggingface.co/coqui/XTTS-v2/blob/main/LICENSE.txt) applies noncommercial terms to models and outputs and requires passing those terms on. Not integrated. |
| [stackeugene/MorganFreemanTTS](https://github.com/stackeugene/MorganFreemanTTS) | SpeechT5 training/inference project with a model file tracked through Git LFS. README and file listing inspected. | README claims MIT but the inspected root has no LICENSE file. Documents a Python/FastAPI workflow; Android support and output quality have not been verified. |

These are free public downloads, but that alone does not establish that they are fully open or that their outputs can be redistributed without conditions. In particular, a generic OpenRAIL metadata tag does not identify the complete terms to include with a public voice pack. No claim of official endorsement or verified likeness is made.

## Practical integration path

The RVC files convert existing speech audio into another timbre. They cannot be selected directly in Android's TextToSpeech voice chooser. They require an inference pipeline, a content encoder and pitch processing, plus a source voice. XTTS directly accepts text but also needs its own model runtime; neither candidate is an installed Android TTS voice.

Pickleball has a small vocabulary: the numbers zero through ninety-nine, “Turnover,” “Correction,” “Game,” “to,” “We win,” “They win,” and “You just got pickled!” A practical offline approach is to generate and review those clips on a computer, package the clips with their applicable terms, and assemble them on the phone. That avoids model inference and network latency on court. This requires clip playback support and validated voice assets; **v0.1.12 does not include that pipeline or named-voice clips**.

For comparison, [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) is a general TTS model with Apache-2.0 metadata and [documented voices](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md). Its listed voices do not include Morgan Freeman or Kermit, so it is not a substitute for the requested identities.

## Implemented in v0.1.12

The phone can select installed offline English voices and Classic, Deep narrator or Playful frog pitch/speed styles. The band sends optional Boolean `pickled: true` on a long-press rally message, after successfully saving the rally. The phone prefixes that rally's speech with the catchphrase. Missing flags preserve compatibility with earlier band versions; older phone versions ignore the new flag and announce the score normally. Sync, repeat, corrections and undo do not replay it. No model weights or research downloads are included in source archives, APKs or release bundles.
