#!/usr/bin/env bash
# Builds and tests both plugins in one run.
#   1. IntelliJ plugin and shared core: Gradle (tests, plugin zip)
#   2. Eclipse plugin: Maven + Tycho (tests, bundle, update site zip)
# Stops at the first failure. Needs JDK 21 and Maven 3.9.x on the PATH for step 2.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

if ! command -v mvn >/dev/null 2>&1; then
    echo "mvn not found. Install Maven 3.9.x and JDK 21 first (see eclipse/README.md)." >&2
    exit 1
fi

echo "==> IntelliJ plugin and shared core (Gradle)"
"$ROOT/gradlew" test buildPlugin

echo "==> Eclipse plugin (Maven + Tycho)"
mvn -f "$ROOT/eclipse/pom.xml" clean package

echo
echo "Done."
echo "IntelliJ plugin zip: $(ls "$ROOT"/build/distributions/*.zip 2>/dev/null | head -1)"
echo "Eclipse update site: $ROOT/eclipse/site/target/damu.plugin.oneclickapi.eclipse.site-2.0.0-SNAPSHOT.zip"
