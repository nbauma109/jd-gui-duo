#!/usr/bin/env bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./mvn -B test --no-transfer-progress
