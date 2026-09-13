@echo off
setlocal EnableExtensions
title orange-wz MCP Server

if not defined ORANGE_WZ_PORT set "ORANGE_WZ_PORT=10012"
set "ORANGE_WZ_RUNTIME=E:\pro\orange-wz\mcp-runtime"

cd /d "D:\software\OrzRepacker-v1.162.50"
if errorlevel 1 (
  echo [ERROR] OrzRepacker install not found: D:\software\OrzRepacker-v1.162.50
  exit /b 1
)

set "DATA=E:\pro\orange-wz\mcp-runtime\data.bin"
if not exist "%DATA%" set "DATA=C:\Users\Administrator\AppData\Local\Temp\OrzRepacker\v1.162.50\data.bin"
if not exist "%DATA%" set "DATA=D:\software\OrzRepacker-v1.162.50\data.bin"

set "JAVA=%CD%\jre\bin\java.exe"
if not exist "%JAVA%" set "JAVA=jre\bin\java.exe"

powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',%ORANGE_WZ_PORT%); $c.Close(); exit 0 } catch { exit 1 }" >nul 2>&1
if %ERRORLEVEL%==0 (
  echo [orange-wz] MCP already running on http://127.0.0.1:%ORANGE_WZ_PORT%/mcp
  exit /b 0
)

if not exist "%DATA%" (
  echo [ERROR] MCP data.bin not found.
  exit /b 1
)

if not exist "%JAVA%" (
  echo [ERROR] java.exe not found under %CD%\jre\bin
  exit /b 1
)

if not defined ORANGE_WZ_XMX set "ORANGE_WZ_XMX=12g"
if not defined ORANGE_WZ_GC_OPTS set "ORANGE_WZ_GC_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:InitiatingHeapOccupancyPercent=45"

if not exist "%ORANGE_WZ_RUNTIME%" mkdir "%ORANGE_WZ_RUNTIME%"
> "%ORANGE_WZ_RUNTIME%\active-port.txt" echo %ORANGE_WZ_PORT%
powershell -NoProfile -Command "$p=%ORANGE_WZ_PORT%; $u='http://127.0.0.1:'+$p+'/mcp'; @{url=$u;port=[int]$p;updatedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath '%ORANGE_WZ_RUNTIME%\endpoint.json' -Encoding UTF8"

echo [orange-wz] Starting MCP HTTP server on http://127.0.0.1:%ORANGE_WZ_PORT%/mcp
echo [orange-wz] DATA=%DATA%
echo [orange-wz] JAVA=%JAVA%
echo [orange-wz] Heap -Xmx%ORANGE_WZ_XMX%

"%JAVA%" -Xmx%ORANGE_WZ_XMX% %ORANGE_WZ_GC_OPTS% --enable-native-access=ALL-UNNAMED -Dorange.gui.enabled=false -Dorange.mcp.http.enabled=true -Dserver.port=%ORANGE_WZ_PORT% -javaagent:"%DATA%" -jar "%DATA%"
set "RC=%ERRORLEVEL%"
echo [orange-wz] exited code=%RC%
exit /b %RC%
