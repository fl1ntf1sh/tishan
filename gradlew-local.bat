@echo off
setlocal

@rem Java NIO selectors can fail in Codex-launched Windows child processes when
@rem the temporary Unix-domain socket path is too long. Keep the standard
@rem Gradle wrapper untouched and run it with a short, per-drive temp path.
set "TISHAN_GRADLE_TEMP=%~d0\tmp\tishan-gradle"

if not exist "%TISHAN_GRADLE_TEMP%" mkdir "%TISHAN_GRADLE_TEMP%"
if not exist "%TISHAN_GRADLE_TEMP%" (
    echo Unable to create Gradle temp directory: %TISHAN_GRADLE_TEMP% 1>&2
    exit /b 1
)

set "TEMP=%TISHAN_GRADLE_TEMP%"
set "TMP=%TISHAN_GRADLE_TEMP%"

call "%~dp0gradlew.bat" %*
exit /b %errorlevel%
