#!/usr/bin/env python3
"""Retime Hytale machinima scene keyframes for constant camera travel speed.

Reads a scene JSON from UserData/Scenes, computes 3D distance between consecutive
keyframe positions, and reassigns Frame values so movement speed is uniform over
a user-specified total duration.

Example:
  python scripts/retime_machinima_scene.py ^
    "%APPDATA%\\Hytale\\UserData\\Scenes\\WraithBusters.json" ^
    --duration 420

Multi-actor scenes retime each track independently using the same --duration.
Cross-actor sync is not adjusted; use per-actor runs if actors must stay aligned.
"""

from __future__ import annotations

import argparse
import json
import math
import shutil
import sys
from pathlib import Path
from typing import Any

MIN_FRAME_EPSILON = 1e-6


def position_from_keyframe(keyframe: dict[str, Any]) -> tuple[float, float, float]:
    pos = keyframe["Settings"]["Position"]
    return float(pos["X"]), float(pos["Y"]), float(pos["Z"])


def distance(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


def retime_keyframes(
    keyframes: list[dict[str, Any]],
    target_duration: float,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if len(keyframes) < 2:
        return keyframes, {
            "skipped": True,
            "reason": "fewer than 2 keyframes",
        }

    sorted_kfs = sorted(keyframes, key=lambda kf: float(kf.get("Frame", 0)))
    old_frames = [float(kf.get("Frame", 0)) for kf in sorted_kfs]
    positions = [position_from_keyframe(kf) for kf in sorted_kfs]

    segment_distances: list[float] = []
    for i in range(1, len(positions)):
        segment_distances.append(distance(positions[i - 1], positions[i]))

    total_distance = sum(segment_distances)
    if total_distance <= 0:
        return sorted_kfs, {
            "skipped": True,
            "reason": "zero total path distance",
            "total_distance": total_distance,
        }

    blocks_per_frame = total_distance / target_duration
    new_frames = [0.0]
    cumulative = 0.0
    zero_distance_segments: list[int] = []

    for i, seg_dist in enumerate(segment_distances, start=1):
        if seg_dist <= 0:
            zero_distance_segments.append(i)
            cumulative += MIN_FRAME_EPSILON
        else:
            cumulative += seg_dist / blocks_per_frame
        new_frames.append(cumulative)

    # Snap last frame to exact target duration to avoid float drift.
    new_frames[-1] = float(target_duration)

    for kf, new_frame in zip(sorted_kfs, new_frames):
        kf["Frame"] = new_frame

    old_speeds: list[float | None] = []
    for i in range(len(old_frames) - 1):
        delta = old_frames[i + 1] - old_frames[i]
        if delta <= 0:
            old_speeds.append(None)
        else:
            old_speeds.append(segment_distances[i] / delta)

    new_speeds = [segment_distances[i] / (new_frames[i + 1] - new_frames[i]) for i in range(len(segment_distances))]

    return sorted_kfs, {
        "skipped": False,
        "old_frames": old_frames,
        "new_frames": new_frames,
        "segment_distances": segment_distances,
        "old_speeds": old_speeds,
        "new_speeds": new_speeds,
        "total_distance": total_distance,
        "blocks_per_frame": blocks_per_frame,
        "zero_distance_segments": zero_distance_segments,
    }


def print_report(actor_name: str, report: dict[str, Any]) -> None:
    print(f"\n=== Actor: {actor_name} ===")
    if report.get("skipped"):
        print(f"  Skipped: {report.get('reason', 'unknown')}")
        return

    print(f"  Total path distance: {report['total_distance']:.4f} blocks")
    print(f"  Speed: {report['blocks_per_frame']:.6f} blocks/frame")
    print(f"  Final frame: {report['new_frames'][-1]:.4f}")
    if report["zero_distance_segments"]:
        print(f"  Warning: zero-distance segments at indices {report['zero_distance_segments']}")

    print()
    print(f"  {'Seg':>3}  {'Dist':>8}  {'Old Frame':>12}  {'New Frame':>12}  {'Old Spd':>10}  {'New Spd':>10}")
    print(f"  {'---':>3}  {'--------':>8}  {'------------':>12}  {'------------':>12}  {'----------':>10}  {'----------':>10}")

    for i, seg_dist in enumerate(report["segment_distances"]):
        old_start = report["old_frames"][i]
        old_end = report["old_frames"][i + 1]
        new_start = report["new_frames"][i]
        new_end = report["new_frames"][i + 1]
        old_speed = report["old_speeds"][i]
        new_speed = report["new_speeds"][i]
        old_speed_str = f"{old_speed:.4f}" if old_speed is not None else "n/a"
        print(
            f"  {i + 1:>3}  {seg_dist:>8.4f}  "
            f"{old_start:>6.2f}->{old_end:<5.2f}  "
            f"{new_start:>6.2f}->{new_end:<5.2f}  "
            f"{old_speed_str:>10}  {new_speed:>10.4f}"
        )


def retime_scene(
    scene: dict[str, Any],
    target_duration: float,
    actor_filter: set[str] | None,
) -> tuple[dict[str, Any], list[tuple[str, dict[str, Any]]]]:
    reports: list[tuple[str, dict[str, Any]]] = []
    actors = scene.get("Actors", [])

    for actor in actors:
        name = actor.get("Name", "<unnamed>")
        if actor_filter is not None and name not in actor_filter:
            continue

        track = actor.get("Track")
        if not track or "Keyframes" not in track:
            continue

        keyframes = track["Keyframes"]
        if not keyframes:
            continue

        for kf in keyframes:
            if "Settings" not in kf or "Position" not in kf["Settings"]:
                reports.append((name, {"skipped": True, "reason": "missing Settings.Position"}))
                break
        else:
            retimed, report = retime_keyframes(keyframes, target_duration)
            track["Keyframes"] = retimed
            reports.append((name, report))

    return scene, reports


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Retime machinima scene keyframes for constant positional speed.",
    )
    parser.add_argument("scene", type=Path, help="Path to machinima scene JSON")
    parser.add_argument(
        "--duration",
        "-d",
        type=float,
        required=True,
        help="Target total frames from first to last keyframe",
    )
    parser.add_argument(
        "--output",
        "-o",
        type=Path,
        default=None,
        help="Output path (default: overwrite input with .bak backup)",
    )
    parser.add_argument(
        "--actor",
        action="append",
        default=None,
        help="Retime only this actor (repeatable; default: all actors with tracks)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print segment report without writing output",
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="Skip .bak backup when overwriting input",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    if args.duration <= 0:
        print("Error: --duration must be positive", file=sys.stderr)
        return 1

    scene_path = args.scene.resolve()
    if not scene_path.is_file():
        print(f"Error: scene file not found: {scene_path}", file=sys.stderr)
        return 1

    output_path = args.output.resolve() if args.output else scene_path
    actor_filter = set(args.actor) if args.actor else None

    scene = json.loads(scene_path.read_text(encoding="utf-8"))
    scene_name = scene.get("Name", scene_path.stem)
    print(f"Scene: {scene_name}")
    print(f"Target duration: {args.duration} frames")

    retimed_scene, reports = retime_scene(scene, args.duration, actor_filter)

    if not reports:
        print("Warning: no actors with keyframe tracks matched", file=sys.stderr)

    for actor_name, report in reports:
        print_report(actor_name, report)

    if args.dry_run:
        print("\nDry run — no file written.")
        return 0

    if output_path == scene_path and not args.no_backup:
        backup_path = scene_path.with_suffix(scene_path.suffix + ".bak")
        shutil.copy2(scene_path, backup_path)
        print(f"\nBackup: {backup_path}")

    output_path.write_text(json.dumps(retimed_scene, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
