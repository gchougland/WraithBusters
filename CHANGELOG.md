# Changelog

## [0.2.1] - 7/3/2026

### Fixed

- **Ghost win crash** — Fixed a server crash when ghosts win by eliminating unsafe entity-store writes during interaction and entity-removal processing.
- **Cocoon and candle effects** — Cocoon burst damage, burn effects, slow effects, and candle/cocoon activation particles now use the interaction command buffer.
- **Disconnect cleanup** — Player departure cleanup is deferred when the player entity reference is already invalid, preventing round cleanup from running inside Hytale's entity removal callback.
- **Possessable spawns and cleanup** — Spawned possessables and marker icons now queue entity-store mutations on the world task queue.
- **Statue repair timing** — Sword statue and watcher statue filler repair now runs from deferred world tasks instead of directly in the possess interaction path.
