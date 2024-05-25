#!/usr/bin/env bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./mvnw -B test --no-transfer-progress
