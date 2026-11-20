#!/bin/bash
# Registers JD-GUI Duo with the desktop: an application menu entry and the "Open With" association for Java
# archives (.jar .war .ear .aar .jmod .kar) and class files. Per user, no root needed.
#
#   install-desktop-integration.sh               install (menu entry, file types, icon)
#   install-desktop-integration.sh --set-default also make JD-GUI Duo the default application for those files
#   install-desktop-integration.sh --uninstall   remove everything this script installed
#
# XDG_DATA_HOME (default ~/.local/share) selects where the files go.
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -n "$XDG_DATA_HOME" ]; then DATA="$XDG_DATA_HOME"; else DATA="$HOME/.local/share"; fi
APPS="$DATA/applications"
MIME="$DATA/mime"
ICONS="$DATA/icons/hicolor/128x128/apps"
DESKTOP="$APPS/jd-gui-duo.desktop"
TYPES="application/java-archive application/x-java-archive application/x-java-applet application/x-java-war-archive application/x-java-enterprise-archive application/x-java-android-library application/x-java-module application/x-java-karaf-archive"

refresh() {
  if command -v update-mime-database > /dev/null 2>&1; then update-mime-database "$MIME" > /dev/null 2>&1 || true; fi
  if command -v update-desktop-database > /dev/null 2>&1; then update-desktop-database "$APPS" > /dev/null 2>&1 || true; fi
  if command -v gtk-update-icon-cache > /dev/null 2>&1; then gtk-update-icon-cache -q -t "$DATA/icons/hicolor" > /dev/null 2>&1 || true; fi
}

case "$1" in
  --uninstall)
    rm -f "$DESKTOP" "$MIME/packages/jd-gui-duo.xml" "$ICONS/jd-gui-duo.png"
    refresh
    echo "JD-GUI Duo desktop integration removed"
    ;;
  ""|--set-default)
    mkdir -p "$APPS" "$MIME/packages" "$ICONS"
    cp "$DIR/jd-gui-duo.png" "$ICONS/jd-gui-duo.png"
    cp "$DIR/jd-gui-duo-mime.xml" "$MIME/packages/jd-gui-duo.xml"
    sed -e "s|@INSTALL_DIR@|$DIR|g" -e "s|@ICON@|jd-gui-duo|g" "$DIR/jd-gui-duo.desktop.template" > "$DESKTOP"
    refresh
    if [ "$1" = "--set-default" ] && command -v xdg-mime > /dev/null 2>&1; then
      for type in $TYPES; do xdg-mime default jd-gui-duo.desktop "$type"; done
    fi
    echo "JD-GUI Duo registered: $DESKTOP"
    ;;
  *)
    echo "Usage: $0 [--set-default | --uninstall]" >&2
    exit 1
    ;;
esac
