@echo off
setlocal

rem 1. Auto-configure JAVA_HOME and guard against broken pleiades JDK or old Java versions
set "FORCE_JDK17=0"
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot" (
    if "%JAVA_HOME%"=="" set "FORCE_JDK17=1"
    if not "%JAVA_HOME%"=="" (
        echo %JAVA_HOME% | findstr /i "pleiades" >nul
        if not errorlevel 1 set "FORCE_JDK17=1"
        echo %JAVA_HOME% | findstr /i "java8" >nul
        if not errorlevel 1 set "FORCE_JDK17=1"
        echo %JAVA_HOME% | findstr /i "1.8" >nul
        if not errorlevel 1 set "FORCE_JDK17=1"
    )
)

if "%FORCE_JDK17%"=="1" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
    set "PATH=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin;%PATH%"
)

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
