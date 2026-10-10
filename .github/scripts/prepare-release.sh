#!/usr/bin/env bash
set -euo pipefail
: "${GH_TOKEN:?Set DEPS_TOKEN with Contents read access to TF-Minecraft/ServerAssets}"
ref=4b80431398e4ff35d703cad915b7ee4e5924a763
mkdir -p libs
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/a37f7789fcdc/MMOItems-6.10.1-SNAPSHOT.jar?ref=$ref" > "libs/MMOItems-6.10.1-SNAPSHOT.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/225aa7f75d4e/MythicLib-1.7.1-SNAPSHOT.jar?ref=$ref" > "libs/MythicLib-1.7.1-SNAPSHOT.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/4e69696892b8/json-simple-1.1.1.jar?ref=$ref" > "libs/json-simple-1.1.1.jar"
bash .github/scripts/install-local-dependencies.sh "$@"
