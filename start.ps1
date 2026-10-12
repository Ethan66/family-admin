[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$sshTarget = 'root@8.153.99.32'
$localPort = 3307
$remoteEndpoint = '127.0.0.1:3307'
$tunnelSpec = '3307:127.0.0.1:3307'
$databaseUsername = 'family_admin'
$projectRoot = $PSScriptRoot
$serverDirectory = Join-Path $projectRoot 'server'
$mavenWrapper = Join-Path $serverDirectory 'mvnw.cmd'
$createdTunnelProcess = $null
$locationPushed = $false

function Test-LocalTcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$HostName,

        [Parameter(Mandatory = $true)]
        [int]$Port,

        [int]$TimeoutMilliseconds = 500
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne($TimeoutMilliseconds, $false)) {
            return $false
        }

        $client.EndConnect($connect)
        return $true
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

try {
    $ssh = Get-Command 'ssh.exe' -ErrorAction SilentlyContinue
    if (-not $ssh) {
        throw '未找到 ssh.exe，请先安装 Windows OpenSSH 客户端。'
    }

    if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
        throw "未找到 Maven Wrapper：$mavenWrapper"
    }

    if (Test-LocalTcpPort -HostName '127.0.0.1' -Port $localPort) {
        Write-Host "检测到本地端口 $localPort 已可用，将复用现有 SSH 隧道。" -ForegroundColor Yellow
    }
    else {
        $sshArguments = @(
            '-N',
            '-L', $tunnelSpec,
            '-o', 'BatchMode=yes',
            '-o', 'ExitOnForwardFailure=yes',
            '-o', 'ServerAliveInterval=30',
            '-o', 'ServerAliveCountMax=3',
            $sshTarget
        )

        Write-Host "正在建立数据库隧道：127.0.0.1:$localPort -> $sshTarget/$remoteEndpoint"
        $createdTunnelProcess = Start-Process `
            -FilePath $ssh.Source `
            -ArgumentList $sshArguments `
            -WindowStyle Hidden `
            -PassThru

        $tunnelReady = $false
        foreach ($attempt in 1..20) {
            Start-Sleep -Milliseconds 500
            $createdTunnelProcess.Refresh()

            if ($createdTunnelProcess.HasExited) {
                throw 'SSH 隧道进程已退出。请确认 SSH 密钥登录可用。'
            }

            if (Test-LocalTcpPort -HostName '127.0.0.1' -Port $localPort) {
                $tunnelReady = $true
                break
            }
        }

        if (-not $tunnelReady) {
            throw "等待 SSH 隧道超时，本地端口 $localPort 不可用。"
        }

        Write-Host 'SSH 数据库隧道已建立。' -ForegroundColor Green
    }

    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/family_admin?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
    $env:DB_USERNAME = $databaseUsername
    $env:DB_PASSWORD = 'zc123456'

    Push-Location $serverDirectory
    $locationPushed = $true
    & $mavenWrapper 'spring-boot:run'

    if ($LASTEXITCODE -ne 0) {
        throw "后端启动命令退出，状态码：$LASTEXITCODE"
    }
}
finally {
    if ($locationPushed) {
        Pop-Location
    }

    if ($createdTunnelProcess -and -not $createdTunnelProcess.HasExited) {
        Stop-Process -Id $createdTunnelProcess.Id
        Write-Host '已关闭本次脚本创建的 SSH 隧道。'
    }
}
