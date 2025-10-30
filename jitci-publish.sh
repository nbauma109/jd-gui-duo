#!/usr/bin/env bash
export JAVA_HOME=~/.sdkman/candidates/java/current
./mvnw -B deploy -DskipTests -Dfindbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true -Dsigning.disabled=true -DaltDeploymentRepository=jitci::file:///home/jitpack/deploy --no-transfer-progress
