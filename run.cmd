@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=E:\DevTools\Java\jdk-21.0.13+9"
if not exist "%JAVA_HOME%\bin\javaw.exe" (
  echo Java not found. Check JAVA_HOME.
  pause
  exit /b 1
)
if not exist "%~dp0SnakeGame.jar" (
  echo Run build.cmd first to create SnakeGame.jar.
  pause
  exit /b 1
)
start "" "%JAVA_HOME%\bin\javaw.exe" "-Dsnake.home=%~dp0." -jar "%~dp0SnakeGame.jar"
