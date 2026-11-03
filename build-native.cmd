@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=E:\DevTools\Java\jdk-21.0.13+9"
if not defined ZIG_EXE set "ZIG_EXE=E:\DevTools\Zig\zig-x86_64-windows-0.17.0\zig.exe"
if not exist "%ZIG_EXE%" (
  echo Zig C compiler not found. Set ZIG_EXE to the full path of zig.exe.
  echo Download: https://ziglang.org/download/
  goto :failed
)
if not exist "%JAVA_HOME%\include\jni.h" (
  echo JDK headers not found. Check JAVA_HOME.
  goto :failed
)
if not exist "out\native" mkdir "out\native"
if not exist "out\zig-cache\global" mkdir "out\zig-cache\global"
if not exist "out\zig-cache\local" mkdir "out\zig-cache\local"
if not exist "native\windows-x64" mkdir "native\windows-x64"
set "ZIG_GLOBAL_CACHE_DIR=%~dp0out\zig-cache\global"
set "ZIG_LOCAL_CACHE_DIR=%~dp0out\zig-cache\local"
set "TEMP=%~dp0out\native"
set "TMP=%TEMP%"
"%ZIG_EXE%" cc -target x86_64-windows-gnu -std=c11 -O2 -Wall -Wextra -Werror -shared -I"%JAVA_HOME%\include" -I"%JAVA_HOME%\include\win32" "native\snakepatch.c" "native\color_x64.S" -o "out\native\snakepatch.dll"
if errorlevel 1 goto :failed
copy /y "out\native\snakepatch.dll" "native\windows-x64\snakepatch.dll" >nul
if errorlevel 1 goto :failed
echo Native DLL built: native\windows-x64\snakepatch.dll
if /i not "%~1"=="--no-pause" pause
exit /b 0
:failed
echo Native build failed. Close any game launched from this project before rebuilding.
if /i not "%~1"=="--no-pause" pause
exit /b 1
