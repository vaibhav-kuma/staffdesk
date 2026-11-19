param([string]$Type = "app-image")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$inputDir = "desktop/target/package-input"
if (Test-Path $inputDir) { Remove-Item -Recurse -Force $inputDir }
New-Item -ItemType Directory -Force $inputDir | Out-Null
Copy-Item "desktop/target/desktop-1.0.0.jar" $inputDir
Copy-Item "desktop/target/lib/*.jar" $inputDir
jpackage --type $Type --name Staffdesk --app-version 1.0.0 `
 --vendor Staffdesk --description "Secure employee directory" `
 --input $inputDir --main-jar desktop-1.0.0.jar `
 --main-class com.staffdesk.desktop.Launcher --dest artifacts `
 --java-options "-Dfile.encoding=UTF-8" `
 --add-modules "java.base,java.desktop,java.net.http,java.logging,java.sql,java.naming,jdk.unsupported,jdk.crypto.ec"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
