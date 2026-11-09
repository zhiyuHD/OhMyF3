# OhMyF3

**English** | [简体中文](README.zh-CN.md)

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?logo=minecraft&logoColor=white)](#)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.20.1%2B-dbb67b)](#)
[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk&logoColor=white)](#)
[![Release](https://img.shields.io/github/v/release/zhiyuHD/OhMyF3?color=blue)](https://github.com/zhiyuHD/OhMyF3/releases)
[![License](https://img.shields.io/github/license/zhiyuHD/OhMyF3?color=green)](./LICENSE)

**The F3 screen, rebuilt.** OhMyF3 replaces vanilla's wall of monospaced text with a system font,
keyword icons, rounded cards and a blurred backdrop — so you can actually read it at a glance.

Written for Minecraft **26.3** on **Fabric**. Client-side only; it changes nothing on the server.

---

## What it does

| | |
|---|---|
| 🖋 **Real font** | Text is rasterised from a system TrueType font (MiSans on this machine, with its own glyph rasteriser) instead of the 5×7 bitmap font. Subpixel positioning, so letters aren't crammed together. |
| 🧩 **Icon-first** | Every field gets an icon next to it — a compass for facing, a map pin for coordinates, a CPU for the processor, and 40 more. |
| 🎴 **Cards** | Related lines are grouped into rounded cards with a hairline border, instead of one flat list. |
| 🫧 **Blurred backdrop** | The world behind the cards is blurred using the engine's own blur chain, run over a copy of the frame — so only the card areas are blurred, not the whole screen. |
| 🎬 **Animation** | The two columns slide in and out. It's a single retargetable value, so pressing F3 mid-animation reverses from where it is instead of snapping. |
| 📊 **Profiler pie** | The F3+L hotspot chart is a flat 2D pie with supersampled edges and every slice labelled in the system font — vanilla draws it extruded and per-scanline, with the text only in a legend beside it. |
| 🀄 **Chinese labels** | Field names can be shown in Chinese (`坐标`, `区块`, `光照` …), toggled in the settings screen. |
| 🔌 **Mod rows** | Lines added by other mods are drawn with the same style, and detected by the *owning entry's namespace* rather than a hard-coded name list. |

## Install

1. Install **Fabric Loader 0.20.1+** for Minecraft 26.3.
2. Drop [**Fabric API**](https://modrinth.com/mod/fabric-api) into `mods/`.
3. Drop `OhMyF3-0.1.0.jar` from the [latest release](https://github.com/zhiyuHD/OhMyF3/releases) into the same `mods/`.

That's it — press **F3**.

## Controls

| Key | Action |
|---|---|
| **F3** | Toggle the debug screen |
| **F9** | Open the OhMyF3 settings screen |
| **OhMyF3** button | Also on the title screen and pause menu (key bindings don't fire while a screen is open, so this is the reliable way in) |

Settings can also be edited in `config/ohmyf3.json`:

```json
{
  "chineseLabels": true,
  "icons": true,
  "blur": true,
  "slideAnimation": true
}
```

## Build from source

Needs **JDK 25** (Minecraft 26.3 targets Java 25 — not 21):

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build          # jar lands in build/libs/
./gradlew runClient      # dev client
```

## How it works

The deep dive — font rasterisation, the nine-slice cards, how the blur is limited to the cards,
how animation state is shared between the two columns — lives in **[docs/internals.md](docs/internals.md)**.

## License

MIT — see [LICENSE](./LICENSE).
