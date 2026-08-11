# Third-Party Licenses

This repository includes or depends on multiple third-party components.

## 1) Piper (training/inference toolchain)
- Local vendored snapshot source: https://github.com/rhasspy/piper
- Active upstream location: https://github.com/OHF-Voice/piper1-gpl
- Local license file: `pc_trainer/third_party/piper/LICENSE.md` (MIT for vendored snapshot)

## 2) piper-phonemize (Android native subtree)
- Source: https://github.com/rhasspy/piper-phonemize
- Local license file: `android-app/app/src/main/cpp/piper-phonemize/LICENSE.md` (MIT)

## 3) eSpeak NG
- Source: https://github.com/espeak-ng/espeak-ng
- Upstream license family: GPL v3+
- Local bundled artifacts include:
  - `pc_trainer/tools/espeak-ng/**`
  - `pc_trainer/resources_pack/tools/espeak-ng/**`
  - `android-app/app/src/main/jniLibs/arm64-v8a/libespeak-ng.so`
  - `android-app/app/src/main/assets/espeak-ng-data.zip`

## 4) sherpa-onnx
- Source: https://github.com/k2-fsa/sherpa-onnx
- Android AAR path: `android-app/app/libs/sherpa-onnx-*.aar`
- Python dist-info license path:
  - `pc_trainer/piper_env/Lib/site-packages/sherpa_onnx-*.dist-info/LICENSE`

## 5) ONNX Runtime
- Source: https://github.com/microsoft/onnxruntime
- License: MIT
- Local license paths:
  - `pc_trainer/piper_env/Lib/site-packages/onnxruntime/LICENSE`
  - `pc_trainer/resources_pack/piper_env/Lib/site-packages/onnxruntime/LICENSE`

## 6) Python dependency tree
`pc_trainer/piper_env` and `pc_trainer/resources_pack/piper_env` include many Python packages with their own licenses.

Representative locations:
- `*/site-packages/*dist-info/licenses/*`
- `*/site-packages/*dist-info/LICENSE*`

## 6.1) MeCab IPADIC 2.7.0-20070801 reading data
- Source: https://taku910.github.io/mecab/ and https://sourceforge.net/projects/mecab/files/mecab-ipadic/2.7.0-20070801/
- Android asset: `android-app/app/src/main/assets/japanese_reading/ipadic_readings.tsv.xz`
- Local license copy: `android-app/app/src/main/assets/japanese_reading/IPADIC_LICENSE.txt`
- Purpose: compact surface-form to kana-reading data for Japanese text before Piper phonemization
- License: NAIST/ICOT terms included in the local license copy

## 6.2) pypinyin 0.55.0 reading data
- Source: https://github.com/mozillazg/python-pinyin
- Android asset: `android-app/app/src/main/assets/chinese_pinyin/pypinyin_readings.tsv.xz`
- Local license copy: `android-app/app/src/main/assets/chinese_pinyin/PYPINYIN_LICENSE.txt`
- Purpose: compact Chinese character and phrase readings with tone numbers before Piper phonemization
- License: MIT

## 7) NanoHTTPD 2.3.1
- Source: https://github.com/NanoHttpd/nanohttpd
- Android dependency: `org.nanohttpd:nanohttpd-websocket:2.3.1`
- License: BSD 3-Clause

Copyright (c) 2012 - 2016, nanohttpd

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the nanohttpd nor the names of its contributors
   may be used to endorse or promote products derived from this software without
   specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
OF THE POSSIBILITY OF SUCH DAMAGE.

## 8) Optional neural target-speaker resources

- Target-speaker extraction model: `penta2himajin/tse-conv-tasnet-48k`
  - Revision: `5d8934d48e582dbd00285697bde972c4ec17ba2a`
  - License: Creative Commons Attribution 4.0 International (CC BY 4.0)
  - Source: https://huggingface.co/penta2himajin/tse-conv-tasnet-48k
- Speaker-condition model: `penta2himajin/ecapa-tdnn-onnx`
  - Revision: `57bc773c7cc1a8afa117b38b0b2a38c96ffa99a2`
  - Based on: `speechbrain/spkrec-ecapa-voxceleb`
  - License: Apache License 2.0
  - Source: https://huggingface.co/penta2himajin/ecapa-tdnn-onnx
- SpeechBrain filterbank parity fixture: `penta2himajin/mellonella`
  - License: Apache License 2.0
  - Source: https://github.com/penta2himajin/mellonella
- Speaker confirmation model: `3dspeaker_speech_eres2netv2_sv_zh-cn_16k-common.onnx`
  - Original project: ModelScope `3D-Speaker`
  - ONNX distribution: sherpa-onnx speaker recognition models
  - License: Apache License 2.0
  - Source: https://github.com/modelscope/3D-Speaker
  - Runtime file SHA-256: `bf1a75b9930474cf3389ef415e6e5d38ca96fea4a3a00f7e301d080a58ee2239`

The model files are optional downloads and are not bundled in the APK. KIGTTS
downloads exact pinned revisions, verifies their SHA-256 values, and writes a
local `NOTICE.txt` alongside the installed model resources.

## 9) Optional Chinese-English punctuation model

- Model: `sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12-int8`
- Converted model source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/punctuation-models
- Original model: `iic/punc_ct-transformer_zh-cn-common-vocab272727-pytorch`
- License: Apache License 2.0

The model is an optional download and is not bundled in the APK. KIGTTS verifies
the official release archive and extracted ONNX file with pinned SHA-256 values.

---

If a third-party component is missing in this list, please add:
- component name/version
- upstream URL
- license type
- local license file path
