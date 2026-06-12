@echo off
echo ============================================
echo  Parallel Point-in-Polygon - Build and Run
echo ============================================

if not exist bin mkdir bin

echo.
echo [1] Compiling...
javac --release 8 -d bin src\PointInPolygon.java src\PolygonVisualizer.java
if %errorlevel% neq 0 (
    echo COMPILE ERROR!
    pause
    exit /b 1
)
echo     OK

echo.
echo [2] Running benchmark (PointInPolygon)...
java -cp bin PointInPolygon

echo.
echo [3] Launching visualizer (PolygonVisualizer)...
java -cp bin PolygonVisualizer

pause
