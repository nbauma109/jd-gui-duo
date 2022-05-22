[![](https://jitpack.io/v/nbauma109/jd-gui-duo.svg)](https://jitpack.io/#nbauma109/jd-gui-duo)
[![](https://jitci.com/gh/nbauma109/jd-gui-duo/svg)](https://jitci.com/gh/nbauma109/jd-gui-duo)
[![CodeQL](https://github.com/nbauma109/jd-gui-duo/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/codeql-analysis.yml)
[![Maven Release](https://github.com/nbauma109/jd-gui-duo/actions/workflows/maven.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/maven.yml)
[![Github Release](https://github.com/nbauma109/jd-gui-duo/actions/workflows/release.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/release.yml)

# jd-gui-duo
A 2-in-1 JAVA decompiler based on JD-CORE v0 and v1 [![image](https://user-images.githubusercontent.com/9403560/156565769-51264b92-4850-46c1-ad33-a4211a4c89ec.png)](https://jitpack.io/com/github/nbauma109/jd-gui-duo/jd-gui-duo/master-SNAPSHOT/jd-gui-duo-master-SNAPSHOT.zip)

Initially a duo of decompilers were supported (JD-Core v0 & v1), but now other decompilers are supported with the [transformer-api](https://github.com/nbauma109/transformer-api) project.
JD-Core v0 and v1 are 2 different decompilers rather than 2 different versions of the same one. They use a different algorithm :
 - [JD-Core v0](https://github.com/nbauma109/jd-core-v0) uses byte code pattern matching like [JAD](http://www.kpdus.com/jad.html), the 1st Java decompiler
 - [JD-Core v1](https://github.com/nbauma109/jd-core) uses an analytical algorithm like [Fernflower](https://github.com/fesh0r/fernflower), the 1st analytical decompiler

# Feature tour

## Open Type

After opening a jar file, click 'Open Type' to decompile a class (tip: use $ to match the end of the class name)

![image](https://user-images.githubusercontent.com/9403560/169690563-32909189-d748-4978-a2c7-acc5da2cadf4.png)

# Outline tree

Use the outline tree to select decompiled methods :

![image](https://user-images.githubusercontent.com/9403560/169690314-24cc1cad-9beb-44c4-b9fe-e9bc909054e2.png)

## Maven central

In the Help -> Preferences menu, use search source code on [Maven Central](https://search.maven.org/) if you want to show the original source code, if it's available :

![image](https://user-images.githubusercontent.com/9403560/169690664-6a3e1a40-dda9-4f3f-9eac-515a24ae9e65.png)

![image](https://user-images.githubusercontent.com/9403560/169690709-3d191968-69bf-4323-acd2-387541012a5d.png)

## Search

Use the search button to do a search for methods, string constants,...

![image](https://user-images.githubusercontent.com/9403560/169690812-1cde4346-0d08-41d9-b321-280a81727a31.png)

## Decompile all classes

Use File -> Save All Sources to decompile all sources into a sources jar (choose a destination directory where you have rights)

![image](https://user-images.githubusercontent.com/9403560/169691065-a7a56b8c-5949-412f-a855-816eff1aca71.png)

## Keyboard shortcuts

 - CTRL+F Find text
 - CTRL+L Goto line

![image](https://user-images.githubusercontent.com/9403560/169691345-622a15dc-8ef8-4470-9ebb-1d5f86f126b2.png)

## Show byte code

Right-click on a method of the outline tree and select 'Show Byte Code' in the context menu.

![image](https://user-images.githubusercontent.com/9403560/169691669-8672828a-3cc0-4622-a083-4f36159b9463.png)

![image](https://user-images.githubusercontent.com/9403560/169691698-65e13ee6-5cde-41d5-b924-58f1a36abda7.png)

To get a visual of the control flow graph that is used by JD-Core v1 for each of the decompilation steps, select one of the menus 'Show Control Flow Graph ...'.
The graph is built by [plantuml](https://plantuml.com/).

![image](https://user-images.githubusercontent.com/9403560/169707683-bfbf0aed-78f1-4f70-91bb-0a4d98ed08ba.png)

![image](https://user-images.githubusercontent.com/9403560/169707573-9cc0d318-e6c3-47b0-8f37-eb70df1e0b3e.png)


## Method patching

Sometimes JD-Core v1 fails to decompile a method. In this case, method will be patched from JD-Core v0 if possible and the comment 'Patched from JD-Core v0' will appear :

![image](https://user-images.githubusercontent.com/9403560/169692097-4f96d304-4bac-4596-a3bf-076ae49b8670.png)


## Comparison

A modified version of [JarComp](https://activityworkshop.net/software/jarcomp/index.html) is used to compare the jars

![image](https://user-images.githubusercontent.com/9403560/169692577-1d14cacc-71b0-458c-ad5c-a8686ae2fb95.png)

Select the 1st file to compare and then another file chooser will appear to select the 2nd file to compare :

![image](https://user-images.githubusercontent.com/9403560/169694123-a070e409-6eec-45f5-866f-cdbc06027cc0.png)
![image](https://user-images.githubusercontent.com/9403560/169694196-0fa04413-9cf1-4255-8ad0-163776c42eec.png)

The differences in size and CRC checksums are shown :

![image](https://user-images.githubusercontent.com/9403560/169694899-a4f57fcf-95aa-4481-8351-99827d544625.png)

Double-click on the row you want to compare :

![image](https://user-images.githubusercontent.com/9403560/169694362-3c760435-6a4a-46c3-8944-941cea481033.png)

Or alternatively, if the 2 jars are opened, you can open the type you want to compare :

![image](https://user-images.githubusercontent.com/9403560/169694513-8e8ebf31-c0c9-4235-879d-94b2dc5799e8.png)

If you select no, you will be asked select which file you want to open

![image](https://user-images.githubusercontent.com/9403560/169694563-ac77422d-3a1b-416a-92ba-c916206bbfbd.png)

## Hyperlinks

The navigable links are shown as underlined portions of text. Single click navigates to definition (no CTRL click, no double click).

## Select Decompiler

Choose another decompiler in the preferences window and the class will be decompiled with the newly selected decompiler as soon as you press OK.

![image](https://user-images.githubusercontent.com/9403560/169696132-fe35d1b2-b0e3-48a3-9023-831d6fcf49fe.png)

## Compiler

Compiler reports errors and warnings. Choose whether you want to report them in the preferences page.

![image](https://user-images.githubusercontent.com/9403560/169695981-73198acd-7962-47ba-a540-23a872a6a862.png)

![image](https://user-images.githubusercontent.com/9403560/169695797-2a97058e-1768-42aa-8cd4-34cd1f2f2043.png)

## Advanced class lookup

When this option is selected, the jars located in the same directory and the jmods (or rt.jar) of JAVA_HOME will be used by decompilers for better type resolution.
Currently, the decompilers supporting this are CFR, JD-Core v1, Procyon.
This option applies to the Eclipse AST parser too, to provide better hyperlinks.
For optimal results, setup your JAVA_HOME to the same JDK that was used to build the jar you want to decompile (if any doubt, check the META-INF/MANIFEST.MF inside the jar).
The running JRE of jd-gui-duo does not interfere.

## Realign line numbers

This is a known option from the original JD-GUI that enables to align code for debugging if the debugging information is present in the class files.
As of now, this is only supported by JD-Core v0 and v1.
If the code is misaligned, the numbers will appear in red.

![image](https://user-images.githubusercontent.com/9403560/169709845-e9da03fe-5fce-4014-aa4d-56c288f0d864.png)

## Remove unnecessary casts

This option uses the same feature as Eclipse to cleanup unnecessary casts.
