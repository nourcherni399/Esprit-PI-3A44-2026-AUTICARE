@echo off
REM Utilise le Maven Wrapper (pas besoin d'installer "mvn" dans le PATH).
setlocal
cd /d "%~dp0"
if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-maven.ps1" compile
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-maven.ps1" %*
)
exit /b %ERRORLEVEL%
