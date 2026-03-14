[![Dependabot updates](https://img.shields.io/badge/Dependabot-Updates-025E8C?logo=dependabot&logoColor=white)](https://github.com/nbauma109/jd-gui-duo/network/updates)
[![](https://jitpack.io/v/nbauma109/jd-gui-duo.svg)](https://jitpack.io/#nbauma109/jd-gui-duo)
[![](https://jitci.com/gh/nbauma109/jd-gui-duo/svg)](https://jitci.com/gh/nbauma109/jd-gui-duo)
[![CodeQL](https://github.com/nbauma109/jd-gui-duo/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/codeql-analysis.yml)
[![Maven Release](https://github.com/nbauma109/jd-gui-duo/actions/workflows/maven.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/maven.yml)
[![Github Release](https://github.com/nbauma109/jd-gui-duo/actions/workflows/release.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/release.yml)

Downloads from Github releases :

[![Github Downloads (all releases)](https://img.shields.io/github/downloads/nbauma109/jd-gui-duo/total.svg)]()
[![Github Downloads (latest release)](https://img.shields.io/github/downloads/nbauma109/jd-gui-duo/latest/total.svg)]()

Downloads from Jitpack :

[![Jitpack Downloads](https://jitpack.io/v/nbauma109/jd-gui-duo/month.svg)](https://jitpack.io/#nbauma109/jd-gui-duo)
[![Jitpack Downloads](https://jitpack.io/v/nbauma109/jd-gui-duo/week.svg)](https://jitpack.io/#nbauma109/jd-gui-duo)

Downloads from Sourceforge :

[![Download jd-gui-duo](https://img.shields.io/sourceforge/dt/jd-gui-duo.svg)](https://sourceforge.net/projects/jd-gui-duo/files/latest/download)
[![Download jd-gui-duo](https://img.shields.io/sourceforge/dd/jd-gui-duo.svg)](https://sourceforge.net/projects/jd-gui-duo/files/latest/download)
[![Download jd-gui-duo](https://img.shields.io/sourceforge/dw/jd-gui-duo.svg)](https://sourceforge.net/projects/jd-gui-duo/files/latest/download)
[![Download jd-gui-duo](https://img.shields.io/sourceforge/dm/jd-gui-duo.svg)](https://sourceforge.net/projects/jd-gui-duo/files/latest/download)

[![Download jd-gui-duo](https://a.fsdn.com/con/app/sf-download-button)](https://sourceforge.net/projects/jd-gui-duo/files/latest/download)


Looking for an Eclipse plugin ? Try [ECD++](https://github.com/nbauma109/ecd)

# jd-gui-duo
A 2-in-1 JAVA decompiler based on JD-CORE v0 and v1.

Initially a duo of decompilers were supported (JD-Core v0 & v1), but now other decompilers are supported with the [transformer-api](https://github.com/nbauma109/transformer-api) project.
JD-Core v0 and v1 are 2 different decompilers rather than 2 different versions of the same one. They use a different algorithm :
 - [JD-Core v0](https://github.com/nbauma109/jd-core-v0) uses byte code pattern matching like [JAD](http://www.kpdus.com/jad.html), the 1st Java decompiler
 - [JD-Core v1](https://github.com/nbauma109/jd-core) uses an analytical algorithm like [Fernflower](https://github.com/fesh0r/fernflower), the 1st analytical decompiler

This project is built on top of original [JD-GUI](https://github.com/java-decompiler/jd-gui) written by [Emmanuel Dupuy](https://github.com/emmanue1).

The binaries are built and hosted at jitpack.io (identifiable with rocket logo) and the download buttons below retrieve the distributions directly from Jitpack servers :

<a href="https://jitpack.io/com/github/nbauma109/jd-gui-duo/jd-gui-duo/2.0.107/jd-gui-duo-2.0.107-windows.tar.xz?" target="_blank">
  <img
    src="assets/badges/jitpack-download-windows.svg"
    width="200"
    height="56"
    alt="Download Windows archive">
</a>
<br>
<a href="https://jitpack.io/com/github/nbauma109/jd-gui-duo/jd-gui-duo/2.0.107/jd-gui-duo-2.0.107-linux.tar.xz?" target="_blank">
  <img
    src="assets/badges/jitpack-download-linux.svg"
    width="200"
    height="56"
    alt="Download Linux archive">
</a>
<br>
<a href="https://jitpack.io/com/github/nbauma109/jd-gui-duo/jd-gui-duo/2.0.107/jd-gui-duo-2.0.107-macos.tar.xz?" target="_blank">
  <img
    src="assets/badges/jitpack-download-macos.svg"
    width="200"
    height="56"
    alt="Download macOS archive">
</a>

# Feature tour

## Open Type

After opening a jar file, click 'Open Type' to decompile a class (tip: use $ to match the end of the class name)

![image](https://javadecompiler.org/jd-gui-duo/open-type.png)

# Outline tree

Use the outline tree to select decompiled methods :

![image](https://javadecompiler.org/jd-gui-duo/outline-tree.png)

## Maven central

In the Help -> Preferences menu, use search source code on [Maven Central](https://search.maven.org/) if you want to show the original source code, if it's available :

![image](https://javadecompiler.org/jd-gui-duo/maven-central.png)

![image](https://javadecompiler.org/jd-gui-duo/source-code-view.png)

## Maven central search and secured preferences

It is possible to do a maven central search behind a proxy or with a private Nexus repository.

Configure settings in Help -> Secured preferences.

![image](https://javadecompiler.org/jd-gui-duo/secured-preferences.png)

![image](https://javadecompiler.org/jd-gui-duo/secured-preferences-panel.png)

These preferences are protected by a master password that you choose after saving and which will be requested each time you re-open this panel.

You can do a maven search, with or without such settings, depending on when you need them or not :

![image](https://javadecompiler.org/jd-gui-duo/search-maven-central.png)

![image](https://javadecompiler.org/jd-gui-duo/repository-search.png)


## Search

Use the search button to do a search for methods, string constants,...

![image](https://javadecompiler.org/jd-gui-duo/search.png)

## Decompile all classes

Use File -> Save All Sources to decompile all sources into a sources jar (choose a destination directory where you have rights)

![image](https://javadecompiler.org/jd-gui-duo/save-all.png)

## Keyboard shortcuts

 - CTRL+F Find text

![image](https://javadecompiler.org/jd-gui-duo/find-text.png)
 
 - CTRL+L Goto line

![image](https://javadecompiler.org/jd-gui-duo/goto-line.png)

## Show byte code

Right-click on a method of the outline tree and select 'Show Byte Code' in the context menu.

![image](https://javadecompiler.org/jd-gui-duo/show-bytecode.png)

![image](https://javadecompiler.org/jd-gui-duo/bytecode-view.png)

To get a visual of the control flow graph that is used by JD-Core v1 for each of the decompilation steps, select one of the menus 'Show Control Flow Graph ...'.
The graph is built by [plantuml](https://plantuml.com/).

![image](https://javadecompiler.org/jd-gui-duo/show-cfg.png)

![image](https://javadecompiler.org/jd-gui-duo/cfg.png)


## Method patching

Sometimes JD-Core v1 fails to decompile a method. In this case, method will be patched from JD-Core v0 if possible and the comment 'Patched from JD-Core v0' will appear :

![image](https://javadecompiler.org/jd-gui-duo/method-patching.png)


## Comparison

A modified version of [JarComp](https://activityworkshop.net/software/jarcomp/index.html) is used to compare the jars and netbeans diff module for class comparison (see https://github.com/nbauma109/netbeans-visual-diff-standalone).

![image](https://javadecompiler.org/jd-gui-duo/compare.png)

Select the 2 files to compare or drag and drop files into the inputs :

![image](https://javadecompiler.org/jd-gui-duo/select-files.png)

The differences in size and CRC checksums are shown :

![image](https://javadecompiler.org/jd-gui-duo/jar-comparer.png)

Double-click on the row you want to compare :

![image](https://javadecompiler.org/jd-gui-duo/visual-diff.png)

Or alternatively, if the 2 jars are opened, you can open the type you want to compare :

![image](https://javadecompiler.org/jd-gui-duo/compare-popup.png)

If you select no, you will be asked select which file you want to open

![image](https://javadecompiler.org/jd-gui-duo/select-location.png)

## Hyperlinks

The navigable links are shown as underlined portions of text. Single click navigates to definition (no CTRL click, no double click).

## Select Decompiler

Choose another decompiler in the preferences window and the class will be decompiled with the newly selected decompiler as soon as you press OK.

![image](https://javadecompiler.org/jd-gui-duo/help-preferences.png)

![image](https://javadecompiler.org/jd-gui-duo/select-decompiler.png)

Click Configure button to choose decompiler settings (each decompiler has a its own panel) :

![image](https://javadecompiler.org/jd-gui-duo/configure-decompiler.png)

## Compiler

Compiler reports errors and warnings. Choose whether you want to report them in the Eclipse preferences page.

![image](https://javadecompiler.org/jd-gui-duo/help-eclipse-preferences.png)

![image](https://javadecompiler.org/jd-gui-duo/show-error-warn-info.png)

![image](https://javadecompiler.org/jd-gui-duo/warning.png)

## Advanced class lookup

When this option is selected, the jars located in the same directory and the jmods (or rt.jar) of JAVA_HOME will be used by decompilers for better type resolution.
Currently, the decompilers supporting this are CFR, JD-Core v1, Procyon.
For optimal results, setup your JAVA_HOME to the same JDK that was used to build the jar you want to decompile (if any doubt, check the META-INF/MANIFEST.MF inside the jar).
The running JRE of jd-gui-duo does not interfere.
This option does not support jars in war.

## JRE System Library

This option applies to the Eclipse AST parser, which is used in various contexts to provide :
  - hyperlinks to navigate into source files (*.java)
  - indexing for search into source files (*.java)
  - compiler errors and warnings in decompiled sources (*.class) and in sources (*.java)

By default, it uses the running VM of jd-gui-duo, but you can choose another JRE system library by unticking "Include running VM boot classpath".
You can also choose a compatible Source and Compliance.

![image](https://javadecompiler.org/jd-gui-duo/eclipse-preferences-panel.png)

## Realign line numbers

This is a known option from the original JD-GUI that enables to align code for debugging if the debugging information is present in the class files.
As of now, this is only supported by JD-Core v0 and v1.
If the code is misaligned, the numbers will appear in red.

![image](https://javadecompiler.org/jd-gui-duo/realign.png)

![image](https://javadecompiler.org/jd-gui-duo/misaligned.png)

## Remove unnecessary casts

This option uses the same feature as Eclipse to cleanup unnecessary casts.

## Dark mode

![image](https://javadecompiler.org/jd-gui-duo/dark-mode.png)

## Quick outline

Quick outline (CTRL+SHIFT+O) is a feature which was present in the C++ versions 0.3.x of jd-gui and is now present in jd-gui-duo.
It now includes "filter as you type" feature.

![image](https://javadecompiler.org/jd-gui-duo/quick-outline.png)

### For memory, jd-gui 0.3.6 :

![image](https://javadecompiler.org/jd-gui-duo/quick-outline-old.png)


# Credits

Decompiler|Author|Link|License
--- | --- | --- | ---
CFR|Lee Benfield|https://github.com/leibnitz27/cfr|MIT
Procyon|Mike Strobel|https://github.com/mstrobel/procyon|Apache v2
Fernflower|Jetbrains|https://github.com/JetBrains/intellij-community/tree/master/plugins/java-decompiler/engine|Apache v2
Vineflower|Vineflower|https://github.com/Vineflower/vineflower|Apache v2
JADX|Skylot|https://github.com/skylot/jadx|Apache v2
JD-GUI|Emmanuel Dupuy|https://github.com/java-decompiler/jd-gui|GPL v3
