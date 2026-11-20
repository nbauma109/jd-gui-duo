# Windows Executable Layout

## Fixed Filenames

The exe and JAR references in `assembler/pom.xml` use fixed names with no version numbers:

```xml
<outfile>${project.build.directory}/windows/jd-gui-duo.exe</outfile>
<dontWrapJar>true</dontWrapJar>
<jar>lib/jd-gui-duo-app.jar</jar>
```

`dontWrapJar=true` means the JAR is **not** embedded inside the exe. The exe is a small
launcher (~200 KB). Because the working directory is not changed (relative file arguments such
as `jd-gui-duo.exe foo.jar` must keep resolving against it, and Explorer starts an associated
file with that file's folder as working directory), the launcher is told where the JAR is by an
absolute classpath entry anchored to the exe directory:

```xml
<preCp>%EXEDIR%\lib\jd-gui-duo-app.jar</preCp>
```

The JAR manifest `Class-Path` references the other libraries relative to the JAR. Avoid a
wildcard (`%EXEDIR%\lib\*`): the launch4j stub expands it to one absolute path per JAR and
crashes (`0xC0000005`) once the expanded classpath exceeds ~32K characters.

## JAR Copy Step

A Maven Ant task copies the versioned JAR to a version-agnostic name and removes the
versioned original so only one copy lands in the distribution:

```xml
<copy file="${project.build.directory}/lib/jd-gui-duo-app-${project.version}.jar"
      tofile="${project.build.directory}/lib/jd-gui-duo-app.jar"
      overwrite="true" />
<delete file="${project.build.directory}/lib/jd-gui-duo-app-${project.version}.jar" />
```

## Version Information

The exe carries an explicit `<versionInfo>`. Windows takes the name shown in "Open with" and
"Default apps" from `FileDescription`, so it is set to `JD-GUI Duo ${project.version}`.
Without version info the exe is listed as "Unknown application".

## Notes

- The exe is **not** reproducible across builds: it carries the real build-time timestamps and
  the project version in its `FileDescription`.
- The **installer** (`jd-gui-duo-windows-${version}-setup.exe`, built by Inno Setup) carries a
  version-specific name and dynamic timestamps — it is a per-release artifact by design.
- Code signing via SignPath is applied after the exe is generated.
