@echo off
setlocal EnableExtensions
title OrzRepacker GUI
cd /d "D:\software\OrzRepacker-v1.162.50"
if errorlevel 1 (
  echo [ERROR] missing D:\software\OrzRepacker-v1.162.50
  exit /b 1
)
if exist "OrzRepacker.exe" (
  start "" "OrzRepacker.exe"
  exit /b 0
)
echo [ERROR] OrzRepacker.exe not found
exit /b 1