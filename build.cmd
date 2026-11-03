@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=E:\DevTools\Java\jdk-21.0.13+9"
if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo Java JDK not found. Check JAVA_HOME.
  pause
  exit /b 1
)
if not exist "out\production\SnakeGame" mkdir "out\production\SnakeGame"
if not exist "out\test\SnakeGame" mkdir "out\test\SnakeGame"
"%JAVA_HOME%\bin\javac.exe" --release 21 -encoding UTF-8 -d "out\production\SnakeGame" "src\snake\GameModel.java" "src\snake\Difficulty.java" "src\snake\ScoreBoard.java" "src\snake\GameSession.java" "src\snake\BackgroundMusic.java" "src\snake\SnakeGame.java"
if errorlevel 1 goto :failed
"%JAVA_HOME%\bin\javac.exe" --release 21 -encoding UTF-8 -cp "out\production\SnakeGame" -d "out\test\SnakeGame" "tests\snake\GameModelTest.java" "tests\snake\ScoreBoardTest.java" "tests\snake\BackgroundMusicTest.java"
if errorlevel 1 goto :failed
"%JAVA_HOME%\bin\java.exe" -ea -cp "out\production\SnakeGame;out\test\SnakeGame" snake.GameModelTest
if errorlevel 1 goto :failed
"%JAVA_HOME%\bin\java.exe" -ea -cp "out\production\SnakeGame;out\test\SnakeGame" snake.ScoreBoardTest
if errorlevel 1 goto :failed
"%JAVA_HOME%\bin\java.exe" -ea -cp "out\production\SnakeGame;out\test\SnakeGame;resources" snake.BackgroundMusicTest
if errorlevel 1 goto :failed
"%JAVA_HOME%\bin\jar.exe" --create --file "SnakeGame.jar" --main-class snake.SnakeGame -C "out\production\SnakeGame" . -C resources .
if errorlevel 1 goto :failed
echo Build successful: SnakeGame.jar
if /i not "%~1"=="--no-pause" pause
exit /b 0
:failed
echo Build failed.
if /i not "%~1"=="--no-pause" pause
exit /b 1
