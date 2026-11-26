# Scheduled Dimension Lock

A server-side NeoForge mod that schedules when dimensions unlock — and lock again.

Schedule the Nether, the End, or any modded dimension to open at a set date and time, announce countdowns to your players, and automatically close the dimension when an event ends, returning everyone inside to the Overworld.

- **Open windows**: set an unlock time and a re-lock time as a pair — no manual closing after events
- **Countdown announcements**: server-wide notices at 7d / 1d / 12h / 1h / 10m / 5m / 1m before an unlock or a re-lock
- **In-game scheduling**: set, change, and clear schedules with OP commands — no config editing or restarts
- **Public status**: any player can check dimension status and time remaining with `/sdl status`
- **Server-side only**: vanilla clients can join; nothing to install for players
- **Minecraft 1.21.1 and 26.x** on NeoForge, with English and Japanese language files

## Status

Design phase. See [docs/DESIGN.md](docs/DESIGN.md) for the v1.0 specification and [docs/RESEARCH.md](docs/RESEARCH.md) for the competitor research behind it.

## How it differs from existing mods

Gate Keep already offers date-based unlocks, but only unlocks: no re-lock window, no countdowns, config-file-only scheduling, and no 26.x builds. DimensionBlocker offers relative timers for the Nether and End on 1.21.1 only. Scheduled Dimension Lock combines absolute schedules, open windows with evacuation, countdowns, and in-game commands across 1.21.1 and 26.x.
