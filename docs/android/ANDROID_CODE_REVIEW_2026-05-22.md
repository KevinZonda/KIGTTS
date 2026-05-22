# Android code review notes - 2026-05-22

This note records code-verified issues found in the native Android app. These
items are intentionally not fixed in this pass.

## Runtime chain

- `RealtimeHostService.startRealtimeInternal()` allows recognition-only startup
  when TTS is disabled, but `RealtimeController.startMic()` still requires
  `tts != null`. This can block ASR-only use when TTS is disabled or unavailable.
- A specific TTS loading error from `RealtimeController.loadTts()` can be
  overwritten by the generic `麦克风启动失败` status in
  `RealtimeHostService.startRealtimeInternal()`.
- `MainViewModel.applySettingsSnapshot()` and
  `MainViewModel.applySettingsToController()` hard-code `piperNoiseW = 0.8f`,
  while `UserPrefs` and `RealtimeHostService` preserve the stored setting.
- `MainViewModel.applySettingsToController()` does not include `ttsDisabled`
  when calling `setSuppressAsrAutoSpeak()`, so it can temporarily disagree with
  `RealtimeHostService.applySettingsToController()`.

## Build and lint

- `:app:assembleDebug` succeeds.
- `:app:lintDebug` currently reports 13 errors and 159 warnings.
- High-priority lint errors include missing runtime-permission checks around
  `AudioRecord`, API-level issues in splash theme attrs and drawing rotation,
  CameraX `ExperimentalGetImage` opt-in, soundboard muxer flag constants, and
  the missing optional camera hardware declaration in the manifest.

## Import and resource handling

- Recognition resource extraction checks canonical paths for archive entries and
  manifest-relative files.
- Voice pack extraction checks canonical paths for archive entries. Voice pack
  manifest validation is less strict, because it resolves manifest file paths
  with `File(dir, normalized)` without a second canonical containment check.
