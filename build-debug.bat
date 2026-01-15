@echo off
REM -------------------------------
REM Build Debug APK for Capacitor App
REM -------------------------------

REM Set Java 17 path
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

REM Show current Java version
echo Using Java:
java -version
javac -version

REM Go to android folder
cd android

REM Stop old Gradle daemons
gradlew.bat --stop

REM Clean previous builds
gradlew.bat clean

REM Build debug APK
gradlew.bat assembleDebug

REM Open APK output folder
explorer app\build\outputs\apk\debug

pause
