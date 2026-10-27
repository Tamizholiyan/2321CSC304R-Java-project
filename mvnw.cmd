@echo off
setlocal

rem 1. Auto-configure JAVA_HOME if not already set in current shell
if "%JAVA_HOME%"=="" (
    if exist "%USERPROFILE%\.jdk\jdk-17.0.20.1+1" (
        set "JAVA_HOME=%USERPROFILE%\.jdk\jdk-17.0.20.1+1"
        set "PATH=%USERPROFILE%\.jdk\jdk-17.0.20.1+1\bin;%PATH%"
    )
)

rem 2. Check for local project Maven installation
set "MAVEN_CMD=%USERPROFILE%\.m2\apache-maven-3.9.6\bin\mvn.cmd"
if exist "%MAVEN_CMD%" (
    call "%MAVEN_CMD%" %*
    exit /b %ERRORLEVEL%
)

rem 3. Check for system-wide mvn in PATH
where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

rem 4. Auto-download Apache Maven if missing
echo [Campus Commute] Downloading Apache Maven 3.9.6...
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = Join-Path $env:TEMP 'maven-3.9.6.zip'; curl.exe -L -s -o $zip 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip'; if (-not (Test-Path '%USERPROFILE%\.m2')) { New-Item -ItemType Directory -Path '%USERPROFILE%\.m2' | Out-Null }; tar.exe -xf $zip -C '%USERPROFILE%\.m2'; Remove-Item $zip -Force -ErrorAction SilentlyContinue"

if exist "%MAVEN_CMD%" (
    call "%MAVEN_CMD%" %*
    exit /b %ERRORLEVEL%
)

rem 5. Fallback guard: Only reached if network/download failed and no Maven is found
echo ERROR: Could not locate or download Maven. Please ensure Java 17 is installed.
exit /b 1
