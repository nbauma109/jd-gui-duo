#!/usr/bin/env bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./mvnw -B -e -X install -DskipTests -Dsigning.disabled=true
