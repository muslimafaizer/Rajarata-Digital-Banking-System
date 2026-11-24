@echo off
title Rajarata Digital Banking System - Server
echo ==============================================================
echo        RAJARATA DIGITAL BANKING SYSTEM - STARTING SERVER      
echo ==============================================================
echo Compiling Java source files...
if not exist "bin" mkdir bin
javac -encoding UTF-8 -d bin -cp "lib/*" src/pack_1/*.java src/pack_1/model/*.java src/pack_1/service/*.java src/pack_1/repository/*.java src/pack_1/controller/*.java src/pack_1/exception/*.java src/pack_1/util/*.java
if %ERRORLEVEL% neq 0 (
    echo Compilation failed! Please ensure JDK 17+ or JDK 25 is installed.
    pause
    exit /b %ERRORLEVEL%
)
echo Server starting at http://localhost:8080 ...
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" pack_1.WebServer
pause
