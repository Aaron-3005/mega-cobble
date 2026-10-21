# Cobblemon 1.8 release line

Mega Cobble **0.2.x** is maintained on `cobblemon-1.8`, branched from **v0.1.3**.
The `main` branch and **0.1.x** releases continue to target Cobblemon 1.7.3.

| Dependency | 0.1.x / main | 0.2.x / cobblemon-1.8 |
| --- | --- | --- |
| Minecraft | 1.21.1 | 1.21.1 |
| Java | 21 | 21 |
| Cobblemon | 1.7.3, below 1.8 | 1.8.2, below 1.9 (0.2.1+) |
| Fabric Loader | 0.17.2+ | 0.17.2+ |
| Fabric API used to build | 0.116.6+1.21.1 | 0.116.6+1.21.1 |
| Fabric Language Kotlin | 1.12.3+kotlin.2.0.21 | 1.13.6+kotlin.2.2.20 |

Version 0.2.1 requires Cobblemon 1.8.2 because it uses the new `BATTLE_ENDED` event.
Use 0.2.0 if remaining on Cobblemon 1.8.1.

The dependency versions follow the
[official Cobblemon 1.8.2 release](https://modrinth.com/mod/cobblemon/version/GJ2a1b8O)
and its [version catalog](https://gitlab.com/cable-mc/cobblemon/-/blob/1.8.2/gradle/libs.versions.toml).
The mod metadata enforces the respective Cobblemon version ranges. Install one Mega Cobble JAR,
matching the installed Cobblemon line; both JARs use the same mod ID and must not be installed together.

## Downloads and releases

- Stable 1.7.3 line: [v0.1.3](https://github.com/Aaron-3005/mega-cobble/releases/tag/v0.1.3).
- Cobblemon 1.8.2: [v0.2.2](https://github.com/Aaron-3005/mega-cobble/releases/tag/v0.2.2).
- Stable 1.8.1 line: [v0.2.0](https://github.com/Aaron-3005/mega-cobble/releases/tag/v0.2.0).
- Rolling 1.8 build: `latest-cobblemon-1.8` / `megacobble-latest-cobblemon-1.8.jar`.

The 1.8 workflow runs on `cobblemon-1.8`. It publishes its own rolling tag and asset without moving
the original `latest` tag. Versioned 1.8 releases do not replace the repository's default latest
stable release. Future patch releases on this branch use 0.2.x tags.

## Compatibility checks

Run with Java 21:

```powershell
.\gradlew.bat build
python tools/test_mega_abilities.py
.\gradlew.bat -PmegaAbilityTests runAbilityTestServer
```

The simulator runner discovers the exact dependency declared in `gradle.properties`, using a local
Cobblemon release JAR or Gradle's dependency cache. An explicit JAR path remains supported.
The headless Minecraft test uses a separate world under `build/ability-test-server` and stops itself.
The GitHub workflow requires both suites before publishing.

Development server launches explicitly include Cobblemon's ICU 71.1 dependency after Loom's
client-library filtering. Without this, GraalJS fails to start with a missing `DateFormat` class
in a clean workspace. This development classpath fix adds no library to the shipped Mega Cobble JAR;
the official Cobblemon release already bundles ICU for installed servers.

All ability corrections, UI synchronization, persistent Mega recovery and classic/custom form tests
from v0.1.3 are carried into this branch. The six provisional ability assignments and placeholder
models documented in the [ability audit](mega-abilities-audit.md) remain limitations.

## Verified for 0.2.2

- Corrected Mega Starmie to 100 base Attack and Huge Power (620 total), matching Champions.
- Release build, artifact validation, all 28 simulator test groups and the full Minecraft lifecycle
  suite pass, including all 93 Mega forms and the focused ability/UI/reload checks.

## Verified for 0.2.1

- Java 21 release build and artifact validation, including the required mixin and exclusion of test code.
- All 28 simulator tests against the checksum-verified official Cobblemon 1.8.2 release JAR.
  All 5,891 Cobblemon classes and the bundled simulator match the Maven dependency.
- All 93 Mega forms pass battle/world transformation, UI packet, reversion and save/reload checks,
  with explicit coverage for both Meowstic genders and the 24 focused ability/UI/reload checks.
- Active move slots, current PP, PP Ups and the full level-up benched learnset survive each lifecycle.
  Spending PP while Mega evolved does not refund it on reversion or reload.
- World Mega forms retain riding seats when the base species has them.
- Calling Cobblemon's battle end path directly reverts the Mega before the next tick.
- Heat Rotom still loses Overheat on reverting normally; the Mega hook leaves ordinary form rules intact.

Cobblemon 1.8.2 sanitizes moves on form changes. A targeted mixin skips that step only when a
transition involves a Mega form and the Pokemon carries this mod's durable original-state marker.
This prevents Eternal Floette's Light of Ruin and female Meowstic's moves being removed, including
during save decoding. It does not restore an old moveset or refund PP. If Cobblemon changes the
target method in a future release, the required mixin fails visibly instead of silently losing moves.

Manual visual testing across every species and interactions with third-party addons remain outside
these automated checks.

## Previously verified for 0.2.0

- Java 21 release build and artifact metadata validation.
- All 28 simulator tests against the official Cobblemon 1.8.1 release JAR. The Maven and release
  JARs contain identical Cobblemon classes and simulator data.
- All 93 Mega forms pass battle/world evolution, UI packet, reversion and save/reload checks.
- Both male and female Meowstic are tested explicitly. Its Mega form now allows both genders
  instead of inheriting the male-only base species ratio, preventing a gender/base-form change.
- The additional 24 focused ability/UI/reload checks pass on the 1.8.1 headless Minecraft server.
