#!/usr/bin/env bash
set -euo pipefail
: "${GH_TOKEN:?Set DEPS_TOKEN with Contents read access to TF-Minecraft/ServerAssets}"
ref=4b80431398e4ff35d703cad915b7ee4e5924a763
mkdir -p libs
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/81d511d08309/MMOCore-1.13.1-SNAPSHOT.jar?ref=$ref" > "libs/MMOCore-1.13.1-SNAPSHOT.jar"
curl --fail --location --silent --show-error --retry 3 -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github.raw+json" "https://api.github.com/repos/TF-Minecraft/ServerAssets/contents/jars/225aa7f75d4e/MythicLib-1.7.1-SNAPSHOT.jar?ref=$ref" > "libs/MythicLib-1.7.1-SNAPSHOT.jar"
bash .github/scripts/install-local-dependencies.sh "$@"
