@echo off
setlocal EnableExtensions
title orange-wz MCP Server :10003
cd /d "D:\software\OrzRepacker-v1.162.50"
if errorlevel 1 exit /b 1

set "DATA=E:\pro\orange-wz\mcp-runtime\data.bin"
if not exist "%DATA%" set "DATA=C:\Users\Administrator\AppData\Local\Temp\OrzRepacker\v1.162.50\data.bin"
if not exist "%DATA%" set "DATA=D:\software\OrzRepacker-v1.162.50\data.bin"
set "JAVA=%CD%\jre\bin\java.exe"

powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',10003); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if %ERRORLEVEL%==0 (
  echo [orange-wz] MCP already running on :10003
  exit /b 0
)

if not exist "%DATA%" (
  echo [ERROR] MCP data.bin not found
  exit /b 1
)
if not defined ORANGE_WZ_XMX_B set "ORANGE_WZ_XMX_B=6g"
if not defined ORANGE_WZ_GC_OPTS set "ORANGE_WZ_GC_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:InitiatingHeapOccupancyPercent=45"

echo [orange-wz] Starting MCP HTTP server on http://127.0.0.1:10003/mcp
"%JAVA%" -Xmx%ORANGE_WZ_XMX_B% %ORANGE_WZ_GC_OPTS% --enable-native-access=ALL-UNNAMED -Dorange.gui.enabled=false -Dorange.mcp.http.enabled=true -Dserver.port=10003 -javaagent:"%DATA%" -jar "%DATA%"
exit /b %ERRORLEVEL%