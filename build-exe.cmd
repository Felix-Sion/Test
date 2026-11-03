@echo off
setlocal
cd /d "%~dp0"
set "NO_PAUSE="
if /i "%~1"=="--no-pause" set "NO_PAUSE=1"
if /i "%~2"=="--no-pause" set "NO_PAUSE=1"
if not defined JAVA_HOME set "JAVA_HOME=E:\DevTools\Java\jdk-21.0.13+9"
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
  echo JDK 21 with jpackage is required. Check JAVA_HOME.
  goto :failed
)
if /i "%~1"=="--skip-build" goto :package
call "%~dp0build.cmd" --no-pause
if errorlevel 1 goto :failed

:package
if not exist "%~dp0SnakeGame.jar" goto :failed
if not exist "%~dp0native\windows-x64\snakepatch.dll" (
  echo Build the native DLL with build-native.cmd before packaging.
  goto :failed
)
set "BUILD_ID=%RANDOM%-%RANDOM%"
set "WORK=%~dp0out\packaging\%BUILD_ID%"
set "DEST=%~dp0dist"
if exist "%DEST%\SnakeGame" set "DEST=%DEST%\build-%BUILD_ID%"
mkdir "%WORK%\input"
mkdir "%WORK%\temp"
if errorlevel 1 goto :failed
set "TEMP=%WORK%\temp"
set "TMP=%TEMP%"
copy /y "%~dp0SnakeGame.jar" "%WORK%\input\SnakeGame.jar" >nul
if errorlevel 1 goto :failed

echo Creating a bundled Java runtime...
"%JAVA_HOME%\bin\jlink.exe" --module-path "%JAVA_HOME%\jmods" --add-modules java.desktop,jdk.localedata --include-locales=en,zh --strip-debug --no-header-files --no-man-pages --strip-native-commands --compress=zip-6 --output "%WORK%\runtime"
if errorlevel 1 goto :failed

echo Creating SnakeGame.exe...
"%JAVA_HOME%\bin\jpackage.exe" --type app-image --name SnakeGame --app-version 1.3.0 --vendor "Snake Arcade" --description "Snake Arcade desktop game" --input "%WORK%\input" --main-jar SnakeGame.jar --main-class snake.SnakeGame --runtime-image "%WORK%\runtime" --java-options "-Dsnake.home=$APPDIR/.." --dest "%DEST%"
if errorlevel 1 goto :failed

set "SNAKE_IMAGE=%DEST%\SnakeGame"
set "SNAKE_ZIP=%DEST%\SnakeGame-Windows-x64.zip"
mkdir "%SNAKE_IMAGE%\native\windows-x64"
copy /y "%~dp0native\windows-x64\snakepatch.dll" "%SNAKE_IMAGE%\native\windows-x64\snakepatch.dll" >nul
if errorlevel 1 goto :failed
copy /y "%~dp0packaging\README-Windows.txt" "%SNAKE_IMAGE%\README-Windows.txt" >nul
if errorlevel 1 goto :failed
set "PROFILE=%~dp0dist\SnakeGame\data"
if not exist "%PROFILE%" set "PROFILE=%~dp0data"
if exist "%PROFILE%" (
  mkdir "%SNAKE_IMAGE%\data"
  if exist "%PROFILE%\best-score.txt" copy /y "%PROFILE%\best-score.txt" "%SNAKE_IMAGE%\data\best-score.txt" >nul
  if exist "%PROFILE%\leaderboards.properties" copy /y "%PROFILE%\leaderboards.properties" "%SNAKE_IMAGE%\data\leaderboards.properties" >nul
)

echo Creating the portable ZIP package...
powershell.exe -NoProfile -Command "$ErrorActionPreference='Stop'; Compress-Archive -LiteralPath $env:SNAKE_IMAGE -DestinationPath $env:SNAKE_ZIP -CompressionLevel Optimal"
if errorlevel 1 goto :failed
echo.
echo EXE: %SNAKE_IMAGE%\SnakeGame.exe
echo ZIP: %SNAKE_ZIP%
echo Keep the entire SnakeGame folder together. No Java installation required.
if not defined NO_PAUSE pause
exit /b 0

:failed
echo EXE packaging failed.
if not defined NO_PAUSE pause
exit /b 1
