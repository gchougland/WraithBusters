#!/usr/bin/env python3
"""Generate hold-pose eye animations for portrait variants from source EyeFollow clips."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANIM_DIR = ROOT / "src/main/resources/Common/NPC/WraithBusters/Animations"
MODEL_DIR = ROOT / "src/main/resources/Server/Models/WraithBusters"
POSE_COUNT = 11
# Painting_2x2 block hitbox (X/Y/Z), centered on entity spawn (block center), before model scale.
PORTRAIT_MODEL_SCALE = 2.0
PORTRAIT_HITBOX = {
    "Min": {"X": -0.5, "Y": -0.5, "Z": -0.0375},
    "Max": {"X": 0.5, "Y": 0.5, "Z": 0.0375},
}

VARIANTS = [
    {
        "id": "Sloth",
        "texture": "NPC/WraithBusters/SlothPortrait_Texture.png",
        "source": "SlothEyeFollow.blockyanim",
        "pose_prefix": "SlothEyeFollow",
        "blink": "SlothBlinkAnim.blockyanim",
        "model_icon": "Icons/ModelsGenerated/WraithBusters_Sloth_Portrait.png",
    },
    {
        "id": "Klops",
        "texture": "NPC/WraithBusters/KlopsPortrait_Texture.png",
        "source": "KlopsEyeFollow.blockyanim",
        "pose_prefix": "KlopsEyeFollow",
        "blink": "KlopsBlinkAnim.blockyanim",
        "model_icon": "Icons/ModelsGenerated/WraithBusters_Klops_Portrait.png",
    },
    {
        "id": "Outlander",
        "texture": "NPC/WraithBusters/OutlanderPortrait_Texture.png",
        "source": "OutlanderEyeFollow.blockyanim",
        "pose_prefix": "OutlanderEyeFollow",
        "blink": "OutlanderBlinkAnim.blockyanim",
        "model_icon": "Icons/ModelsGenerated/WraithBusters_Outlander_Portrait.png",
    },
]


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def sample_position(keyframes: list, time: float) -> dict:
    if not keyframes:
        return {"x": 0.0, "y": 0.0, "z": 0.0}
    if time <= keyframes[0]["time"]:
        return dict(keyframes[0]["delta"])
    if time >= keyframes[-1]["time"]:
        return dict(keyframes[-1]["delta"])
    for i in range(len(keyframes) - 1):
        a = keyframes[i]
        b = keyframes[i + 1]
        if a["time"] <= time <= b["time"]:
            span = b["time"] - a["time"]
            t = 0.0 if span == 0 else (time - a["time"]) / span
            return {
                "x": lerp(a["delta"]["x"], b["delta"]["x"], t),
                "y": lerp(a["delta"]["y"], b["delta"]["y"], t),
                "z": lerp(a["delta"].get("z", 0.0), b["delta"].get("z", 0.0), t),
            }
    return dict(keyframes[-1]["delta"])


def pose_anim(delta: dict) -> dict:
    return {
        "formatVersion": 1,
        "duration": 1,
        "holdLastKeyframe": True,
        "nodeAnimations": {
            "Eyes": {
                "position": [
                    {
                        "time": 0,
                        "delta": {
                            "x": round(delta["x"], 4),
                            "y": round(delta["y"], 4),
                            "z": round(delta.get("z", 0.0), 4),
                        },
                        "interpolationType": "linear",
                    }
                ],
                "orientation": [],
                "shapeStretch": [],
                "shapeVisible": [],
                "shapeUvOffset": [],
            }
        },
    }


def generate_poses(variant: dict) -> None:
    source_path = ANIM_DIR / variant["source"]
    data = json.loads(source_path.read_text(encoding="utf-8"))
    keyframes = data["nodeAnimations"]["Eyes"]["position"]
    duration = data["duration"]
    prefix = variant["pose_prefix"]

    for i in range(POSE_COUNT):
        t = i / (POSE_COUNT - 1) if POSE_COUNT > 1 else 0.5
        sample_time = t * duration
        delta = sample_position(keyframes, sample_time)
        out_path = ANIM_DIR / f"{prefix}_Pose_{i:02d}.blockyanim"
        out_path.write_text(json.dumps(pose_anim(delta), indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {out_path.relative_to(ROOT)}")


def build_animation_sets(pose_prefix: str, blink_file: str) -> dict:
    sets = {
        "Idle": {
            "Animations": [
                {
                    "Animation": f"NPC/WraithBusters/Animations/{pose_prefix}_Pose_05.blockyanim",
                    "Looping": True,
                }
            ]
        },
        "IdlePassive": {
            "Animations": [
                {
                    "Animation": f"NPC/WraithBusters/Animations/{blink_file}",
                    "Looping": False,
                }
            ],
            "NextAnimationDelay": {
                "Min": 5,
                "Max": 7,
            },
        },
    }
    for i in range(POSE_COUNT):
        sets[f"EyePose_{i:02d}"] = {
            "Animations": [
                {
                    "Animation": f"NPC/WraithBusters/Animations/{pose_prefix}_Pose_{i:02d}.blockyanim",
                    "Looping": True,
                    "BlendingDuration": 0.15,
                }
            ]
        }
    return sets


def generate_model_json(variant: dict) -> None:
    model_id = f"WraithBusters_{variant['id']}_Portrait"
    doc = {
        "Model": "NPC/WraithBusters/SlothPortrait.blockymodel",
        "Texture": variant["texture"],
        "EyeHeight": 1.0,
        "MinScale": PORTRAIT_MODEL_SCALE,
        "MaxScale": PORTRAIT_MODEL_SCALE,
        "HitBox": PORTRAIT_HITBOX,
        "AnimationSets": build_animation_sets(variant["pose_prefix"], variant["blink"]),
        "Icon": variant["model_icon"],
        "IconProperties": {
            "Scale": 0.3,
            "Rotation": [0, 0, 352],
            "Translation": [-6, -34],
        },
    }
    out_path = MODEL_DIR / f"{model_id}.json"
    out_path.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {out_path.relative_to(ROOT)}")


def cleanup_legacy_poses() -> None:
    for path in ANIM_DIR.glob("EyeFollow_Pose_*.blockyanim"):
        path.unlink()
        print(f"Removed legacy {path.relative_to(ROOT)}")
    for path in ANIM_DIR.glob("*_BlinkPose_*.blockyanim"):
        path.unlink()
        print(f"Removed legacy {path.relative_to(ROOT)}")


def main() -> None:
    ANIM_DIR.mkdir(parents=True, exist_ok=True)
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    cleanup_legacy_poses()
    for variant in VARIANTS:
        generate_poses(variant)
        generate_model_json(variant)


if __name__ == "__main__":
    main()
