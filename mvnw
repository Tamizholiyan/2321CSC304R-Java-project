#!/usr/bin/env sh
# Maven Wrapper for Linux/macOS
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
fi

MAVEN_DIR="$HOME/.m2/apache-maven-3.9.6"
MAVEN_CMD="$MAVEN_DIR/bin/mvn"

if [ ! -f "$MAVEN_CMD" ]; then
    echo "[Campus Commute] Maven not found. Downloading Apache Maven 3.9.6..."
    mkdir -p "$HOME/.m2"
    TMP_ZIP="/tmp/maven-3.9.6.zip"
    curl -s -L -o "$TMP_ZIP" "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
    unzip -q -o "$TMP_ZIP" -d "$HOME/.m2"
    chmod +x "$MAVEN_CMD"
    rm -f "$TMP_ZIP"
fi

if [ -f "$MAVEN_CMD" ]; then
    exec "$MAVEN_CMD" "$@"
fi

echo "ERROR: Could not locate or download Maven. Please ensure Java 17 is installed."
exit 1
