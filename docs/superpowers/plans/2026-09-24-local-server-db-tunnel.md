# Local Server DB Tunnel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one PowerShell entry point that opens the SSH tunnel to the server, injects the local backend database settings, starts the Maven backend, and cleans up only the tunnel process it created.

**Architecture:** Keep the implementation in a single root-level `start-server.ps1`, with small functions for TCP probing, tunnel argument construction, fixed database environment setup, and application startup. Add a dependency-free PowerShell test that parses the script and checks the agreed connection and cleanup contract without contacting the production server.

**Tech Stack:** Windows PowerShell 5.1+, OpenSSH client, Maven Wrapper, Spring Boot, MySQL over SSH local port forwarding

---

## File Structure

- Create `start-server.ps1`: owns tunnel lifecycle, database environment variables, and backend startup.
- Create `scripts/tests/start-server.Tests.ps1`: dependency-free syntax and contract checks for the startup script.

### Task 1: Add the executable contract test

**Files:**
- Create: `scripts/tests/start-server.Tests.ps1`
- Test: `scripts/tests/start-server.Tests.ps1`

- [ ] **Step 1: Write the failing contract test**

```powershell
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptPath = Join-Path $projectRoot 'start-server.ps1'

if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) {
    throw "Missing startup script: $scriptPath"
}

$tokens = $null
$parseErrors = $null
[void][System.Management.Automation.Language.Parser]::ParseFile(
    $scriptPath,
    [ref]$tokens,
    [ref]$parseErrors
)

if ($parseErrors.Count -gt 0) {
    throw "PowerShell syntax errors: $($parseErrors.Message -join '; ')"
}

$content = Get-Content -LiteralPath $scriptPath -Raw
$requiredFragments = @(
    'root@8.153.99.32',
    '3307:127.0.0.1:3306',
    'ExitOnForwardFailure=yes',
    'ServerAliveInterval=30',
    'ServerAliveCountMax=3',
    'jdbc:mysql://127.0.0.1:3307/family_admin',
    'DB_USERNAME',
    'DB_PASSWORD',
    'mvnw.cmd',
    'Stop-Process'
)

foreach ($fragment in $requiredFragments) {
    if (-not $content.Contains($fragment)) {
        throw "Startup script is missing required behavior: $fragment"
    }
}

Write-Host 'start-server.ps1 contract checks passed.' -ForegroundColor Green
```

- [ ] **Step 2: Run the contract test and verify it fails before implementation**

Run from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\start-server.Tests.ps1
```

Expected: non-zero exit with `Missing startup script` because `start-server.ps1` does not exist yet.

### Task 2: Implement tunnel and backend startup

**Files:**
- Create: `start-server.ps1`
- Test: `scripts/tests/start-server.Tests.ps1`

- [ ] **Step 1: Create the complete startup script**

```powershell
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$sshTarget = 'root@8.153.99.32'
$localPort = 3307
$remoteEndpoint = '127.0.0.1:3306'
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
            '-L', "$localPort`:$remoteEndpoint",
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
    $env:DB_PASSWORD = $databasePassword

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
```

- [ ] **Step 2: Run the contract test and verify it passes**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\start-server.Tests.ps1
```

Expected: `start-server.ps1 contract checks passed.` with exit code `0`.

- [ ] **Step 3: Verify the script parses in Windows PowerShell and PowerShell 7**

```powershell
powershell -NoProfile -Command "$e=$null; [void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path '.\start-server.ps1'), [ref]$null, [ref]$e); if ($e.Count) { $e | Format-List; exit 1 }"
pwsh -NoProfile -Command '$e=$null; [void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path "./start-server.ps1"), [ref]$null, [ref]$e); if ($e.Count) { $e | Format-List; exit 1 }'
```

Expected: both commands exit with code `0` and no parser errors.

### Task 3: Perform the user-authenticated smoke test

**Files:**
- Verify: `start-server.ps1`

- [ ] **Step 1: Verify non-interactive SSH authentication**

```powershell
ssh -o BatchMode=yes -o ConnectTimeout=5 root@8.153.99.32 exit
```

Expected: exit code `0`. If authentication fails, configure the user's public key on the server before proceeding; do not put an SSH password into the script.

- [ ] **Step 2: Run the one-command startup flow**

```powershell
.\start-server.ps1
```

Expected: the script reports that the tunnel is established, sets the fixed `DB_PASSWORD` without prompting, and Spring Boot starts successfully using `127.0.0.1:3307`.

- [ ] **Step 3: Verify cleanup behavior**

Stop the backend with `Ctrl+C`, then run:

```powershell
Test-NetConnection 127.0.0.1 -Port 3307 -InformationLevel Quiet
```

Expected: `False` when the script created the tunnel. If a tunnel already existed before startup, expected result is `True` because the script must not stop a process it did not create.

- [ ] **Step 4: Review the final diff**

```powershell
git diff -- start-server.ps1 scripts/tests/start-server.Tests.ps1 docs/superpowers/specs/2026-09-24-local-server-db-tunnel-design.md docs/superpowers/plans/2026-09-24-local-server-db-tunnel.md
```

Expected: only the approved design, plan, startup script, and its test are present. Do not create a Git commit without separate user approval.

## 2026-09-24 Requirement Change

- Replace the runtime secure MySQL password prompt with a fixed plaintext value in `start-server.ps1`.
- Keep the approved password value only in the implementation script; do not duplicate it in this plan.
- Update the contract test to reject `Read-Host` password prompting and require a direct `DB_PASSWORD` assignment.
- Re-run the contract and parser checks after the change.
