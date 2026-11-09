# OhMyF3

A Fabric mod scaffold for **Minecraft 26.3**.

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
