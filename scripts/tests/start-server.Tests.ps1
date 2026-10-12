$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$scriptPath = Join-Path $projectRoot 'start.ps1'

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
    '3307:127.0.0.1:3307',
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

if ($content.Contains('Read-Host')) {
    throw 'Startup script must not prompt for the MySQL password.'
}

if ($content -notmatch "\`$env:DB_PASSWORD\s*=\s*'[^']+'") {
    throw 'Startup script must assign a fixed non-empty DB_PASSWORD value.'
}

Write-Host 'start.ps1 contract checks passed.' -ForegroundColor Green
