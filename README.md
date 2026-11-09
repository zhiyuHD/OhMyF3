# OhMyF3

A Fabric mod for **Minecraft 26.3** that slides the F3 debug screen in from the left.

Minecraft `26.3` is a real, released version (2026-09-15), published under Mojang's
year-based version naming scheme that replaced the old `1.x` numbering.

## Verified versions

Every value below was checked against the official metadata rather than assumed:

| Component | Version | Source |
| --- | --- | --- |
| Minecraft | `26.3` | Mojang version manifest (`latest.release`) |
| Fabric Loader | `0.20.1` | `meta.fabricmc.net/v2/versions/loader/26.3` |
| Fabric Loom | `1.19-SNAPSHOT` | `maven.fabricmc.net` (resolves to `1.19-20261106.225003-4`) |
| Fabric API | `0.165.2+26.3` | Modrinth project `fabric-api` |
| Java | **25** | Mojang `26.3.json` → `javaVersion.majorVersion = 25` |
| Gradle | `9.8.2` | Gradle wrapper |

## The F3 slide-in animation

Pressing F3 now slides the debug overlay in from the left over ~260 ms with an
ease-out curve, instead of it popping into place.

| File | Role |
| --- | --- |
| `src/main/java/com/ohmyf3/animation/SlideInAnimation.java` | Pure timing/easing math, no Minecraft types |
| `src/client/java/com/ohmyf3/client/mixin/HudMixin.java` | Applies the offset to the HUD extraction pose |
| `src/test/java/com/ohmyf3/animation/SlideInAnimationTest.java` | Unit tests for the easing and the restart behaviour |

How it works: 26.3 renders the HUD with an extract/draw split. `Hud.extractDebugOverlay`
records the debug overlay's geometry into a `GuiGraphicsExtractor`, and every vertex it
emits is baked with the extractor's current pose (`extractor.pose()`, a
`org.joml.Matrix3x2fStack`). Pushing a translation around that single call therefore moves
the entire overlay.

`Hud.extractDebugOverlay` was chosen as the hook because:

- it runs every frame, so opening and closing F3 can both be observed. The only exception
  is the debug-options screen, which skips the call entirely;
- its body is a single call with one return path, so the `@Inject` at `HEAD` (push +
  translate) and the one at `RETURN` (pop) always pair up. MixinExtras is not bundled with
  Fabric Loader 0.20.1, so `@WrapMethod` was not available here;
- visibility comes from `DebugScreenOverlay#showDebugScreen()`, the same check the overlay
  itself uses before extracting anything.

Tuning is two constants at the top of `HudMixin`:

```java
private static final long SLIDE_DURATION_NANOS = 260_000_000L; // slide length
private static final float SLIDE_DISTANCE = 200.0F;            // start offset in GUI px
```

Negative `SLIDE_DISTANCE` slides in from the right; `SlideInAnimation` also accepts a second
constructor argument that holds the panel displaced for a moment before it starts moving.

## System font rendering for the debug screen

The F3 text is drawn with a **system TrueType font** instead of Minecraft's bitmap font.
The TTF engine is written from scratch — no FreeType, no fontconfig parsing library, no
AWT rasterizer — because the point was to own the pipeline end to end.

| File | Role |
| --- | --- |
| `src/main/java/com/ohmyf3/font/TtfFont.java` | TrueType parser: `head`, `maxp`, `hhea`, `hmtx`, `cmap` 4/12, `loca`, `glyf`, composite glyphs |
| `src/main/java/com/ohmyf3/font/GlyphOutline.java` | Contours in font units, on/off-curve points preserved |
| `src/main/java/com/ohmyf3/font/GlyphRasterizer.java` | Quadratic flattening + non-zero winding fill, exact horizontal coverage and 4x vertical supersampling |
| `src/main/java/com/ohmyf3/font/SystemFonts.java` | Finds a font: `-Dohmyf3.font=`, `OHMYF3_FONT`, `fc-match`, then a scan of the usual directories |
| `src/client/java/com/ohmyf3/client/font/GlyphAtlas.java` | Shelf-packs glyphs into one 1024x1024 atlas |
| `src/client/java/com/ohmyf3/client/font/TtfTextRenderer.java` | Texture upload, layout, formatting codes, quad emission |

How it fits into 26.3:

- `DebugScreenOverlay#extractLines` is hooked; the injection draws both columns itself and
  cancels vanilla. If no usable font is found the injection does **not** cancel, so the
  screen silently stays with vanilla text.
- Glyphs are rasterised at the current **device** pixel ratio (GUI scale times the pose
  scale the overlay applies) and drawn at 1/N of that in GUI units, so atlas texels land on
  screen pixels one-to-one. Rasterising at the nominal 9px and letting the GUI scale blow
  it up is what made the first attempt look blurry.
- Glyph colour is baked into the atlas (the GUI blit path has no tint parameter), so the
  atlas is keyed by `(code point, colour)`. Legacy `§` formatting codes are parsed and
  removed from the layout, which also matters for width computation: mods colour their
  debug lines with `§7`, `§a`, `§r`, and drawing those literally used to show up as "§7" in
  the text.
- Mod compatibility: other mods contribute lines through `DebugScreenDisplayer`, which end
  up in the same `List<String>` and are therefore drawn with the same font, colours and
  layout. Nothing is filtered or reordered.

Two bugs worth remembering, both caught by checking output rather than trusting the code:

- Simple-glyph x and y coordinates use **different flag bits** (`0x02`/`0x10` for x,
  `0x04`/`0x20` for y). Decoding both axes with the x bits left every y coordinate garbage,
  which stretched glyphs into 200-450px tall white bars. `TtfGlyphBoundsTest` now compares
  parsed outlines against the bounding boxes stored in `glyf` for all of ASCII.
- `new NativeImage(format, w, h, false)` leaves the buffer **uninitialised**; the unused
  atlas area then leaked garbage pixels into the texture and showed up as stray white
  blocks. The atlas is created with `true` (calloc).

## Debug screen layout

The content itself is rebuilt around icons rather than decorated with them:

- every line is classified into an **icon + key + value** entry
  (`EntryClassifier`); the key is drawn dim, the value bright;
- values form a real column: each card aligns them at the widest key, so the numbers line up;
- blank lines still separate groups, and each group is a **rounded card** (nine-slice texture
  generated in code: fill `0xC8141418`, 1px border, 6px radius);
- the backdrop is **blurred** behind the cards via the GUI render state's once-per-frame
  `blurBeforeThisStratum()`; if anything else already used the frame's blur the overlay simply
  draws sharp;
- every row has a fixed **icon gutter**, tinted per category;
- rows that are not vanilla fields go into a separate **Mods card** at the end of the column,
  with an amber border and a `Mods` header.

### Telling mod rows apart

Rows are **not** guessed from their text. The overlay assembles the screen from registered
entries: for every visible id it looks up the entry and asks it to emit lines through a
`DebugScreenDisplayer`. Vanilla registers under the `minecraft` namespace and mods under their
own, so the namespace of the entry that emitted a line answers "who wrote this row" exactly.

`DebugScreenEntriesMixin` hooks `DebugScreenEntries.getEntry(id)` — called once per entry,
immediately before that entry emits — and `DebugScreenDisplayerMixin` tags each line pushed
through the displayer with that namespace. Lines are matched by object identity, because the
displayer stores the very same `String` instances the overlay later hands to `extractLines`.
Rows the overlay synthesises itself (blank separators, the "to edit" hints) are never tagged
and stay in the vanilla area, so they cannot be misattributed.

`EntryClassifier` therefore only recovers structure — icon, key, value — and says nothing
about ownership.

## Keyword icons

A recognised word gets an icon in its gutter. Apart from Sodium's logo (below), the icons are
**copied one-to-one from Lucide** (`lucide-static` 1.70.0, ISC): original 24x24 path data,
2px strokes, round caps and joins.

| Keyword | Icon | Source |
| --- | --- | --- |
| `fps` | speedometer | Lucide `gauge` |
| `MiB` | RAM stick | Lucide `memory-stick` |
| `Chunk` | four tiles | Lucide `layout-grid` |
| `XYZ` | reticle | Lucide `crosshair` |
| `Sodium` | Sodium's own logo | `sodium-icon.png` |
| `OpenGL` | processor | Lucide `cpu` |
| `Java` | coffee cup | Lucide `coffee` |
| `Seed` | dice | Lucide `dices` |

`SvgPath` parses the Lucide path data (`M L H V C S Q T A Z`, absolute and relative, implicit
linetos, repeated coordinate sets) and flattens curves and elliptical arcs to polylines;
`IconPainter` strokes them by distance field, 8x8 samples per pixel. Because the stroke has
round caps, Lucide's zero-length segments (`M6 18h.01`) become dots for free.

**Sodium's logo is not a vector in Sodium's code.** Its video settings screen blits
`sodium-icon.png` through a tinted blit, so there is no path data to copy. The mark is
therefore embedded as a 48x48 coverage mask traced from that icon (`SodiumLogo`), sampled
bilinearly when painted, which keeps the shape accurate without shipping another mod's asset.

## Text rendering quality

Three separate problems made the first versions look bad, and each has a concrete cause:

- **Glyphs were rasterised at 9px and blown up by the GUI scale.** They are now rasterised at
  the current device ratio (`guiScale * poseScale`) and drawn at 1/N of that in GUI units.
- **Quads can only be placed and sized on whole GUI pixels**, which are several screen pixels
  wide, so rounding a glyph's bitmap size down to GUI units made the GUI scale resample it —
  that is the soft, aliased look. The bitmap is padded out to the device grid
  (`alignToDeviceGrid`) so one texel lands on exactly one screen pixel.
- **Advances were integers**, which collapsed the font's side bearings and made some letter
  pairs touch while others drifted apart. Advances are fractional now, and glyphs are placed
  with a **subpixel pose translation** for the remainder, since the render state captures the
  pose per quad.

Known limits: the GUI blit API takes integer coordinates, so subpixel placement costs one
matrix push/pop per glyph; and `§l`/`§o` are still ignored (only colours are applied).

## Requirements

- **JDK 25** (not 21!). Minecraft 26.3 targets Java 25, so the toolchain must be 25.
  Point `JAVA_HOME` at a JDK 25 install before building.
- No need to install Gradle — use the bundled wrapper.

## Build

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew build
```

The remapped jar lands in `build/libs/OhMyF3-0.1.0.jar`.
Run a dev client with `./gradlew runClient`.

## Layout

```
src/main/java/com/ohmyf3/          common entrypoint (OhMyF3)
src/main/java/com/ohmyf3/mixin/    common mixins
src/main/resources/                fabric.mod.json, ohmyf3.mixins.json
src/client/java/com/ohmyf3/client/ client entrypoint (OhMyF3Client)
src/client/java/.../client/mixin/  client-only mixins
src/client/resources/              ohmyf3.client.mixins.json
```

The split `main` / `client` source sets come from `splitEnvironmentSourceSets()`
in `build.gradle`; anything in `src/client` is never loaded on a dedicated server.

## Notes on the 26.x toolchain

The 26.x toolchain is not a drop-in upgrade of a 1.21.x build script. Verified against
the official artifacts:

- **Minecraft 26.3 ships unobfuscated.** The official client jar contains readable
  names (`net/minecraft/resources/Identifier.class`, `net/minecraft/client/Minecraft.class`)
  and no `class_1234` intermediary names. Because there is nothing to remap,
  Yarn and intermediary are gone: Yarn stops at `1.21.11`, `intermediary` reports
  version `0.0.0`, and this build has **no `mappings` line and no `remapJar` task**.
  The jar in `build/libs` is already in the runtime namespace, so it loads as-is.
- **Do not add `mappings`.** Copying an older script that has
  `mappings "net.fabricmc:yarn:..."` will fail on 26.3.
- **`Identifier` moved** to `net.minecraft.resources.Identifier` (not
  `net.minecraft.util.Identifier`) and is built with
  `Identifier.fromNamespaceAndPath(namespace, path)`. Expect further `net.minecraft.*`
  package moves relative to 1.21.x mappings.
- `compatibilityLevel` in the mixin configs is `JAVA_25`.

## Renaming

The mod id is `ohmyf3`, package root `com.ohmyf3`. To rename, update
`gradle.properties` (`group`), `settings.gradle` (`rootProject.name`),
`fabric.mod.json` (`id`, entrypoints), the mixin configs, and the source
directories together.

## License

MIT — see `LICENSE`.
