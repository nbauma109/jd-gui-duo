#!/usr/bin/env bash
# Smoke-tests the packaged Linux application: unpacks the tar.xz into a folder whose name contains spaces,
# starts jd-gui-duo.sh on a jar, and checks that the main window shows up with the file in its title.
# Also checks the desktop integration (menu entry, file types, "Open With" association) in a sandboxed
# XDG_DATA_HOME. Needs a running X server (DISPLAY), xdotool and ImageMagick (for the screenshot);
# desktop-file-utils and xdg-utils are used when installed.
# Usage: test-packaged.sh <jd-gui-duo-linux-*.tar.xz> <fixture.jar> [screenshot-dir]
set -euo pipefail
archive="$(realpath "$1")"
fixture="$(realpath "$2")"
shots="${3:-$PWD/packaging-shots}"
mkdir -p "$shots"

work="$(mktemp -d)"
root="$work/folder with spaces/JD GUI"
mkdir -p "$root"
trap '[ -z "${pid:-}" ] || kill "$pid" 2>/dev/null || true; rm -rf "$work"' EXIT

tar -xJf "$archive" -C "$root"
for required in jd-gui-duo.sh jre/bin/java lib install-desktop-integration.sh jd-gui-duo.desktop.template jd-gui-duo-mime.xml jd-gui-duo.png; do
  [ -e "$root/$required" ] || { echo "Missing $required in the package"; exit 1; }
done
[ -x "$root/jd-gui-duo.sh" ] || { echo "jd-gui-duo.sh is not executable"; exit 1; }
"$root/jre/bin/java" -version

export JAVA_TOOL_OPTIONS="$("$(dirname "$0")/../test/isolated-config.sh" "$work/config")"
title="packaged-smoke.jar - Java Decompiler"

wait_for_window() {
  for i in $(seq 1 60); do
    if xdotool search --name "$title" > /dev/null 2>&1; then
      echo "Main window found: $title"
      return 0
    fi
    kill -0 "$pid" 2>/dev/null || { echo "The application exited early:"; cat "$work/app.log"; return 1; }
    sleep 1
  done
  echo "No window titled '$title' appeared:"; cat "$work/app.log"
  return 1
}

echo "--- launch the script on a jar"
"$root/jd-gui-duo.sh" "$fixture" > "$work/app.log" 2>&1 &
pid=$!
wait_for_window || { import -window root "$shots/linux-packaged-failure.png" || true; exit 1; }
import -window root "$shots/linux-packaged.png" || true
stop_application() {
  # the launcher execs java, so $pid is the application itself
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  for i in $(seq 1 30); do
    xdotool search --name "$title" > /dev/null 2>&1 || return 0
    sleep 1
  done
  echo "The application window is still there after stopping the application"; exit 1
}
stop_application

echo "--- desktop integration"
export XDG_DATA_HOME="$work/xdg-data"
export XDG_CONFIG_HOME="$work/xdg-config"   # xdg-mime default writes mimeapps.list here: never the user's own
"$root/install-desktop-integration.sh"
desktop="$XDG_DATA_HOME/applications/jd-gui-duo.desktop"
[ -f "$desktop" ] || { echo "The menu entry was not installed"; exit 1; }
[ -f "$XDG_DATA_HOME/mime/packages/jd-gui-duo.xml" ] || { echo "The file types were not installed"; exit 1; }
[ -f "$XDG_DATA_HOME/icons/hicolor/128x128/apps/jd-gui-duo.png" ] || { echo "The icon was not installed"; exit 1; }
cat "$desktop"
grep -q "@INSTALL_DIR@\|@ICON@" "$desktop" && { echo "The desktop entry still has placeholders"; exit 1; }
grep -q "^MimeType=.*application/java-archive" "$desktop" || { echo "The desktop entry does not handle jar files"; exit 1; }
if command -v desktop-file-validate > /dev/null 2>&1; then desktop-file-validate "$desktop"; fi
if command -v update-mime-database > /dev/null 2>&1; then
  # xdg-mime may fall back to libmagic outside a desktop session, so read the generated database itself
  grep -q "application/x-java-war-archive:\*.war" "$XDG_DATA_HOME/mime/globs2" \
    || { echo "The .war file type was not registered in the mime database"; cat "$XDG_DATA_HOME/mime/globs2" 2>&1; exit 1; }
fi

# Run the Exec line of the entry, as the file manager does for "Open With" (%F = the files)
exec_line="$(sed -n 's/^Exec=//p' "$desktop")"
echo "Exec: $exec_line"
launcher="$(printf '%s' "$exec_line" | sed -e 's/^"\(.*\)" *%F$/\1/')"
[ -x "$launcher" ] || { echo "The Exec line does not point to an executable launcher: $launcher"; exit 1; }
"$launcher" "$fixture" > "$work/app2.log" 2>&1 &
pid=$!
wait_for_window || exit 1
stop_application

if command -v xdg-mime > /dev/null 2>&1; then
  "$root/install-desktop-integration.sh" --set-default
  [ "$(xdg-mime query default application/java-archive)" = "jd-gui-duo.desktop" ] \
    || { echo "jd-gui-duo.desktop is not the default for jar files"; exit 1; }
fi

"$root/install-desktop-integration.sh" --uninstall
[ ! -e "$desktop" ] && [ ! -e "$XDG_DATA_HOME/mime/packages/jd-gui-duo.xml" ] || { echo "Uninstalling left files behind"; exit 1; }
echo "Packaged Linux application: all checks passed"
