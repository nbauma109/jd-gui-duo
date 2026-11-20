#!/usr/bin/env bash
# Smoke-tests the packaged macOS application bundle: unpacks the tar.xz into a folder whose name contains
# spaces, validates the bundle (Info.plist, launcher, runtime), starts it on a jar through LaunchServices
# (like Finder does) and checks that it shows a window.
# Usage: test-packaged.sh <jd-gui-duo-macos-*.tar.xz> <fixture.jar> [screenshot-dir]
set -euo pipefail
archive="$(realpath "$1")"
fixture="$(realpath "$2")"
shots="${3:-$PWD/packaging-shots}"
mkdir -p "$shots"

work="$(mktemp -d)"
root="$work/folder with spaces"
mkdir -p "$root"
trap 'pkill -f "$root/jd-gui-duo.app" 2>/dev/null || true; rm -rf "$work"' EXIT

tar -xJf "$archive" -C "$root"
app="$root/jd-gui-duo.app"

echo "--- bundle structure"
[ -d "$app/Contents" ] || { echo "No jd-gui-duo.app bundle in the package"; exit 1; }
plutil -lint "$app/Contents/Info.plist"
for key in CFBundleName CFBundleIdentifier CFBundleVersion CFBundleIconFile CFBundlePackageType; do
  /usr/libexec/PlistBuddy -c "Print :$key" "$app/Contents/Info.plist" > /dev/null || { echo "Info.plist lacks $key"; exit 1; }
done
[ -f "$app/Contents/Resources/JDGUI.icns" ] || { echo "The icon is missing"; exit 1; }
[ -x "$app/Contents/MacOS/jd-gui-duo" ] || { echo "The launcher is not executable"; exit 1; }
[ -x "$app/Contents/runtime/bin/java" ] || { echo "The bundled runtime is missing"; exit 1; }
ls "$app"/Contents/lib/jd-gui-duo-app*.jar > /dev/null

echo "--- file associations (CFBundleDocumentTypes)"
plist="$app/Contents/Info.plist"
extensions="$(/usr/libexec/PlistBuddy -c "Print :CFBundleDocumentTypes" "$plist")" \
  || { echo "Info.plist declares no document types: Finder cannot offer the application for any file"; exit 1; }
for extension in jar war ear aar jmod kar class; do
  echo "$extensions" | grep -qw "$extension" || { echo "No document type for .$extension"; exit 1; }
done
echo "$extensions" | grep -q "Alternate" || { echo "The document types should be an alternate handler (not take over as default)"; exit 1; }

# The x64 runtime of the bundle needs Rosetta on Apple silicon
if [ "$(uname -m)" = "arm64" ] && ! /usr/bin/pgrep -q oahd; then
  echo "Rosetta is not running; the x64 bundle cannot be started on this machine"; exit 2
fi
"$app/Contents/runtime/bin/java" -version

config_opts="$("$(dirname "$0")/../test/isolated-config.sh" "$work/config")"

# CoreGraphics lists the windows of every process. The owner (the bundle name) is always readable; the
# title only with the Screen Recording permission, so it is checked when available and not required.
cat > "$work/windows.swift" <<'SWIFT'
import CoreGraphics
let list = CGWindowListCopyWindowInfo([.optionOnScreenOnly], kCGNullWindowID) as? [[String: Any]] ?? []
for w in list where (w[kCGWindowLayer as String] as? Int) == 0 {
    let owner = (w[kCGWindowOwnerName as String] as? String) ?? ""
    let title = (w[kCGWindowName as String] as? String) ?? ""
    if owner.lowercased().contains("jd-gui") || owner.lowercased().contains("java") {
        print("yes \(owner) | \(title)")
        break
    }
}
SWIFT

# Waits for the application window; when the title is readable it must name the opened file.
wait_for_window() {
  for i in $(seq 1 "$1"); do
    found="$(swift "$work/windows.swift" 2>/dev/null || true)"
    if [ -n "$found" ] && { [ -z "${found#*| }" ] || [[ "$found" == *"packaged-smoke.jar"* ]]; }; then
      echo "The application window is on screen: ${found#yes }"
      return 0
    fi
    sleep 1
  done
  return 1
}

echo "--- launch through LaunchServices, file as an argument"
open -n -a "$app" --env "JAVA_TOOL_OPTIONS=$config_opts" --args "$fixture"
if wait_for_window 60; then
  screencapture -x "$shots/macos-packaged.png" || true
else
  echo "No window appeared"
  screencapture -x "$shots/macos-packaged-failure.png" || true
  exit 1
fi
pkill -f "$root/jd-gui-duo.app" 2>/dev/null || true
for i in $(seq 1 20); do [ -z "$(swift "$work/windows.swift" 2>/dev/null || true)" ] && break; sleep 1; done

# Finder hands the file to the application as an "open document" event ("Open With", double-click), not as an
# argument. `open -a app file` goes through the same LaunchServices path as Finder, so this is a hard check
# that the shell-script launcher of the bundle receives these events.
echo "--- launch through LaunchServices, file as an 'open document' event (Finder 'Open With')"
open -n -a "$app" --env "JAVA_TOOL_OPTIONS=$config_opts" "$fixture"
if wait_for_window 40 && [[ "$(swift "$work/windows.swift" 2>/dev/null)" == *"packaged-smoke.jar"* ]]; then
  echo "The open-document event opened the file"
else
  echo "The application did not open the file passed as an open-document event (Finder 'Open With')"
  screencapture -x "$shots/macos-open-document-failure.png" || true
  exit 1
fi
pkill -f "$root/jd-gui-duo.app" 2>/dev/null || true
