#!/bin/sh
set -eu

input_archive="$1"
output_zip="$2"
staging_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$staging_dir"
}
trap cleanup EXIT

tar -xJf "$input_archive" -C "$staging_dir"
rm -f "$output_zip"
(cd "$staging_dir" && ditto -c -k --sequesterRsrc --keepParent jd-gui-duo.app "$output_zip")
