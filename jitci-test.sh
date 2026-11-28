#!/usr/bin/env bash
export JAVA_HOME=~/.sdkman/candidates/java/current
export MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2
./mvnw -B -s .ci/settings.xml test --no-transfer-progress
