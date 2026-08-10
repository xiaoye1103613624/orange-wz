@echo off
setlocal EnableExtensions
title orange-wz MCP Server
cd /d "D:\software\OrzRepacker-v1.162.50"
if errorlevel 1 (
  echo [ERROR] OrzRepacker install not found: D:\software\OrzRepacker-v1.162.50
  exit /b 1
)

REM Prefer stable copy under orange-wz (survives Temp cleanup). Fallback to Temp then install.
set "DATA=E:\pro\orange-wz\mcp-runtime\data.bin"
if not exist "%DATA%" set "DATA=C:\Users\Administrator\AppData\Local\Temp\OrzRepacker\v1.162.50\data.bin"
if not exist "%DATA%" set "DATA=D:\software\OrzRepacker-v1.162.50\data.bin"

set "JAVA=%CD%\jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=jre\bin\java.exe"

REM Already listening on 10002? Do not spawn another console / JVM.
powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',10002); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if %ERRORLEVEL%==0 (
  echo [orange-wz] MCP already running on http://127.0.0.1:10002/mcp
  exit /b 0
)

if not exist "%DATA%" (
  echo [ERROR] MCP data.bin not found.
  echo Tried: E:\pro\orange-wz\mcp-runtime\data.bin
  echo Tried: Temp\OrzRepacker\v1.162.50\data.bin
  echo Tried: D:\software\OrzRepacker-v1.162.50\data.bin
  exit /b 1
)

if not exist "%JAVA%" (
  echo [ERROR] java.exe not found under %CD%\jre\bin
  exit /b 1
)

if not defined ORANGE_WZ_XMX set "ORANGE_WZ_XMX=12g"
if not defined ORANGE_WZ_GC_OPTS set "ORANGE_WZ_GC_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:InitiatingHeapOccupancyPercent=45"

echo [orange-wz] Starting MCP HTTP server on http://127.0.0.1:10002/mcp
echo [orange-wz] DATA=%DATA%
echo [orange-wz] JAVA=%JAVA%
echo [orange-wz] Heap -Xmx%ORANGE_WZ_XMX%

"%JAVA%" -Xmx%ORANGE_WZ_XMX% %ORANGE_WZ_GC_OPTS% --enable-native-access=ALL-UNNAMED -Dorange.gui.enabled=false -Dorange.mcp.http.enabled=true -Dserver.port=10002 -javaagent:"%DATA%" -jar "%DATA%"
set "RC=%ERRORLEVEL%"
echo [orange-wz] exited code=%RC%
exit /b %RC%