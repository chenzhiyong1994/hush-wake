"""Transcode the licensed Android recordings for AVFoundation; never download assets."""
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SOUNDS = (
    "alarm_soft_bell", "alarm_clear_bell", "alarm_wind_chimes", "alarm_deep_bell",
    "alarm_garden_chimes", "alarm_morning_birds", "sleep_rain", "gentle_stream",
    "soft_fireplace", "morning_forest", "night_crickets", "gentle_wind", "ocean_waves", "distant_thunder",
)


def main():
    if not shutil.which("ffmpeg") or not shutil.which("ffprobe"):
        raise SystemExit("ffmpeg and ffprobe are required (macOS: brew install ffmpeg)")
    output = ROOT / "ios/HushWake/GeneratedResources"
    output.mkdir(parents=True, exist_ok=True)
    for name in SOUNDS:
        source = ROOT / "app/src/main/res/raw" / f"{name}.ogg"
        target = output / f"{name}.m4a"
        subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-i", str(source),
                        "-map_metadata", "-1", "-c:a", "aac", "-b:a", "160k", "-ar", "48000",
                        "-movflags", "+faststart", str(target)], check=True)
        duration = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                            "-of", "default=nw=1:nk=1", str(target)], text=True)
        if float(duration) <= 1:
            raise RuntimeError(f"Invalid audio duration: {name}")
    credits = (ROOT / "docs/audio-credits.md").read_text(encoding="utf-8")
    credits += "\n\niOS packaging: these recordings are transcoded to AAC/M4A at 48 kHz, 160 kbps. Original licenses remain in effect.\n\n"
    credits += (ROOT / "LICENSE").read_text(encoding="utf-8")
    (output / "AudioCredits.txt").write_text(credits, encoding="utf-8")
    print(f"Prepared and decoded {len(SOUNDS)} offline iOS sounds with attribution.")


if __name__ == "__main__":
    main()
