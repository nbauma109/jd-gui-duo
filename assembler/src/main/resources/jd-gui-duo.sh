#!/bin/bash
jre/bin/java -ea \
  --add-opens java.base/java.net=ALL-UNNAMED \
  --add-opens java.desktop/javax.swing.plaf.basic=ALL-UNNAMED \
  --add-opens java.desktop/javax.swing.text=ALL-UNNAMED \
  --add-opens java.prefs/java.util.prefs=ALL-UNNAMED \
  --add-opens java.base/java.lang.ref=ALL-UNNAMED \
  -cp "lib/*" org.jd.gui.App "$@"
