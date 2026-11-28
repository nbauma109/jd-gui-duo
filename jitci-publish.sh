#!/usr/bin/env bash
export JAVA_HOME=~/.sdkman/candidates/java/current
export MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2
./mvnw -B -s .ci/settings.xml deploy -DskipTests -Dfindbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true -Dsigning.disabled=true -DaltDeploymentRepository=jitci::file:///home/jitpack/deploy --no-transfer-progress
