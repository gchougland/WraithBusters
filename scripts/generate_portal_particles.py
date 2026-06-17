#!/usr/bin/env python3
"""Generate WraithBusters phase portal particle systems sized per door tier.

Scale accounting (read before tuning):
  Vanilla Portal_Square_* spawners are authored for a 2x2-block opening (BASE_WIDTH x BASE_HEIGHT).
  Each tier only adjusts spawner Scale/EmitOffset XY by (doorWidth/2, doorHeight/2).
  NPC model Particles[].Scale is ONE constant (MODEL_WORLD_SCALE) for every tier — never multiply
  door size into the model scale or height gets applied twice.
  NPC spawn Y = door floor (PhaseDoorAnalyzer.doorFloorY). Do NOT lift the entity in Java.
  PositionOffset.Y = portal_entity_height / 2 — half the rendered portal effect height so the
  effect bottom sits on the entity origin (engine anchors the particle system center on the NPC).
  portal_entity_height = tier opening height in blocks (spawner mults size the art; MODEL_WORLD_SCALE
  is the single global tune factor, never per-tier).
"""

import json
import zipfile
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS_ZIP = Path.home() / "AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip"
OUT_DIR = ROOT / "src/main/resources/Server/Particles/WraithBusters/PhasePortal"
MODEL_DIR = ROOT / "src/main/resources/Server/Models/WraithBusters"

VANILLA_SPAWNERS = {
    "Frame1": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Square_Frame1.particlespawner",
    "Frame2": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Square_Frame2.particlespawner",
    "FrameSparks": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Square_Blue_FrameSparks.particlespawner",
    "Background": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Square_Blue_Background.particlespawner",
    "Distortion": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Square_Blue_Distortion.particlespawner",
    "BackgroundBurst": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Sqquare_Blue_BaackgroundBurst.particlespawner",
    "Frame_Perm": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Square_Frame_Perm.particlespawner",
    "Frame_PermGlow": "Server/Particles/Spell/Portal/Spawners/Square/Portal_Square_Frame_PermGlow.particlespawner",
}

SYSTEM_SPAWNERS = [
    ("Frame1", {}),
    ("Frame2", {"FixedRotation": False}),
    ("FrameSparks", {"StartDelay": 0.1}),
    ("Background", {}),
    ("Distortion", {"StartDelay": 0.5}),
    ("BackgroundBurst", {}),
    ("Frame_Perm", {}),
    ("Frame_PermGlow", {"StartDelay": 0.2}),
]

# Door opening size in blocks (width x height). Do NOT add per-tier model_scale here.
MODEL_WORLD_SCALE = 0.9

TIERS = {
    "1x2": {
        "width": 1,
        "height": 2,
        "model": "WraithBusters_Phase_Portal_1x2.json",
        "light_radius": 6,
    },
    "2x2": {
        "width": 2,
        "height": 2,
        "model": "WraithBusters_Phase_Portal_2x2.json",
        "light_radius": 8,
    },
    "3x3": {
        "width": 3,
        "height": 3,
        "model": "WraithBusters_Phase_Portal_3x3.json",
        "light_radius": 10,
    },
    "4x4": {
        "width": 4,
        "height": 4,
        "model": "WraithBusters_Phase_Portal_4x4.json",
        "light_radius": 12,
    },
}

BASE_WIDTH = 2
BASE_HEIGHT = 2


def portal_entity_height_blocks(opening_height: int) -> float:
    """World-space rendered height of the portal NPC particle effect (blocks)."""
    return float(opening_height)


def load_vanilla_spawners() -> dict[str, dict]:
    spawners: dict[str, dict] = {}
    with zipfile.ZipFile(ASSETS_ZIP) as archive:
        for key, path in VANILLA_SPAWNERS.items():
            with archive.open(path) as handle:
                spawners[key] = json.load(handle)
    return spawners


def round4(value: float) -> float:
    return round(value, 4)


def scale_axis(value: float, mult: float) -> float:
    return round4(value * mult)


def scale_scale_block_xy(block: dict, width_mult: float, height_mult: float) -> None:
    if not isinstance(block, dict):
        return
    for axis, mult in (("X", width_mult), ("Y", height_mult)):
        axis_block = block.get(axis)
        if not isinstance(axis_block, dict):
            continue
        for bound in ("Min", "Max"):
            if bound in axis_block and isinstance(axis_block[bound], (int, float)):
                axis_block[bound] = scale_axis(axis_block[bound], mult)


def scale_emit_offset_xy(block: dict, width_mult: float, height_mult: float) -> None:
    if not isinstance(block, dict):
        return
    for axis, mult in (("X", width_mult), ("Y", height_mult)):
        axis_block = block.get(axis)
        if not isinstance(axis_block, dict):
            continue
        for bound in ("Min", "Max"):
            if bound in axis_block and isinstance(axis_block[bound], (int, float)):
                axis_block[bound] = scale_axis(axis_block[bound], mult)


def zero_emit_offset_y(node: object) -> None:
    if isinstance(node, dict):
        if "EmitOffset" in node and isinstance(node["EmitOffset"], dict):
            y_block = node["EmitOffset"].get("Y")
            if isinstance(y_block, dict):
                for bound in ("Min", "Max"):
                    if bound in y_block:
                        y_block[bound] = 0.0
        for value in node.values():
            zero_emit_offset_y(value)
    elif isinstance(node, list):
        for item in node:
            zero_emit_offset_y(item)


def walk_and_scale(node: object, width_mult: float, height_mult: float) -> None:
    if isinstance(node, dict):
        if "Scale" in node and isinstance(node["Scale"], dict):
            scale_scale_block_xy(node["Scale"], width_mult, height_mult)
        if "EmitOffset" in node and isinstance(node["EmitOffset"], dict):
            scale_emit_offset_xy(node["EmitOffset"], width_mult, height_mult)
        for value in node.values():
            walk_and_scale(value, width_mult, height_mult)
    elif isinstance(node, list):
        for item in node:
            walk_and_scale(item, width_mult, height_mult)


def build_tier(tier_id: str, cfg: dict, vanilla: dict[str, dict]) -> None:
    width = cfg["width"]
    height = cfg["height"]
    width_mult = (width / BASE_WIDTH) * cfg.get("extra_width_mult", 1.0)
    height_mult = height / BASE_HEIGHT
    entity_height = portal_entity_height_blocks(height)
    bottom_anchor_y = entity_height / 2.0 + 0.2
    prefix = f"WraithBusters_Portal_{tier_id}"
    spawner_dir = OUT_DIR / "Spawners"
    spawner_dir.mkdir(parents=True, exist_ok=True)

    system = {"Spawners": [], "IsImportant": True}
    for spawner_key, extras in SYSTEM_SPAWNERS:
        data = deepcopy(vanilla[spawner_key])
        walk_and_scale(data, width_mult, height_mult)
        zero_emit_offset_y(data)
        spawner_name = f"{prefix}_{spawner_key}"
        out_path = spawner_dir / f"{spawner_name}.particlespawner"
        out_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

        entry = {
            "SpawnerId": spawner_name,
            "PositionOffset": {
                "X": 0.0,
                "Y": round4(bottom_anchor_y),
                "Z": 0.0,
            },
        }
        entry.update(extras)
        system["Spawners"].append(entry)

    system_path = OUT_DIR / f"{prefix}_Blue_Fit.particlesystem"
    system_path.write_text(json.dumps(system, indent=2) + "\n", encoding="utf-8")


def update_model(model_name: str, system_id: str, light_radius: int) -> None:
    model_path = MODEL_DIR / model_name
    model = json.loads(model_path.read_text(encoding="utf-8"))
    model["Particles"] = [{"SystemId": system_id, "Scale": MODEL_WORLD_SCALE}]
    model["Light"]["Radius"] = light_radius
    model_path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    if not ASSETS_ZIP.is_file():
        raise SystemExit(f"Assets.zip not found: {ASSETS_ZIP}")
    vanilla = load_vanilla_spawners()
    for tier_id, cfg in TIERS.items():
        build_tier(tier_id, cfg, vanilla)
        system_id = f"WraithBusters_Portal_{tier_id}_Blue_Fit"
        update_model(cfg["model"], system_id, cfg["light_radius"])
        w_mult = (cfg["width"] / BASE_WIDTH) * cfg.get("extra_width_mult", 1.0)
        h_mult = cfg["height"] / BASE_HEIGHT
        print(
            f"Generated {tier_id} -> {system_id} "
            f"(spawner {w_mult:.2f}x{h_mult:.2f}, model {MODEL_WORLD_SCALE}, "
            f"entity H={portal_entity_height_blocks(cfg['height'])}, anchor Y={portal_entity_height_blocks(cfg['height']) / 2})"
        )


if __name__ == "__main__":
    main()
