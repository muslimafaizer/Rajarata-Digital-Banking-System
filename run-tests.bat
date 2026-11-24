@echo off
title Rajarata Digital Banking System - Automated Tests
echo ==============================================================
echo    RAJARATA DIGITAL BANKING SYSTEM - RUNNING TEST SUITE       
echo ==============================================================
javac -encoding UTF-8 -d bin -cp "bin;lib/*" src/pack_1/*.java src/pack_1/model/*.java src/pack_1/service/*.java src/pack_1/repository/*.java src/pack_1/controller/*.java src/pack_1/exception/*.java src/pack_1/util/*.java
java -Dfile.encoding=UTF-8 -cp "bin;lib/*" pack_1.TestSystem
pause
