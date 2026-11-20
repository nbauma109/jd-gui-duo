#!/usr/bin/env bash
# Writes a throw-away jd-gui.cfg so the packaged application under test never touches the user's own configuration.
# The application reads it thanks to -Djd-gui.cfg=<file>, passed through JAVA_TOOL_OPTIONS.
# Usage: isolated-config.sh <dir>      (prints the JAVA_TOOL_OPTIONS value to export)
set -euo pipefail
dir="$1"
mkdir -p "$dir"
cat > "$dir/jd-gui.cfg" <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <gui>
    <lookAndFeel>com.formdev.flatlaf.FlatLightLaf</lookAndFeel>
  </gui>
</configuration>
XML
echo "-Djd-gui.cfg=$dir/jd-gui.cfg"
