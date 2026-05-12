# Version-Agnostic Windows Executable for Stable SmartScreen Reputation

## Problem Statement

SmartScreen uses file hashes to identify trusted applications. Previously, every release generated a different `.exe` file hash due to:
1. Version numbers embedded in the executable filename and JAR references
2. Timestamps changing with each build

This caused the Windows SmartScreen reputation score to reset with each release, resulting in security warnings for users.

## Root Causes

1. **Version-specific filenames**: The exe was named `jd-gui-duo-${version}.exe` and referenced `jd-gui-duo-app-${version}.jar`, causing the exe to be different for each version
2. **Dynamic timestamps**: The Launch4j Maven plugin was embedding build-time timestamps into the PE (Portable Executable) header

## Solution

We implemented a **version-agnostic exe with fixed timestamps** to maintain the same file hash across all releases:

### 1. Maven Reproducible Builds Property

Added `project.build.outputTimestamp` to the root `pom.xml`:

```xml
<properties>
  <!-- Fixed timestamp for reproducible builds -->
  <project.build.outputTimestamp>2024-01-01T00:00:00Z</project.build.outputTimestamp>
</properties>
```

This property ensures that all Maven-generated artifacts (JARs, etc.) use a consistent timestamp, as documented in the [Maven Reproducible Builds Guide](https://maven.apache.org/guides/mini/guide-reproducible-builds.html).

### 2. Launch4j Reproducible Builds with SOURCE_DATE_EPOCH

The Launch4j Maven plugin (version 2.7.0+) supports reproducible builds through the `SOURCE_DATE_EPOCH` environment variable. This is the standard approach recommended by the [Reproducible Builds project](https://reproducible-builds.org/docs/source-date-epoch/).

#### Setting SOURCE_DATE_EPOCH

The `SOURCE_DATE_EPOCH` environment variable should be set to a Unix timestamp (seconds since 1970-01-01 00:00:00 UTC). For this project, we use `1704067200`, which corresponds to `2024-01-01T00:00:00Z`.

In GitHub Actions workflows (`.github/workflows/release.yml` and `.github/workflows/maven.yml`):

```yaml
- name: Build with Maven
  env:
    SOURCE_DATE_EPOCH: 1704067200
  run: mvn --no-transfer-progress -B package
```

For local builds, set the environment variable before running Maven:

```bash
# On Linux/macOS
export SOURCE_DATE_EPOCH=1704067200
mvn clean package

# On Windows (PowerShell)
$env:SOURCE_DATE_EPOCH=1704067200
mvn clean package
```

This ensures the PE (Portable Executable) header timestamp in the generated `.exe` file is fixed to the specified date, making the executable hash stable across builds when the content is unchanged.

### 3. Version-Agnostic Filenames

Changed the exe and JAR references to remove version numbers in `assembler/pom.xml`:

```xml
<configuration>
  <outfile>${project.build.directory}/windows/jd-gui-duo.exe</outfile>
  <dontWrapJar>true</dontWrapJar>
  <jar>lib/jd-gui-duo-app.jar</jar>
  ...
</configuration>
```

The exe is now always named `jd-gui-duo.exe` (no version) and references `jd-gui-duo-app.jar` (no version).

**Important**: `dontWrapJar=true` tells Launch4j to NOT embed the JAR inside the exe. Instead, the exe remains a small launcher that references the external JAR file. The `<jar>` path is a runtime-relative path (relative to the exe location), not a build path. This ensures:
- The exe file is very small (~200 KB) and completely stable
- Only the external JAR file changes between versions
- The exe hash never changes, even when the application code is updated
- The launcher correctly finds the JAR at `lib/jd-gui-duo-app.jar` relative to the exe location when users run the distributed application

### 4. JAR Copy Step

Added a Maven Ant task to copy the versioned JAR to a version-agnostic name:

```xml
<execution>
  <id>copy-version-agnostic-jar</id>
  <phase>prepare-package</phase>
  <configuration>
    <target>
      <copy file="${project.build.directory}/lib/jd-gui-duo-app-${project.version}.jar"
            tofile="${project.build.directory}/lib/jd-gui-duo-app.jar"
            overwrite="true" />
    </target>
  </configuration>
</execution>
```

This ensures the version-agnostic JAR name exists for the exe to reference.

## Benefits

1. **Stable File Hash Across Versions**: The exe wrapper (`jd-gui-duo.exe`) remains byte-for-byte identical across different versions (v2.0.112, v2.0.113, etc.)
2. **SmartScreen Reputation Preserved**: Windows SmartScreen reputation accumulates across all releases since the exe hash never changes
3. **Reduced Security Warnings**: Users experience fewer security warnings as the application maintains its trust score
4. **Better Security**: Stable hashes make it easier to verify official releases and detect tampering
5. **Only JAR Content Changes**: Version updates only change the JAR file content, not the exe wrapper

## Verification

### Test 1: Reproducible Builds (Same Version)

To verify that builds are reproducible for the same version:

1. Build the project twice from the same commit with SOURCE_DATE_EPOCH set:
   ```bash
   # On Linux/macOS
   export SOURCE_DATE_EPOCH=1704067200
   mvn clean package
   sha256sum assembler/target/windows/jd-gui-duo.exe > build1.sha256

   mvn clean package
   sha256sum assembler/target/windows/jd-gui-duo.exe > build2.sha256

   diff build1.sha256 build2.sha256
   ```

2. If the hashes match (no output from `diff`), the builds are reproducible

### Test 2: Version-Agnostic Exe (Different Versions)

To verify the exe stays the same across versions:

1. Build version A with SOURCE_DATE_EPOCH set:
   ```bash
   # On Linux/macOS
   export SOURCE_DATE_EPOCH=1704067200
   mvn clean package
   sha256sum assembler/target/windows/jd-gui-duo.exe > versionA.sha256
   ```

2. Update version in pom.xml to version B

3. Build version B:
   ```bash
   mvn clean package
   sha256sum assembler/target/windows/jd-gui-duo.exe > versionB.sha256
   ```

4. Compare hashes:
   ```bash
   diff versionA.sha256 versionB.sha256
   ```

5. The hashes should be **identical** (no output from `diff`), proving the exe doesn't change between versions

## Technical Details

### Timestamp Value Choice

The timestamp `2024-01-01T00:00:00Z` was chosen as:
- It's a recent date that's valid for all modern systems
- It's easy to remember and recognize
- It uses UTC timezone (indicated by the `Z` suffix) for consistency

### PE Header

The PE (Portable Executable) header is the Windows executable format specification. The timestamp field in the PE header is traditionally set to the build time. By fixing this timestamp, we ensure the PE header (and thus the file hash) remains consistent.

### Version-Agnostic Design

The exe wrapper acts as a small, stable launcher that:
- Has a fixed name: `jd-gui-duo.exe`
- References a fixed JAR name: `jd-gui-duo-app.jar`
- Does NOT embed the JAR (`dontWrapJar=true`) - the exe is just a lightweight launcher
- Contains only launcher code and configuration (no version-specific data or application code)
- Remains identical across all versions

The exe is approximately 200 KB and never changes. Only the external JAR file content changes between versions, while the exe wrapper stays byte-for-byte identical.

### Launch4j Version

This project uses Launch4j Maven plugin version 2.7.0, which supports reproducible builds via the `SOURCE_DATE_EPOCH` environment variable. This is the standard mechanism for achieving deterministic timestamps in build outputs, as documented in the [Reproducible Builds specifications](https://reproducible-builds.org/docs/source-date-epoch/).

## References

- [Maven Reproducible Builds](https://maven.apache.org/guides/mini/guide-reproducible-builds.html)
- [Launch4j Maven Plugin](https://github.com/lukaszlenart/launch4j-maven-plugin)
- [Reproducible Builds Project](https://reproducible-builds.org/)
- [Windows PE Format](https://learn.microsoft.com/en-us/windows/win32/debug/pe-format)

## Notes

- The installer `.exe` file (generated by Inno Setup) **continues to have version numbers** in its filename (`jd-gui-duo-windows-${version}-setup.exe`) and has dynamic timestamps, as it's a different file for each release
- Only the application `.exe` file (`jd-gui-duo.exe`) is version-agnostic and uses reproducible builds
- Code signing (via SignPath) is applied after the `.exe` is generated and does not affect reproducibility
- The versioned JAR (`jd-gui-duo-app-${version}.jar`) is still created and packaged; the version-agnostic name (`jd-gui-duo-app.jar`) is a copy used only by the exe wrapper

## Impact on Releases

When releasing a new version:
1. The application exe (`jd-gui-duo.exe`) remains **identical** to previous releases
2. The JAR content is updated with new features/fixes
3. Windows SmartScreen sees the same trusted exe file
4. Users experience no security warnings
5. The tar.xz distribution archive name still includes the version for clarity
