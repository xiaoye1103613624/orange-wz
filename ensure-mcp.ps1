param(
  [int]$Port = 10002,
  [int]$WaitSeconds = 45
)
$ErrorActionPreference = 'Stop'
function Test-McpPort([int]$p) {
  try {
    $c = New-Object Net.Sockets.TcpClient
    $c.Connect('127.0.0.1', $p)
    $c.Close()
    return $true
  } catch { return $false }
}
if (Test-McpPort $Port) {
  Write-Output "OK already_up :$Port"
  exit 0
}
$bat = 'E:\pro\orange-wz\start-mcp.bat'
if (-not (Test-Path -LiteralPath $bat)) { throw "missing $bat" }
# Start hidden; do not use the VBS if wscript blocked — use Start-Process WindowStyle Hidden
Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', "`"$bat`"") -WindowStyle Hidden
$deadline = (Get-Date).AddSeconds($WaitSeconds)
while ((Get-Date) -lt $deadline) {
  if (Test-McpPort $Port) {
    Write-Output "OK started :$Port"
    exit 0
  }
  Start-Sleep -Milliseconds 500
}
Write-Output "FAIL not_listening :$Port after ${WaitSeconds}s"
exit 1