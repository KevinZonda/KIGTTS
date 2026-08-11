#!/usr/bin/env python3
"""Generate desktop TTS fixtures for the Android synthetic voiceprint matrix."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


DEFAULT_PHRASE = "请帮我打开现场字幕，并保持持续识别。"
ENROLLMENT_PHRASES = (
    "你好，我正在测试声纹识别功能。",
    "今天的天气很好，我们一起出发吧。",
    "请确认这段声音属于同一个说话人。",
)
MICROSOFT_VOICES = (
    ("microsoft_xiaoxiao", "微软晓晓（女声）", "zh-CN-XiaoxiaoNeural"),
    ("microsoft_xiaoyi", "微软晓伊（女声）", "zh-CN-XiaoyiNeural"),
    ("microsoft_yunxi", "微软云希（男声）", "zh-CN-YunxiNeural"),
    ("microsoft_yunjian", "微软云健（男声）", "zh-CN-YunjianNeural"),
    ("microsoft_xiaobei", "微软晓北（东北口音）", "zh-CN-liaoning-XiaobeiNeural"),
    ("microsoft_xiaoni", "微软晓妮（陕西口音）", "zh-CN-shaanxi-XiaoniNeural"),
)


def run(command: list[str]) -> None:
    print("+", subprocess.list2cmdline(command), flush=True)
    subprocess.run(command, check=True)


def convert_to_pcm16(source: Path, target: Path) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise RuntimeError("ffmpeg is required but was not found in PATH")
    run(
        [
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-ac",
            "1",
            "-ar",
            "16000",
            "-c:a",
            "pcm_s16le",
            str(target),
        ]
    )


def generate_microsoft(output: Path, phrase: str) -> list[dict[str, object]]:
    voices: list[dict[str, object]] = []
    for fixture_id, display_name, voice_name in MICROSOFT_VOICES:
        wave = generate_microsoft_clip(output, fixture_id, voice_name, phrase)
        enrollment_files = []
        for index, enrollment_phrase in enumerate(ENROLLMENT_PHRASES, start=1):
            enrollment_wave = generate_microsoft_clip(
                output,
                f"{fixture_id}_enrollment_{index}",
                voice_name,
                enrollment_phrase,
            )
            enrollment_files.append(
                {"phrase": enrollment_phrase, "file": enrollment_wave.name}
            )
        voices.append(
            {
                "id": fixture_id,
                "name": display_name,
                "origin": f"microsoft-edge-tts:{voice_name}",
                "file": wave.name,
                "enrollment_files": enrollment_files,
                "scenarios": ["clean", "noisy", "headshell_noisy"],
            }
        )
    return voices


def generate_microsoft_clip(
    output: Path,
    fixture_id: str,
    voice_name: str,
    text: str,
) -> Path:
    media = output / f"{fixture_id}.mp3"
    wave = output / f"{fixture_id}.wav"
    run(
        [
            sys.executable,
            "-m",
            "edge_tts",
            "--voice",
            voice_name,
            "--text",
            text,
            "--write-media",
            str(media),
        ]
    )
    convert_to_pcm16(media, wave)
    media.unlink(missing_ok=True)
    return wave


def generate_voxcpm(
    output: Path,
    phrase: str,
    model_id: str,
    device: str,
) -> dict[str, object]:
    if device == "cpu":
        os.environ["CUDA_VISIBLE_DEVICES"] = ""
    try:
        import soundfile as sf
        from voxcpm import VoxCPM
    except ImportError as error:
        raise RuntimeError(
            "VoxCPM dependencies are missing. Install them in an isolated Python 3.10-3.12 "
            "environment with: pip install voxcpm soundfile"
        ) from error

    print(f"Loading {model_id} on {device}...", flush=True)
    model = VoxCPM.from_pretrained(
        model_id,
        load_denoiser=False,
        device=None if device == "auto" else device,
    )
    if model_id.lower().endswith("voxcpm2"):
        audio = model.generate(
            text="(清晰自然、语速适中的年轻女声)" + phrase,
            cfg_value=2.0,
            inference_timesteps=10,
            seed=42,
        )
        sample_rate = int(model.tts_model.sample_rate)
    else:
        audio = model.generate(
            text=phrase,
            prompt_wav_path=None,
            prompt_text=None,
            cfg_value=2.0,
            inference_timesteps=10,
            normalize=True,
            denoise=False,
            retry_badcase=True,
            retry_badcase_max_times=3,
        )
        sample_rate = 16000

    raw_wave = output / "voxcpm_raw.wav"
    wave = output / "voxcpm.wav"
    sf.write(raw_wave, audio, sample_rate)
    convert_to_pcm16(raw_wave, wave)
    raw_wave.unlink(missing_ok=True)
    return {
        "id": "voxcpm",
        "name": f"VoxCPM（{model_id.rsplit('/', 1)[-1]}）",
        "origin": f"voxcpm:{model_id}",
        "file": wave.name,
        "scenarios": ["clean", "noisy", "headshell_noisy"],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--phrase", default=DEFAULT_PHRASE)
    parser.add_argument("--skip-microsoft", action="store_true")
    parser.add_argument("--include-voxcpm", action="store_true")
    parser.add_argument("--voxcpm-model", default="openbmb/VoxCPM-0.5B")
    parser.add_argument("--voxcpm-device", choices=("auto", "cpu"), default="auto")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    manifest_path = args.output / "manifest.json"
    voices_by_id: dict[str, dict[str, object]] = {}
    if manifest_path.is_file():
        existing = json.loads(manifest_path.read_text(encoding="utf-8"))
        if existing.get("phrase") != args.phrase:
            raise RuntimeError("Existing manifest uses a different phrase")
        voices_by_id.update(
            (str(voice["id"]), voice) for voice in existing.get("voices", [])
        )
    if not args.skip_microsoft:
        voices_by_id.update(
            (str(voice["id"]), voice)
            for voice in generate_microsoft(args.output, args.phrase)
        )
    if args.include_voxcpm:
        voice = generate_voxcpm(
            args.output,
            args.phrase,
            args.voxcpm_model,
            args.voxcpm_device,
        )
        voices_by_id[str(voice["id"])] = voice
    voices = list(voices_by_id.values())
    manifest = {"version": 1, "phrase": args.phrase, "voices": voices}
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {manifest_path} with {len(voices)} voices", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
