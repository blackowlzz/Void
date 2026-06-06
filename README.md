![banner](https://cdn.modrinth.com/data/cached_images/6f624a99defd10b20af28bd4e64c743a2e3cc7c8.png)



VoidAC is a **prediction-based anticheat** that simulates every possible player movement server-side. If a player moves in a way that is mathematically impossible, they get flagged — no heuristics, no guessing.

It is a fork of [GrimAC](https://github.com/GrimAnticheat/Grim) and is **completely free and open source** under the GNU GPL v3.

---

# Direct Download on Modrinth!

Dont want to build anything? Just download Void from Modrinth!.

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-green)](https://modrinth.com/plugin/voidac)

---
## Discord / Support

Join the community for support, false positive reports, and updates.

[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?style=flat&logo=discord)](https://discord.gg/e7WffFM6k3)

---

## What it catches

| Category | Checks |
|----------|--------|
| **Movement** | Speed, Flight, Jesus, NoFall, Timer, Phase, Glide, Blink |
| **Combat** | Reach, Hitbox expansion, Aim assist, AutoClicker, AutoTotem |
| **World** | Scaffold/Tower, FastPlace, NoSlow, Baritone (auto-walk) |
| **Packets** | BadPackets, PacketOrder, TimerLimit, AntiSpoof (fake client) |
| **Exploit** | Freecam, MultiActions, Vehicle hacks, Elytra exploits |

Geyser/Bedrock players are **always exempt** — no Bedrock false positives.

---

## Why VoidAC

* **Simulation engine** — mathematically verifiable, not pattern-based. Speed hacks and flight are impossible to sneak past due to a 1:1 movement replication engine (covers everything from basic walking/swimming to riding entities).
* **Low false positives** — the engine accounts for lag, ViaVersion, ViaBackwards, ping spikes, Optifine FastMath, and many other quirks.
* **Latency & inventory compensation** — tracks inventory to prevent ghost blocks and queues world changes (like breaking blocks under players) until they reach the client, avoiding false flags.
* **Full world replication** — maintains a compressed world cache (16-64 kb per chunk) per player to allow safe multithreading, prevent issues with client-side or fake blocks, and eliminate block glitching falses.
* **No license key required** — drop the JAR in and it works.
* **Multi-version & cross-version accuracy** — one JAR covers 1.8 clients all the way to 26.1, accurately handling client/server translation, missing blocks, correct collision orders, and minor bounding box changes across versions.
* **Folia support** — runs natively on threaded-region servers.
* **Lightweight & asynchronous** — all movement checks, the world cache, and the majority of listeners run entirely on the netty thread. Carefully designed for thread safety without main-thread overhead, scaling to hundreds of players.

## Punishment system

VoidAC has a built-in punishment pipeline — no extra plugins needed (though LiteBans is supported for tempbans).

- **Auto-punish** — kicks or bans automatically once a player hits a violation threshold. Configurable per-check.
- **Manual punish** — `/void punish <player> [duration]` with a customizable ban screen.
- **Ban wave** — queue suspicious players and ban them all at once with a single command. Great for catching ban evasion.

---

## Anti-Xray & StorageESP

VoidAC can configure Paper's built-in anti-xray engine automatically on startup. It also supports **Storage ESP decoys** — fake chest/barrel/shulker packets are sent to clients to confuse X-ray mods that reveal storage blocks.

Both features are opt-in and configured in `config.yml`.

---

## Threshold Optimizer

VoidAC includes an **adaptive threshold tuning system** that learns from real server data to automatically recommend optimal violation thresholds. This eliminates guesswork when configuring punishments.

**How it works:**
1. Run `/void optimizer start` to begin collecting flag data
2. Grant trusted/legit players the `void.optimizer.legit` permission — their flags are treated as false positives
3. Let the server run for **24+ hours** with active gameplay
4. Run `/void optimizer stop` then `/void optimizer save` to generate recommendations
5. Review the generated `threshold-optimizer-report.yml` and apply thresholds to `punishments.yml` manually

**Key features:**
- **Safety multiplier** — recommends thresholds at 1.5× the highest legitimate flag to avoid false positives
- **Per-check analysis** — each check gets independent recommendations based on actual player data
- **Minimum sample validation** — ignores checks with fewer than 5 legit samples (insufficient data)
- **Lightweight** — minimal CPU overhead; the optimizer is designed to run continuously

**Available commands:**
- `/void optimizer start` — begin collecting flag data
- `/void optimizer stop` — stop collection (required before saving/discarding)
- `/void optimizer status` — view real-time statistics (checks observed, sample counts, top flagged checks)
- `/void optimizer save` — generate and save the recommendations report
- `/void optimizer discard` — clear collected data without saving

The optimizer is **opt-in** and controlled via the `threshold-optimizer.enabled` setting in `config.yml`.

---

## Commands

| Command | What it does |
|---------|-------------|
| `/void alerts` | Turn violation alerts on/off for yourself |
| `/void verbose` | Toggle detailed alert info |
| `/void profile <player>` | See a player's ping, version, brand, sensitivity |
| `/void spectate <player>` | Silently spectate a player |
| `/void history <player>` | View past violation sessions |
| `/void debug <player>` | Upload a debug trace (for reporting false positives) |
| `/void perf` | Check prediction engine timings |
| `/void reload` | Reload the config without restarting |
| `/void punish <player>` | Manually ban a player |
| `/void banwave add <player>` | Queue a player for a ban wave |
| `/void banwave execute` | Ban everyone in the queue at once |
| `/void optimizer start` | Begin collecting flag data for threshold tuning |
| `/void optimizer stop` | Stop threshold optimizer (required before saving) |
| `/void optimizer status` | View optimizer statistics and real-time data |
| `/void optimizer save` | Generate and save threshold recommendations |
| `/void optimizer discard` | Clear collected optimizer data without saving |

---

## Configuration

The config is generated on first start at `plugins/Void/config.yml`.  
VoidAC ships in **13 languages** — the language file is selected based on your server locale and written to `plugins/Void/` next to the main config.

Available languages: English · German · Spanish · French · Italian · Japanese · Dutch · Polish · Portuguese · Romanian · Russian · Turkish · Chinese

## Credits & License

VoidAC is a fork of [GrimAC](https://github.com/GrimAnticheat/Grim) by **MWHunter (DefineOutside)**, licensed under the **GNU General Public License v3.0**.  
VoidAC inherits that license — it is free software. The full license text is in LICENSE.

**Copyright © 2026 blackowlzz, MWHunter (DefineOutside) and contributors.**

Additional credits: [Axionize](https://github.com/Axionize/LightningGrim) for upstream bug fixes, and [PacketEvents](https://github.com/retrooper/packetevents) for the packet layer.
