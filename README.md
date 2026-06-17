# WraithBusters

**A Hytale mod by Hexvane**

WraithBusters is a horror minigame mod for Hytale. Early development — project scaffold only.

---

## Requirements

- Hytale server **0.5.0+**
- **Java 25**

---

## Building

```bash
.\gradlew.bat jar
```

Release JAR output: `build/libs/WraithBusters-0.1.0.jar`

---

## Local development

Same workflow as Machinaria, dragonlings, and OrbisOrigins:

```bash
.\gradlew.bat runServerNoSync
```

Edit assets in `src/main/resources`, then stop the server and run the command again. The dev server reads from `build/resources/main` (populated by `processResources` via the normal `classes` dependency chain).

Item and NPC names must use **`server.lang`** with keys like `server.items.YourItemId.name` — not a custom `.lang` filename.

Use `runServer` only if you want in-game asset edits synced back to source when the server stops.
