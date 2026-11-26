# Scheduled Dimension Lock

A **server-side only** NeoForge mod for Minecraft **1.21.1** and **26.x** (`26.1.2` / `26.2` / `26.3`) that schedules when dimensions unlock — and lock again.

Vanilla clients can join without installing the mod. Built-in `en_us` and `ja_jp` language files are included, with automatic English fallbacks for vanilla clients.

## Features

- **Open Windows**: Schedule an unlock time and a re-lock time as a pair (`unlockAt < lockAt`). When an event ends, the dimension automatically locks again and evacuates non-bypass players back to the Overworld world spawn.
- **Countdown Announcements**: Broadcasts server-wide chat notices as the remaining time before an unlock or re-lock crosses configurable thresholds (default: `7d`, `1d`, `12h`, `1h`, `10m`, `5m`, `1m`).
- **Catch-Up on Restart**: Schedules are evaluated against real-world wall-clock time (`Instant.now()`), not in-game ticks. Any transitions missed while the server was offline execute on startup.
- **Public Status Command**: Any player can run `/sdl status` to check locked/unlocked states, scheduled times, and time remaining.
- **In-Game Scheduling**: Operators can lock, unlock, schedule, clear, evacuate, and reload config entirely via commands — no config edits or restarts required.
- **Defense in Depth**:
  - **Primary defense**: Cancels NeoForge's `EntityTravelToDimensionEvent` for all entities (players, mobs, items, portals, end gateways, `/tp`, `/execute in`). Operators with permission level 2+ bypass when `opBypass = true` (players only; non-player entities never bypass).
  - **Secondary defense**: When `evacuateOnLock = true` (default), every 20 ticks and immediately on login/respawn, any non-bypass player found inside a locked dimension is evacuated to the Overworld's world spawn (never a player's personal respawn point, preventing respawn loops). With `evacuateOnLock = false`, players already inside may stay; entering is still blocked by the primary defense.
  - *Note*: Custom dimension teleportation mechanisms implemented by other mods that bypass `EntityTravelToDimensionEvent` may not be intercepted by the primary defense; the periodic 20-tick evacuation check acts as the final safety net.
- **Per-World Persistence**: Lock states and schedules are stored in world `SavedData` (`scheduleddimensionlock`). Malformed entries are discarded with a warning log rather than crashing the server.
- **Overworld Protection**: `minecraft:overworld` can never be locked or scheduled.

## How It Differs from Existing Mods

- **Gate Keep**: Offers date-based unlocks, but unlock-only — no automatic re-lock window or evacuation, no countdown announcements, config-file-only scheduling, and no 26.x builds.
- **DimensionBlocker**: Offers relative timers for the Nether and End on 1.21.1 only.
- **Scheduled Dimension Lock**: Combines absolute date/time and relative duration scheduling, open windows with automatic re-lock & evacuation, countdown broadcasts, public status, in-game OP commands, and both **1.21.1** and **26.x** NeoForge support.

## Commands

Root command: `/sdl` (alias: `/scheduleddimensionlock`).
`<dimension>` supports tab-completion for all loaded dimensions.

### Public Commands (Permission Level 0)

| Command | Description |
| --- | --- |
| `/sdl status` | List all tracked dimensions, their lock status, and next scheduled transition with remaining time |
| `/sdl status <dimension>` | Show detailed status and scheduled unlock/lock times for a specific dimension |

### Operator Commands (Permission Level 2)

| Command | Description |
| --- | --- |
| `/sdl lock <dimension>` | Immediately lock a dimension (evacuates non-bypass players if `evacuateOnLock` is enabled) |
| `/sdl unlock <dimension>` | Immediately unlock a dimension (retains existing schedules; use `schedule clear` to remove them) |
| `/sdl schedule <dimension> unlock at <yyyy-MM-dd> <HH:mm>` | Schedule an unlock at an absolute date and time (interpreted in configured `timezone`) |
| `/sdl schedule <dimension> unlock in <duration>` | Schedule an unlock after a relative duration (e.g. `7d`, `2d12h30m`, `45m`, `30s`) |
| `/sdl schedule <dimension> lock at <yyyy-MM-dd> <HH:mm>` | Schedule a re-lock at an absolute date and time |
| `/sdl schedule <dimension> lock in <duration>` | Schedule a re-lock after a relative duration |
| `/sdl schedule <dimension> window <unlockDate> <unlockTime> <lockDate> <lockTime>` | Set an open window (`unlock < lock` validated). Example: `/sdl schedule minecraft:the_nether window 2027-01-01 10:00 2027-01-03 22:00` |
| `/sdl schedule <dimension> clear` | Clear all scheduled unlock/lock times for a dimension (preserves current lock state) |
| `/sdl evacuate <dimension>` | Manually evacuate all non-bypass players from `<dimension>` to the Overworld world spawn |
| `/sdl reload` | Reload runtime config settings (`timezone`, `countdownThresholds`, etc.) |

### Input Formats

- **Date-time**: `yyyy-MM-dd HH:mm` (interpreted using the configured `timezone`; seconds are not used).
- **Duration**: Concatenation of `d`, `h`, `m`, `s` units such as `2d12h30m`, `7d`, `45m`, `30s` (at least one unit, positive total).

## Configuration

The config file is named `scheduleddimensionlock-server.toml` on every supported version and is generated in the **global config directory**, not under a world's `serverconfig/`:

- **1.21.1**: `config/scheduleddimensionlock-server.toml`
- **26.1.2 / 26.2**: `config/scheduleddimensionlock-server.toml`
- **26.3**: `config/scheduleddimensionlock-server.toml` by default. FML 12 registers this slot as `SYNCED` instead of `SERVER`, so a per-world override at `<world>/syncedconfig/scheduleddimensionlock-server.toml` is additionally recognized, and the config values are synced to connected clients.

Lock states and schedules themselves remain per-world (stored in world `SavedData`).

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `timezone` | String | `""` (system default) | IANA timezone ID (e.g. `Asia/Tokyo`, `UTC`) used for parsing and displaying dates/times |
| `opBypass` | Boolean | `true` | Allow players with permission level 2+ to bypass dimension locks (never applies to non-player entities) |
| `evacuateOnLock` | Boolean | `true` | true: players inside are evacuated when a dimension locks and are also removed if found inside later (login/respawn/periodic check). false: players already inside may stay; entering a locked dimension is still blocked. |
| `announceCountdown` | Boolean | `true` | Broadcast server-wide countdown announcements before scheduled unlocks and locks |
| `countdownThresholds` | List\<String\> | `["7d", "1d", "12h", "1h", "10m", "5m", "1m"]` | Thresholds at which countdown announcements fire once per transition |
| `blockedMessageCooldownSeconds` | Integer | `5` | Cooldown (seconds) to suppress repeated blocked-entry messages for the same player |
| `defaultSchedules` | List\<String\> | `[]` | Initial schedules imported **only on first world load**. Format: `"minecraft:the_nether;unlock=2027-01-01 10:00;lock=2027-01-03 22:00"` (`unlock` and `lock` each optional) |

> **Note**: World `SavedData` is the source of truth after initial world creation. Editing `defaultSchedules` later does not overwrite existing world state; use `/sdl` commands to modify live schedules.

## Building from Source

- **Core unit tests (Java 21)**:
  ```bash
  ./gradlew :core:test
  ```
- **Minecraft 1.21.1 (NeoForge 21.1.268, Java 21)**:
  ```bash
  ./gradlew :neoforge-1.21.1:build
  ```
- **Minecraft 26.x universal jar (Java 25)**:
  ```bash
  ./gradlew :neoforge-26.x:build
  ```
- **Verify against 26.2 / 26.3**:
  ```bash
  ./gradlew :neoforge-26.x:build -PtargetNeoforgeVersion=26.2.0.95
  ./gradlew :neoforge-26.x:build -PtargetNeoforgeVersion=26.3.0.118
  ```

## License

All Rights Reserved.
