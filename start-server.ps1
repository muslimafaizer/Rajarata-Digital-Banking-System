# Rajarata Digital Banking System PowerShell Launcher
Write-Host "==============================================================" -ForegroundColor Green
Write-Host "   RAJARATA DIGITAL BANKING SYSTEM - STARTING SERVER          " -ForegroundColor Green
Write-Host "==============================================================" -ForegroundColor Green

if (!(Test-Path "bin")) { New-Item -ItemType Directory -Path "bin" | Out-Null }
$files = Get-ChildItem -Path src/pack_1 -Filter *.java -Recurse | Select-Object -ExpandProperty FullName

Write-Host "Compiling Java source files..." -ForegroundColor Cyan
javac -encoding UTF-8 -d bin -cp "lib/*" $files
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed! Ensure Java 17+ or 25 is installed." -ForegroundColor Red
    exit 1
}

Write-Host "`nServer running at: http://localhost:8080" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop the server.`n" -ForegroundColor Gray
java "-Dfile.encoding=UTF-8" -cp "bin;lib/*" pack_1.WebServer
