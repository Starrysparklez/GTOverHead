#!/bin/sh
cd "$(dirname "$0")" || exit 1

./gradlew clean build --console=plain || exit 1

MODS_DIR="$HOME/Library/Application Support/minecraft/home/Forge 1.20.1/mods"
if [ -d "$MODS_DIR" ]; then
    cp build/libs/gtoverhead-*.jar "$MODS_DIR/" && echo "Copied to: $MODS_DIR"
fi
