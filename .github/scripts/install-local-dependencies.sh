#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root after downloading the pinned JARs.
# Hash-qualified versions prevent different private JARs sharing a Maven cache key.
sha256sum --check .github/dependencies.sha256

mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MMOCore-1.13.1-SNAPSHOT.jar" -DgroupId="local" -DartifactId="MMOCore" \
    -Dversion="1.13.1-SNAPSHOT-tfmc-81d511d08309" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MythicLib-1.7.1-SNAPSHOT.jar" -DgroupId="local" -DartifactId="MythicLib" \
    -Dversion="1.7.1-SNAPSHOT-tfmc-225aa7f75d4e" -Dpackaging=jar -DgeneratePom=true "$@"
