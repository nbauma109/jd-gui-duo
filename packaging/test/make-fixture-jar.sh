#!/usr/bin/env bash
# Builds a tiny jar (one class) used to check that a packaged application can open a file.
# Usage: make-fixture-jar.sh <output-dir>      (needs a JDK on the PATH; prints the jar path)
set -euo pipefail
out="$1"
mkdir -p "$out/src/demo" "$out/classes"
cat > "$out/src/demo/PackagedSmoke.java" <<'JAVA'
package demo;

public class PackagedSmoke {
    public String hello() {
        return "Hello from a packaged application test";
    }
}
JAVA
javac -g -d "$out/classes" "$out/src/demo/PackagedSmoke.java"
jar cf "$out/packaged-smoke.jar" -C "$out/classes" .
echo "$out/packaged-smoke.jar"
