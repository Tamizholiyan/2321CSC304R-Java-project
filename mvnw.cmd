@echo off
setlocal

where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

set "MAVEN_DIR=%USERPROFILE%\.m2\apache-maven-3.9.6"
set "MAVEN_CMD=%MAVEN_DIR%\bin\mvn.cmd"

if not exist "%MAVEN_CMD%" (
    echo [Campus Commute] Maven not found. Downloading Apache Maven 3.9.6...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = Join-Path $env:TEMP 'maven-3.9.6.zip'; curl.exe -L -s -o $zip 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip'; if (-not (Test-Path '%USERPROFILE%\.m2')) { New-Item -ItemType Directory -Path '%USERPROFILE%\.m2' | Out-Null }; tar.exe -xf $zip -C '%USERPROFILE%\.m2'; Remove-Item $zip -Force -ErrorAction SilentlyContinue"
)

if exist "%MAVEN_CMD%" (
    call "%MAVEN_CMD%" %*
    exit /b %ERRORLEVEL%
)

echo ERROR: Could not locate or download Maven. Please ensure Java 17 is installed.
exit /b 1
