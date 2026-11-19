#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TYPE="${1:-app-image}"
# Run the Maven build on THIS operating system first; dependencies contain native JavaFX libraries.
rm -rf desktop/target/package-input
mkdir -p desktop/target/package-input
cp desktop/target/desktop-1.0.0.jar desktop/target/package-input/
cp desktop/target/lib/*.jar desktop/target/package-input/
jpackage --type "$TYPE" --name Staffdesk --app-version 1.0.0  --vendor Staffdesk --description 'Secure employee directory'  --input desktop/target/package-input --main-jar desktop-1.0.0.jar  --main-class com.staffdesk.desktop.Launcher --dest artifacts  --java-options '-Dfile.encoding=UTF-8'  --add-modules java.base,java.desktop,java.net.http,java.logging,java.sql,java.naming,jdk.unsupported,jdk.crypto.ec
