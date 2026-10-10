#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root after downloading the pinned JARs.
# Hash-qualified versions prevent different private JARs sharing a Maven cache key.
sha256sum --check .github/dependencies.sha256

mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MMOItems-6.10.1-SNAPSHOT.jar" -DgroupId="local" -DartifactId="MMOItems" \
    -Dversion="6.10.1-SNAPSHOT-tfmc-a37f7789fcdc" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/MythicLib-1.7.1-SNAPSHOT.jar" -DgroupId="local" -DartifactId="MythicLib" \
    -Dversion="1.7.1-SNAPSHOT-tfmc-225aa7f75d4e" -Dpackaging=jar -DgeneratePom=true "$@"
mvn -B --no-transfer-progress org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    -Dfile="libs/json-simple-1.1.1.jar" -DgroupId="local" -DartifactId="json-simple" \
    -Dversion="1.1.1-tfmc-4e69696892b8" -Dpackaging=jar -DgeneratePom=true "$@"
