#!/usr/bin/env bash
. ~/.sdkman/bin/sdkman-init.sh
sdk install java 17.0.11-oracle
sdk use java 17.0.11-oracle
export JAVA_HOME=~/.sdkman/candidates/java/current
export M3_VERSION=$(curl -Ls -o /dev/null -w %{url_effective} https://github.com/apache/maven/releases/latest | sed 's,https://github.com/apache/maven/releases/tag/maven-,,g')
mvn wrapper:wrapper -Dmaven=${M3_VERSION} --no-transfer-progress
./mvnw -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies --no-transfer-progress
