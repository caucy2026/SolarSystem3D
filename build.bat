@echo off
setlocal
set "JAVA_HOME=D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7"
set "PROJECT_DIR=D:\work\ai_code\SolarSystem3D"

echo ========================================
echo  SolarSystem3D Build Script
echo  JDK: %JAVA_HOME%
echo  Project: %PROJECT_DIR%
echo ========================================

echo.
echo [1/2] Building APK...
call "%JAVA_HOME%\bin\java.exe" ^
  -classpath "%PROJECT_DIR%\gradle\wrapper\gradle-wrapper.jar" ^
  org.gradle.wrapper.GradleWrapperMain assembleRelease --no-daemon ^
  -p "%PROJECT_DIR%"

if %ERRORLEVEL% neq 0 (
    echo BUILD FAILED!
    pause
    exit /b 1
)

echo.
echo [2/2] Installing to device...
adb install -r "%PROJECT_DIR%\app\build\outputs\apk\release\app-release.apk"

if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================
    echo  INSTALL SUCCESS!
    echo  Launch: adb shell am start -n com.solar.dualscreen/.MainActivity
    echo ========================================
) else (
    echo INSTALL FAILED!
)

pause
endlocal
