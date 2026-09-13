param(
  [int]$BasePort = 10012,
  [int]$MaxPort = 10029,
  [int]$WaitSeconds = 60,
  [switch]$SyncCursorMcpJson = $true,
  [switch]$KillStale
)
$ErrorActionPreference = 'Stop'

$Root = 'E:\pro\orange-wz'
$Runtime = Join-Path $Root 'mcp-runtime'
$ActivePortFile = Join-Path $Runtime 'active-port.txt'
$EndpointFile = Join-Path $Runtime 'endpoint.json'
$StatusFile = Join-Path $Runtime 'status.txt'
$StartBat = Join-Path $Root 'start-mcp.bat'
$CursorMcpJson = Join-Path $env:USERPROFILE '.cursor\mcp.json'

function Test-McpPort([int]$Port) {
  try {
    $client = New-Object Net.Sockets.TcpClient
    $client.Connect('127.0.0.1', $Port)
    $client.Close()
    return $true
  } catch {
    return $false
  }
}

function Write-RuntimeState([int]$Port, [string]$State) {
  New-Item -ItemType Directory -Force -Path $Runtime | Out-Null
  Set-Content -LiteralPath $ActivePortFile -Value $Port -Encoding UTF8 -NoNewline
  $url = "http://127.0.0.1:$Port/mcp"
  $json = (@{ url = $url; port = $Port; updatedAt = (Get-Date).ToString('o') } | ConvertTo-Json -Compress)
  [System.IO.File]::WriteAllText($EndpointFile, $json, (New-Object System.Text.UTF8Encoding $false))
  @(
    "state=$State"
    "port=$Port"
    "url=$url"
    "time=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
  ) | Set-Content -LiteralPath $StatusFile -Encoding UTF8
}

function Sync-CursorMcp([int]$Port) {
  if (-not $SyncCursorMcpJson) { return }
  if (-not (Test-Path -LiteralPath $CursorMcpJson)) { return }
  $json = Get-Content -LiteralPath $CursorMcpJson -Raw -Encoding UTF8 | ConvertFrom-Json
  if (-not $json.mcpServers.'orange-wz') { return }
  $target = "http://127.0.0.1:$Port/mcp"
  if ($json.mcpServers.'orange-wz'.url -eq $target) { return }
  $json.mcpServers.'orange-wz'.url = $target
  $json | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $CursorMcpJson -Encoding UTF8
}

function Get-RecordedPort {
  if (-not (Test-Path -LiteralPath $ActivePortFile)) { return $null }
  $text = (Get-Content -LiteralPath $ActivePortFile -Raw).Trim()
  if ($text -match '^\d+$') { return [int]$text }
  return $null
}

function Find-FreePort([int]$From, [int]$To) {
  for ($p = $From; $p -le $To; $p++) {
    if (-not (Test-McpPort $p)) { return $p }
  }
  return $null
}

if (-not (Test-Path -LiteralPath $StartBat)) {
  throw "missing $StartBat"
}

$recorded = Get-RecordedPort
if ($recorded -and (Test-McpPort $recorded)) {
  Write-RuntimeState $recorded 'already_up'
  Sync-CursorMcp $recorded
  Write-Output "OK already_up :$recorded"
  exit 0
}

for ($p = $BasePort; $p -le $MaxPort; $p++) {
  if (Test-McpPort $p) {
    Write-RuntimeState $p 'already_up'
    Sync-CursorMcp $p
    Write-Output "OK already_up :$p"
    exit 0
  }
}

$port = Find-FreePort $BasePort $MaxPort
if (-not $port) {
  Write-Output "FAIL no_free_port ${BasePort}-${MaxPort}"
  exit 1
}

if ($KillStale) {
  Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*orange.mcp.http.enabled=true*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
  Start-Sleep -Seconds 1
}

$env:ORANGE_WZ_PORT = "$port"
Start-Process -FilePath 'cmd.exe' -ArgumentList @('/c', "`"$StartBat`"") -WindowStyle Hidden
$deadline = (Get-Date).AddSeconds($WaitSeconds)
while ((Get-Date) -lt $deadline) {
  if (Test-McpPort $port) {
    Write-RuntimeState $port 'started'
    Sync-CursorMcp $port
    Write-Output "OK started :$port"
    exit 0
  }
  Start-Sleep -Milliseconds 500
}

Write-Output "FAIL not_listening :$port after ${WaitSeconds}s"
exit 1
