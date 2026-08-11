# 实验性语音识别模块

## 用户行为

实验识别继续使用现有 SenseVoice 底模，不改变语音包格式。面向用户只保留两个动作：

1. 按引导录制三段本人声纹。
2. 通过“仅响应我的声音”开关决定是否筛除其他说话人。

关闭开关时，所有通过语音活动检测的说话都会进入识别，可继续作为现场字幕和辅助收音。开启后，系统自动选择适合当前音频和设备性能的声纹判定路径，不展示阈值、灵敏度或模型后端选项。

## 自动声纹管线

- **CAMPPlus 初筛**：随 APK 提供，负责低延迟多窗口声纹评分。分数明确时直接通过或拒绝。
- **ERes2NetV2 确认**：完整 V6 识别资源包提供。仅处理 CAMPPlus 的灰区结果，不增加每句话的固定延迟。
- **目标说话人分离兜底**：资源和新声纹样本齐全时，可在重噪声候选段尝试 Conv-TasNet。设备实测实时系数超过 `0.85` 后，本次运行自动停用该路径。
- **失败回退**：确认模型或神经分离资源缺失、推理失败或输出质量异常时，自动回到 CAMPPlus，不中断普通识别。

三个录制样本会分别保存 CAMPPlus 与 ERes2NetV2 特征；神经资源可用时还会保存 ECAPA-TDNN 条件特征。旧声纹样本保持兼容，可继续使用 CAMPPlus；重新录制后自动补齐新特征。

## 录制与连续说话

- 录制页使用与正式收音一致的麦克风路由，优先尝试 `UNPROCESSED` 或 `VOICE_RECOGNITION`，不可用时回退 `MIC`。
- 每段样本会检查有效语音比例、削波和持续时长，并裁掉首尾静音；不合格时直接提示重新录制。
- 三段样本用于自动估计本人阈值，不要求用户理解或调整相似度。
- 一次本人验证通过后，3 秒内不超过 1.1 秒的连续短句可以复用会话结果，减少轻声、短答和断句造成的漏识别；较长语句仍重新验证。
- Silero 默认保留触发前 `240 ms` 音频。预滚与 VAD 片段拼接前会检测相同采样重叠，避免吞首字和首字重复。

## 降噪与轻声

- 新配置默认使用单层 GTCRN 流式增强；传统 RNNoise/Speex 与 GTCRN/DPDFNet 互斥，避免重复抑制轻声起音。
- 输入增益、语音活动门限和断句参数使用固定的辅助场景默认值，不再要求用户为头壳内说话或普通现场使用手动调参。
- 神经分离输出会重新经过 CAMPPlus 身份复核和能量检查；身份偏移、音频塌缩或模型异常时不会替换原候选音频。

## 文本规范化与标点

- SenseVoice V6 固定启用内建 ITN 与标点输出，不再向用户提供 ITN 或自动标点开关。中文数字增强分支会改善大数字、日期和金额识别，同时保留中文数字读法。
- 最终结果和流式预览都直接使用 V6 输出，不加载独立标点恢复模型，避免重复处理造成断句变化和额外延迟。
- 完整资源包 V6 继续使用 V5 的中文数字与万、亿、大金额修正版 SenseVoice，但已移除中英文独立标点模型。

## 资源与兼容

- 完整包：`kigtts-recognition-resources-v6-20260810.7z`
- ModelScope：`LHTSTUDIO/KIGTTS_ASR_Resource`
- Hugging Face：`LHT02/KIGTTS_ASR_Resource`
- V6 包大小：`324911962` 字节
- V6 SHA-256：`f02aab69b8c131da12e610694a85815a70d190c4a166fb012e1b3c068f40042a`

安装器对 `full-vN` 与旧 `experimental-full-vN` 完整包强制校验 `requiredFiles`。稳定 V6 共校验 11 个运行时文件，验证完成后才原子切换活动资源。用户自定义下载地址保持不变，旧的内置资源地址会自动升级到稳定 V6。

## 后续验证重点

当前实现偏向无障碍和现场可用性，而不是安全级身份认证。后续应使用中文、轻声、头壳内语音、不同距离和真实展会噪声建立固定评测集，分别统计本人漏识别与他人误通过，再调整自动阈值和是否需要训练更小的中文专用确认模型。

## Debug 静默模拟音频测试

Debug 包提供完整实时识别管线的静默测试入口。测试音频直接以 PCM 分块送入正式录音循环的后半段，不创建 `AudioRecord`、不占用麦克风，也不向 `AudioTrack` 写入数据。

未提供 WAV 时，测试入口会使用当前音色包在内存中合成默认中文语句。随后生成以下可重复场景，并依次运行兼容模式和实验模式：

- `clean`：原始合成语音。
- `quiet`：约 24% 音量的轻声近似。
- `muffled`：双级低通和衰减的头壳内闷声近似。
- `noisy`：固定随机种子的约 7 dB SNR 有色噪声。
- `reverberant`：多抽头室内混响近似。
- `headshell_noisy`：闷声、衰减和约 5 dB SNR 噪声组合。

运行全部默认场景：

```powershell
adb shell am broadcast `
  -a com.lhtstudio.kigtts.app.action.RUN_REALTIME_PIPELINE_SMOKE `
  -n com.lhtstudio.kigtts.app/.audio.RealtimePipelineSmokeReceiver `
  --es modes both `
  --es scenarios all

adb shell run-as com.lhtstudio.kigtts.app `
  cat cache/realtime-pipeline-smoke.txt
```

可通过 `--es modes current|compatibility|experimental` 选择模式，通过逗号分隔的 `--es scenarios clean,quiet,muffled` 选择场景。要使用自备 PCM16 WAV，可将文件放入应用缓存目录并命名为 `realtime-pipeline-test.wav`，也可通过 `--es audio_file 文件名.wav` 指定缓存目录内的文件。读取器支持单声道或双声道 PCM16 WAV，双声道会自动混合为单声道，识别控制器会重采样到 16 kHz。

`--ez use_speaker_verification true` 会按当前声纹配置运行门控。内置 TTS 或他人语音通常应被本人声纹拒绝；验证本人跨文本通过率仍需要一段未参与声纹录制的本人语音，模拟滤波无法替代这项身份数据。

报告包含各场景的识别文本、最大输入电平、声纹事件、音频块数量和管线耗时。`clean` 场景没有输出或产生错误时整轮标记为 `FAIL`；困难场景允许单独失败，以便用于版本间回归对比。

该测试覆盖降噪、语音增强、VAD、声纹门控、SenseVoice、ITN/标点和最终结果回调，但不能验证实体麦克风、厂商录音 DSP、实际房间声场或真实声学回声路径。

长时间聆听模式测试使用 `ListeningPipelineDeviceTest`，避免 Debug 广播超过系统的后台接收器时限。将 16 kHz 单声道 PCM16 WAV 放入应用缓存目录并命名为 `listening-pipeline-device-test.wav`，安装 Debug 与 Debug AndroidTest 包后运行：

```powershell
adb shell am force-stop com.lhtstudio.kigtts.app
adb shell am instrument -w -r `
  -e class com.lhtstudio.kigtts.app.audio.ListeningPipelineDeviceTest `
  com.lhtstudio.kigtts.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell run-as com.lhtstudio.kigtts.app `
  cat cache/listening-pipeline-device-test.txt
```

测试按真实时间将 WAV 送入正式聆听管线，报告全部流式预览和最终确认事件，并要求至少生成四条非空最终字幕。普通广播烟测仍采用非实时快速喂入，避免被厂商系统判定为长广播 ANR。

## Debug 合成音色声纹矩阵

`tools/generate_voiceprint_matrix_audio.py` 可生成外部 TTS 测试集。默认使用 Microsoft Edge 在线神经语音生成晓晓、晓伊、云希、云健、东北口音和陕西口音六组音色；每组包含三句注册语音和一条未参与注册的测试句。添加 `--include-voxcpm` 后可再调用本地 VoxCPM，生成文件统一转换为 16 kHz、单声道、PCM16 WAV。

```powershell
python tools/generate_voiceprint_matrix_audio.py `
  --output D:\KGTTS\_tmp\synthetic_voice_matrix

# VoxCPM 建议放在独立 Python 3.10-3.12 环境中；0.5B 可在 CPU 上运行。
python tools/generate_voiceprint_matrix_audio.py `
  --output D:\KGTTS\_tmp\synthetic_voice_matrix `
  --skip-microsoft --include-voxcpm `
  --voxcpm-model openbmb/VoxCPM-0.5B --voxcpm-device cpu
```

将清单和 WAV 放到 Debug 应用的外部缓存目录后，可使用应用内音色 ID 或清单中的微软音色 ID 注册临时声纹。注册特征只保存在本轮测试控制器内，不修改用户保存的声纹和设置。

```powershell
adb push D:\KGTTS\_tmp\synthetic_voice_matrix\. `
  /sdcard/Android/data/com.lhtstudio.kigtts.app/cache/synthetic_voice_matrix

adb shell am broadcast `
  -a com.lhtstudio.kigtts.app.action.RUN_SYNTHETIC_VOICEPRINT_MATRIX `
  -n com.lhtstudio.kigtts.app/.audio.SyntheticVoiceprintMatrixReceiver `
  --es enrollment_voice microsoft_xiaoxiao `
  --es external_directory /sdcard/Android/data/com.lhtstudio.kigtts.app/cache/synthetic_voice_matrix `
  --es modes experimental

adb shell run-as com.lhtstudio.kigtts.app `
  cat cache/synthetic-voiceprint-matrix.txt
```

可用注册 ID 包括 `microsoft_xiaoxiao`、`microsoft_xiaoyi`、`microsoft_yunxi`、`microsoft_yunjian`、`microsoft_xiaobei` 和 `microsoft_xiaoni`。报告按案例输出音源、场景、预期/实际决定、全部声纹分数与通过状态、识别文本和耗时。不同 TTS 的合成伪影可能让结果偏离真人说话，该矩阵用于发现回归和模型间混淆，不能替代真人本人/旁人录音评测。

## 参考

- [Google Research: VoiceFilter-Lite](https://research.google/pubs/voicefilter-lite-streaming-targeted-voice-separation-for-on-device-speech-recognition/)
- [ModelScope 3D-Speaker](https://github.com/modelscope/3D-Speaker)
- [WeNet WeSep](https://github.com/wenet-e2e/wesep)
- [Personalized PercepNet](https://arxiv.org/abs/2106.04129)
- [penta2himajin/tse-conv-tasnet-48k](https://huggingface.co/penta2himajin/tse-conv-tasnet-48k)
- [sherpa-onnx speaker recognition models](https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models)
- [OpenBMB VoxCPM](https://github.com/OpenBMB/VoxCPM)
