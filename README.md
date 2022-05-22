[![](https://jitpack.io/v/nbauma109/jd-gui-duo.svg)](https://jitpack.io/#nbauma109/jd-gui-duo)
[![](https://jitci.com/gh/nbauma109/jd-gui-duo/svg)](https://jitci.com/gh/nbauma109/jd-gui-duo)
[![CodeQL](https://github.com/nbauma109/jd-gui-duo/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/codeql-analysis.yml)
[![Maven Release](https://github.com/nbauma109/jd-gui-duo/actions/workflows/maven.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/maven.yml)
[![Github Release](https://github.com/nbauma109/jd-gui-duo/actions/workflows/release.yml/badge.svg)](https://github.com/nbauma109/jd-gui-duo/actions/workflows/release.yml)

# jd-gui-duo
A 2-in-1 JAVA decompiler based on JD-CORE v0 and v1 [![image](https://user-images.githubusercontent.com/9403560/156565769-51264b92-4850-46c1-ad33-a4211a4c89ec.png)](https://jitpack.io/com/github/nbauma109/jd-gui-duo/jd-gui-duo/master-SNAPSHOT/jd-gui-duo-master-SNAPSHOT.zip)

Initially a duo of decompilers were supported (JD-Core v0 & v1), but now other decompilers are supported with the transformer-api project.
JD-Core v0 and v1 are 2 different decompilers rather than 2 different versions of the same one. They use a different algoithm :
 - JD-Core v0 uses byte code pattern matching like JAD, the 1st Java decompiler
 - JD-Core v1 uses an analytical algorithm like Fernflower, the 1st analytical decompiler

# Feature tour

After opening a jar file, click 'Open Type' to decompile a class (tip: use $ to match the end of the class name)

![image](https://user-images.githubusercontent.com/9403560/169690563-32909189-d748-4978-a2c7-acc5da2cadf4.png)

Use the outline tree to select decompiled methods :

![image](https://user-images.githubusercontent.com/9403560/169690314-24cc1cad-9beb-44c4-b9fe-e9bc909054e2.png)

In the Help -> Preferences menu, use search source code on maven.org if you want to show the original source code, if it's available :

![image](https://user-images.githubusercontent.com/9403560/169690664-6a3e1a40-dda9-4f3f-9eac-515a24ae9e65.png)

![image](https://user-images.githubusercontent.com/9403560/169690709-3d191968-69bf-4323-acd2-387541012a5d.png)

Use the search button to do a search for methods, string constants,...

![image](https://user-images.githubusercontent.com/9403560/169690812-1cde4346-0d08-41d9-b321-280a81727a31.png)

Use File -> Save All Sources to decompile all sources into a sources jar (choose a destination directory where you have rights)

![image](https://user-images.githubusercontent.com/9403560/169691065-a7a56b8c-5949-412f-a855-816eff1aca71.png)

Available keyboard shortcuts :
 - CTRL+F Find text
 - CTRL+L Goto line
![image](https://user-images.githubusercontent.com/9403560/169691345-622a15dc-8ef8-4470-9ebb-1d5f86f126b2.png)

Show byte code :

![image](https://user-images.githubusercontent.com/9403560/169691669-8672828a-3cc0-4622-a083-4f36159b9463.png)

![image](https://user-images.githubusercontent.com/9403560/169691698-65e13ee6-5cde-41d5-b924-58f1a36abda7.png)


Method patching :

Sometimes JD-Core v1 fails to compile a method. In this case, method will be patched from JD-Core v0 if possible and the comment 'Patched from JD-Core v0' will appear :

![image](https://user-images.githubusercontent.com/9403560/169692097-4f96d304-4bac-4596-a3bf-076ae49b8670.png)






