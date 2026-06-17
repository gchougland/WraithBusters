# WraithBusters getting started

This guide covers how to package your mansion island as a Hytale instance, configure the arena, and run a test game.

## What you need

- WraithBusters built (`.\gradlew.bat classes` at minimum)
- A local dev server with the mod loaded (start it yourself with `.\gradlew.bat runServer` when you are ready to test in game)
- World editor permissions on the server (the built in `/instances` commands require the `hytale:WorldEditor` group)

The minigame spawns a copy of the instance asset **`WraithBusters_Mansion`** for each lobby. That folder lives at:

`src/main/resources/Server/Instances/WraithBusters_Mansion/`

It must contain at least **`instance.bson`** (world config) and a **`chunks/`** folder (your built terrain). The mod reads the instance name `WraithBusters_Mansion` from code; keep the folder name in sync.

---

## Exporting the mansion island

There is no single “export to mod” button. You build the island in-game, save chunk data, then copy those files into the mod’s instance asset folder. This is the same workflow used for [Aetherhaven’s arrival island](https://github.com/Hexvane/Aetherhaven) (see `Docs/INSTANCE_EDITING_GUIDE.md` in that repo’s git history).

### How instances save chunk data

1. **Instance asset** = `Server/Instances/<Name>/` in your mod (`instance.bson`, optional `chunks/`, `resources/`).
2. **`/instances edit load`** opens the asset folder itself as the world save path — chunks write directly into the mod asset folder (then `syncAssets` copies them to `src/`).
3. **`/instances spawn`** copies the asset into a **temporary universe world** (`run/universe/worlds/instance-<Name>-<uuid>/`). Chunks save there; you copy them into the mod by hand.

When the dev server loads base game assets from `Assets.zip` (normal for `.\gradlew.bat runServer`), **`/instances edit load` is blocked** with “Cannot edit instances when using launcher assets.” That is expected. Aetherhaven hit the same issue and used **spawn + copy chunks** instead.

---

### Recommended when you see “launcher assets”: spawn, build, copy chunks

This is the workflow that worked for Aetherhaven.

**0. Temporarily allow building**

WraithBusters’ gameplay config disables block placement. Edit `src/main/resources/Server/GameplayConfigs/WraithBusters.json` and set these to `true`:

- `World.AllowBlockPlacement`
- `World.AllowBlockBreaking`
- `World.AllowBlockGathering`

Rebuild (`.\gradlew.bat classes`) and start the server. **Set them back to `false`** after you finish copying chunks.

**1. Build and start the dev server**

```
.\gradlew.bat classes
.\gradlew.bat runServer
```

**2. Spawn the instance (not edit load)**

```
/instances spawn WraithBusters_Mansion
```

You are teleported into a Creative copy of the instance. The world is saved under:

`run/universe/worlds/instance-WraithBusters_Mansion-<uuid>/`

(not in your mod folder yet).

**3. Build the mansion island**

The template uses void worldgen; default spawn is around **Y 100**. Build the courtyard lobby near spawn.

**4. Stop the server** so chunks flush to disk.

**5. Copy chunks into the mod**

Copy the entire **`chunks/`** folder from that spawned world directory into:

`src/main/resources/Server/Instances/WraithBusters_Mansion/chunks/`

Copy **`resources/`** too if it was created/updated.

**6. Set lobby spawn**

**Spawn for `/instances spawn`:** edit `SpawnProvider.SpawnPoint` in **`instance.bson`** (the game loads this file). `config.json` is only a mirror; changing it alone has no effect.

**Spawn for `/wb start` lobby:** use `/wb setup mark lobbySpawn` and save the arena, or edit `lobbySpawn` in `run/mods/Hexvane_WraithBusters/wraithbusters/arenas/mansion_v1.json`. The minigame teleports players to the arena layout, not the instance bson spawn.

**7. Restore gameplay config** — set the three `World.Allow*` flags back to `false`.

**8. Rebuild** (`.\gradlew.bat classes`). The next `/wb start` or portal-style spawn will use your island chunks.

---

### Optional: `/instances edit load` (when it is not blocked)

If your server log does **not** say `Loaded pack: Hytale:Hytale from Assets.zip`, or you run with writable unpacked base assets, you can use:

```
/instances edit load WraithBusters_Mansion
```

Chunks save directly into `build/resources/main/Server/Instances/WraithBusters_Mansion/`. Stopping the server runs **syncAssets**, which copies them into `src/main/resources/`.

Most `hytale-mod` dev setups use launcher `Assets.zip`, so **expect the spawn workflow above** instead.

### Why “launcher assets” happens

`/instances edit` checks whether the **base** Hytale pack is immutable (`InstanceEditLoadCommand`). Launcher `Assets.zip` and folders with `CommonAssetsIndex.hashes` count as read only. Your mod pack can still be writable; only **edit** is blocked. **`/instances spawn` has no such check.**

### Instance folder checklist

After export, the folder should look roughly like:

```
Server/Instances/WraithBusters_Mansion/
  instance.bson      ← required; world config + spawn (**the game loads this file**)
  config.json        ← optional human readable mirror (keep in sync; stale bson causes spawn failures)
  chunks/            ← required; your island terrain
  resources/         ← world resources (auto maintained)
```

### Tuning `instance.bson` after export

Important fields for WraithBusters (also see `config.json`):

| Setting | Purpose |
|--------|---------|
| `SpawnProvider.SpawnPoint` | Courtyard lobby join position |
| `GameplayConfig` | Should be `WraithBusters` |
| `WorldGen.Type` | `Void` for a floating build, or switch to copied chunk gen if you use a full world gen export |
| `IsGameTimePaused` | `true` keeps spooky fixed lighting |
| `Plugin.Instance` | Removal conditions when the instance empties |

After editing `instance.bson` by hand, run `.\gradlew.bat classes` before the next server start.

---

## Configuring the arena (spawns, puzzles, doors)

In game markers are stored per arena in the plugin data folder:

`pluginData/wraithbusters/arenas/<arenaId>.json`

Default arena id: **`mansion_v1`** (see `config.json` in the plugin data directory).

### Setup workflow

1. Load or join the mansion instance so you are standing in the built world.

2. Enter setup mode:
   ```
   /wb setup enter mansion_v1
   ```

3. Read the in-game guide (recommended):
   ```
   /wb setup help
   ```

4. **How marking works**

   Every marker uses the **block under your feet** (your standing position), **not** the block you are looking at. Place the mod block first, stand on top of it, then run the mark command. After each mark you get a chat line with the block coordinates so you can confirm the right cell was recorded.

5. Stand on each target and run **mark** commands:

   | Command | What it records | In-game use |
   |---------|-----------------|-------------|
   | `/wb setup mark lobbySpawn` | Lobby courtyard spawn (position + facing) | Where players wait before the round |
   | `/wb setup mark humanSpawn` | Human team spawn (run once per spot) | Humans teleport here at round start |
   | `/wb setup mark ghostSpawn` | Ghost team spawn (run once per spot) | Ghosts teleport here at round start |
   | `/wb setup mark library` | Room door anchor (unique name per room) | **Look at** a **WraithBusters_Locked_Door** block (within 24 blocks). Records all connected door blocks (both halves of 2×2 / 4×4 doors). Shorthand for `mark room --extra library` |
   | `/wb setup mark room --extra library` | Same as above (alternate syntax) | Room id is the `--extra` value |
   | `/wb setup mark candle` | One puzzle candle block | Stand on each **WraithBusters_Puzzle_Candle**. Order you mark = activation order for the puzzle |
   | `/wb setup mark candle --extra candles` | Candle in a named puzzle group | Use when a room has its own `puzzleId` |
   | `/wb setup mark possessable --extra plate` | Haunted plate block | Stand on **WraithBusters_Possessable_Plate** |
   | `/wb setup mark manaPickup` | Spirit orb spawn point | Where the floating orb entity appears each round (respawns after pickup) |
   | `/wb setup mark exorcism` | Exorcism table block | Stand on **WraithBusters_Exorcism_Table** in the attic |
   | `/wb setup mark phaseDoor` | *(deprecated)* | Use the **Phase Door Tool** instead — right-click a locked door |

   **Room doors in more detail:** look at the locked door and run the mark command. The game records **every block** in the connected door assembly (both blocks for side-by-side standard or large doors). At runtime, when a human interacts with any of those coordinates, the game looks up the room (name, key symbol, puzzle id). Defaults on first mark: `symbolId` = `blue`, `puzzleId` = `candles`, and `keySpawn` = first door block. Edit `symbolId` (must match the door item’s symbol), `keySpawn`, and `puzzleId` in the saved arena JSON after marking.

   **Ghost phase doors:** `/wb setup enter` gives you the **Phase Door Tool**. Right-click any **WraithBusters locked door** to place a bidirectional ghost portal on both sides of the doorway (portal effect scales to 1×2, 2×2, 3×3, or 4×4 doors). Left-click the door or portal to remove. Ghosts see both sides during a round and can enter from either side.

   **Candles in more detail:** each mark adds one candle to the layout with an auto-incremented index. The puzzle solution is the order of indices you marked. All candles sharing a `puzzleId` belong to one puzzle; the room’s `puzzleId` must match for the puzzle to count toward that room.

6. Save the arena:
   ```
   /wb setup save mansion_v1
   ```
   Writes `run/mods/<your-mod>/wraithbusters/arenas/mansion_v1.json`.

7. Exit setup mode:
   ```
   /wb setup exit
   ```

Place mod blocks in the world where interactions should fire (right-click with the item in hand to place):

- **WraithBusters_Ready_Pedestal** in the courtyard (ready up)
- **Locked door** variants on puzzle room doors (pick the size/style that fits the frame):
  - `WraithBusters_Locked_Door` — Temple Dark (standard)
  - `WraithBusters_Locked_Door_Temple_Dark_Medium`
  - `WraithBusters_Locked_Door_Temple_Dark_Large`
  - `WraithBusters_Locked_Door_Human_Ruins`
  - `WraithBusters_Locked_Door_Human_Ruins_Medium`
- **WraithBusters_Puzzle_Candle** for the candle puzzle
- **WraithBusters_Possessable_Plate** for ghost plates
- Spirit orb spawn points via `/wb setup mark manaPickup` (floating blue fire orbs spawn automatically during rounds)
- **WraithBusters_Exorcism_Table** in the attic

---

## Running a test game

1. Compile after any Java or asset change:
   ```
   .\gradlew.bat classes
   ```

2. Start the dev server and join with one or more clients.

3. **Host** creates a lobby and instance:
   ```
   /wb start
   ```
   Optional arena: `/wb start mansion_v1`

4. **Other players** join:
   ```
   /wb join
   ```

5. **Ready up** at the ready pedestal or:
   ```
   /wb ready
   ```
   When everyone is ready, a countdown starts and teams are assigned.

6. **During the round**
   - Humans: solve puzzles, collect keys, reach the attic exorcism table.
   - Ghosts: collect spirit orbs, possess plates, phase through ghost doors.

7. **After the round** the end screen offers **Play again** or **Return home**.

### Useful host commands

| Command | Description |
|---------|-------------|
| `/wb status` | Phase, ready count |
| `/wb forcestart` | Skip ready wait |
| `/wb stop` | Host ends the game and removes the instance |
| `/wb leave` | Leave the lobby or exit after a round |
| `/wb reload` | Reload plugin balance config |

Balance settings (ghost count formula, round length, mana, rooms per human) live in the plugin **`config.json`** in the server data directory.

---

## Typical first test checklist

- [ ] Mansion instance loads; courtyard is at instance spawn
- [ ] `/wb start` then `/wb join` from a second account
- [ ] Ready pedestal or `/wb ready` starts countdown
- [ ] Humans and ghosts spawn in different places
- [ ] Round timer appears on HUD
- [ ] Candle puzzle completes and grants a key message
- [ ] Ghost can use a plate and mana orb
- [ ] Exorcism table ends the round with human win
- [ ] Play again returns everyone to lobby

---

## Related files

| Path | Role |
|------|------|
| `src/main/resources/Server/Instances/WraithBusters_Mansion/` | Instance asset (island + config) |
| `src/main/resources/Server/GameplayConfigs/WraithBusters.json` | Block break rules, death, combat |
| `src/main/java/.../WraithBustersConstants.java` | Instance name `WraithBusters_Mansion` |
| Plugin data `config.json` | Min players, round time, ghost ratio, etc. |
| Plugin data `wraithbusters/arenas/*.json` | Per arena marker layouts |

For deeper architecture and feature list, see the implementation plan in the repo history or project notes.
