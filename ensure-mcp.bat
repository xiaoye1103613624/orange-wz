@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-mcp.ps1" %*
