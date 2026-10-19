#!/usr/bin/env bash
# Builds and tests both plugins in one run.
#   1. IntelliJ plugin and shared core: Gradle (tests, plugin zip)
#   2. Eclipse plugin: Maven + Tycho (tests, bundle, update site zip)
# Stops at the first failure. Step 2 needs Maven 3.9.x and JDK 17 to 21; the script checks both.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# Tycho 4 needs Maven 3.9.x on JDK 17-21. Newer Maven or JDK versions fail inside Tycho.
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    export JAVA_HOME
fi
if ! command -v mvn >/dev/null 2>&1; then
    echo "mvn not found. Install Maven 3.9.11 (see eclipse/README.md)." >&2
    exit 1
fi
MVN_VERSION="$(mvn -v 2>/dev/null | sed -n 's/^Apache Maven \([0-9.]*\).*/\1/p')"
JAVA_VERSION="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
if [[ "$MVN_VERSION" != 3.9.* ]]; then
    echo "Maven is $MVN_VERSION; the Eclipse build needs 3.9.x (for example 3.9.11)." >&2
    exit 1
fi
if [[ -z "$JAVA_VERSION" ]] || (( JAVA_VERSION < 17 || JAVA_VERSION > 21 )); then
    echo "Java is ${JAVA_VERSION:-unknown}; the Eclipse build needs JDK 17 to 21. Set JAVA_HOME to a JDK 21." >&2
    exit 1
fi
echo "Using Maven $MVN_VERSION and Java $JAVA_VERSION"

echo "==> IntelliJ plugin and shared core (Gradle)"
"$ROOT/gradlew" test buildPlugin

echo "==> Eclipse plugin (Maven + Tycho)"
mvn -f "$ROOT/eclipse/pom.xml" clean package

echo
echo "Done."
echo "IntelliJ plugin zip: $(ls "$ROOT"/build/distributions/*.zip 2>/dev/null | head -1)"
echo "Eclipse update site: $ROOT/eclipse/site/target/damu.plugin.oneclickapi.eclipse.site-2.0.0-SNAPSHOT.zip"
