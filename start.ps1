$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Find-Docker {
    $cmd = Get-Command docker -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'),
        (Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe')
    )
    foreach ($c in $candidates) {
        if (Test-Path -LiteralPath $c) { return $c }
    }
    $reg = Get-ItemProperty 'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -like 'Docker Desktop*' } | Select-Object -First 1
    if ($reg -and $reg.InstallLocation) {
        $p = Join-Path $reg.InstallLocation 'resources\bin\docker.exe'
        if (Test-Path -LiteralPath $p) { return $p }
    }
    throw '未找到 docker.exe，请确认 Docker Desktop 已安装'
}

function Wait-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $async = $client.BeginConnect($HostName, $Port, $null, $null)
            if ($async.AsyncWaitHandle.WaitOne(800) -and $client.Connected) { return $true }
        } catch {
        } finally {
            $client.Close()
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

Write-Host ''
Write-Host '=== 物联网 Demo 启动 ===' -ForegroundColor Cyan
Write-Host ''

$docker = Find-Docker
# Docker 的凭据助手也在同一目录，加入 PATH 后拉取镜像才正常
$env:PATH = (Split-Path -Parent $docker) + ';' + $env:PATH

Write-Host '[1/3] 启动 EMQX ...' -ForegroundColor Yellow
& $docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker 引擎未运行，请先打开 Docker Desktop，等状态变为 Engine running 再试'
}
& $docker compose up -d
if ($LASTEXITCODE -ne 0) { throw 'docker compose 启动失败' }

if (Wait-TcpPort '127.0.0.1' 1883 45) {
    Write-Host '      EMQX 已就绪（1883）' -ForegroundColor Green
} else {
    Write-Warning 'EMQX 的 1883 端口尚未就绪，服务端会继续启动并自动重连'
}

Write-Host '[2/3] 构建服务端 ...' -ForegroundColor Yellow
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw '未找到 Maven，请安装 Maven 并加入 PATH'
}
Push-Location (Join-Path $PSScriptRoot 'server')
try {
    & mvn -B -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败' }
} finally {
    Pop-Location
}

if (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue) {
    throw '8081 端口已被占用，请先关闭占用它的程序'
}

Write-Host '[3/3] 启动服务端 http://localhost:8081/' -ForegroundColor Yellow
Write-Host ''
Write-Host '测试网页会自动打开。停止服务请按 Ctrl+C。' -ForegroundColor DarkGray
Write-Host ''

Start-Process 'http://localhost:8081/'
& java -jar (Join-Path $PSScriptRoot 'server\target\iot-gateway-0.1.0.jar')

Write-Host ''
Write-Host '服务端已退出。' -ForegroundColor Red
