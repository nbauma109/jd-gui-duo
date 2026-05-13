# Version-Agnostic Windows Executable for Stable SmartScreen Reputation

## Problem Statement

SmartScreen uses file hashes to identify trusted applications. Previously, every release generated
a different `.exe` file hash due to:

1. Version numbers embedded in the filename and JAR references
2. Timestamps written into the binary by the native toolchain at build time

This caused the Windows SmartScreen reputation score to reset with each release, resulting in
security warnings for users.

## Root Causes

| Source | What changed | Why |
|---|---|---|
| Versioned filename | `jd-gui-duo-${version}.exe` | Obvious content difference |
| Versioned JAR reference | `jd-gui-duo-app-${version}.jar` embedded in exe | Obvious content difference |
| PE COFF `TimeDateStamp` | Changes every build | `ld.exe` (bundled MinGW linker) stamps current second |
| `.rsrc` section – every `IMAGE_RESOURCE_DIRECTORY.TimeDateStamp` | Changes every build | `windres.exe` (bundled resource compiler) stamps current second in every resource directory node |
| PE Optional Header `CheckSum` | Changes every build | Derived from file content; changes whenever the resource timestamps change |

> **Note**: Neither `SOURCE_DATE_EPOCH` nor `project.build.outputTimestamp` help here.
> The launch4j Maven plugin (2.7.0) has no code that reads either variable — the timestamps
> are written directly by the native `ld.exe` / `windres.exe` binaries it unpacks and calls.

## Solution

### 1. Version-Agnostic Filenames

The exe and JAR references in `assembler/pom.xml` use fixed names with no version numbers:

```xml
<outfile>${project.build.directory}/windows/jd-gui-duo.exe</outfile>
<dontWrapJar>true</dontWrapJar>
<jar>lib/jd-gui-duo-app.jar</jar>
```

`dontWrapJar=true` means the JAR is **not** embedded inside the exe. The exe is a small
launcher (~200 KB) that locates `lib/jd-gui-duo-app.jar` at runtime relative to its own
directory. Only the JAR changes between versions; the exe wrapper stays the same.

### 2. JAR Copy Step

A Maven Ant task copies the versioned JAR to a version-agnostic name and removes the
versioned original so only one copy lands in the distribution:

```xml
<copy file="${project.build.directory}/lib/jd-gui-duo-app-${project.version}.jar"
      tofile="${project.build.directory}/lib/jd-gui-duo-app.jar"
      overwrite="true" />
<delete file="${project.build.directory}/lib/jd-gui-duo-app-${project.version}.jar" />
```

### 3. Post-Build PE Timestamp Patch (`FixPeTimestamp.java`)

After launch4j generates the exe (in the `prepare-package` phase), a second Ant task in the
`package` phase runs `assembler/src/scripts/FixPeTimestamp.java` via the Java 11+ single-file
source launcher. This script neutralises every build-time timestamp in the binary:

| Field | Offset | Action |
|---|---|---|
| COFF `TimeDateStamp` | `e_lfanew + 8` | Set to fixed epoch `1704067200` (2024-01-01) |
| Every `IMAGE_RESOURCE_DIRECTORY.TimeDateStamp` in `.rsrc` | BFS-traversed | Zeroed |
| Optional Header `CheckSum` | `optHdr + 64` | Zeroed (not validated by Windows for user-mode apps) |

The script runs before `maven-assembly-plugin` packages the tar.xz, so the archive always
contains the fully-patched exe.

## Verification

Build twice from the same commit and compare hashes:

```bash
mvn clean package
sha256sum assembler/target/windows/jd-gui-duo.exe > build1.sha256

mvn clean package
sha256sum assembler/target/windows/jd-gui-duo.exe > build2.sha256

diff build1.sha256 build2.sha256   # no output = identical
```

The exe also stays identical across different application versions (v2.0.112 → v2.0.113, etc.)
because it contains no version-specific data.

## Notes

- The **installer** (`jd-gui-duo-windows-${version}-setup.exe`, built by Inno Setup) still
  carries a version-specific name and dynamic timestamps — it is a per-release artifact by design.
- The **tar.xz archives** carry real build-time timestamps on their entries (no artificial
  normalisation). Only the exe reproducibility matters for SmartScreen.
- Code signing via SignPath is applied after the exe is generated and does not affect
  reproducibility.
- The versioned JAR (`jd-gui-duo-app-${version}.jar`) exists inside the fat-jar assembly but
  is removed from `lib/` after the version-agnostic copy is made; the distribution contains
  only `jd-gui-duo-app.jar`.

## References

- [Windows PE Format – COFF Header](https://learn.microsoft.com/en-us/windows/win32/debug/pe-format)
- [Windows PE Format – Resource Directory](https://learn.microsoft.com/en-us/windows/win32/debug/pe-format#the-rsrc-section)
- [Launch4j Maven Plugin](https://github.com/orphan-oss/launch4j-maven-plugin)
- [Reproducible Builds Project](https://reproducible-builds.org/)
