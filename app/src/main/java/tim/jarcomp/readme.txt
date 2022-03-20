Jarcomp version 2
=================

Jarcomp is a simple tool for comparing jar files or zip files.
Full details can be found at http://activityworkshop.net/software/jarcomp/

Jarcomp is copyright activityworkshop.net and distributed under the terms of the Gnu GPL version 2.
You may freely use the software, and may help others to freely use it too.  For further information
on your rights and how they are protected, see the included license.txt file.

Jarcomp comes without warranty and without guarantee - the authors cannot be held responsible for
losses incurred through use of the program, however caused.


Running
=======

To run Jarcomp from the jar file, simply call it from a command prompt or shell:
   java -jar jarcomp_02.jar

If the jar file is saved in a different directory, you will need to include the path.
Depending on your system settings, you may be able to click or double-click on the jar file
in a file manager window to execute it.  A shortcut, menu item, alias, desktop icon
or other link can of course be made should you wish.

To specify two files to compare, simply add them to the command line, eg:
   java -jar jarcomp_02.jar file1.jar file2.jar


What's new with version 2
===============================

* Fixed bug affecting the title of the second file selection dialog (thanks Greg!)
* Added yellow row highlighting for jar entries which are different
* Fixed the sort order when sorting by the "size change" column
* Added spacing around the summary labels
* Added tooltips to the "Path" labels in case the paths are too long to display


Further information and updates
===============================

To obtain the source code (if it wasn't included in your jar file), or for further information,
please visit the website:  http://activityworkshop.net/
