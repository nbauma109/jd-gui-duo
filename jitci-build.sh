#!/usr/bin/env bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./mvnw -B install -DskipTests -Dsigning.disabled=true --no-transfer-progress
