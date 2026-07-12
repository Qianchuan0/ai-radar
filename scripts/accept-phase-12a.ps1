$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"

$checks = @(
    @{
        Name = "Backend Sogou Search tests"
        Command = ".\mvnw.cmd"
        Args = @("-Dtest=TencentCloudV3SignerTest,SogouSearchClientTest,SogouSearchRawDataFlowIntegrationTest", "test")
        Workdir = $backendDir
    },
    @{
        Name = "Frontend tests"
        Command = "npm"
        Args = @("test", "--", "--run")
        Workdir = $frontendDir
    },
    @{
        Name = "Frontend build"
        Command = "npm"
        Args = @("run", "build")
        Workdir = $frontendDir
    }
)

foreach ($check in $checks) {
    Write-Host ""
    Write-Host "==> $($check.Name)" -ForegroundColor Cyan
    Write-Host "    cwd: $($check.Workdir)"
    Write-Host "    cmd: $($check.Command) $($check.Args -join ' ')"

    Push-Location $check.Workdir
    try {
        & $check.Command @($check.Args)
        if ($LASTEXITCODE -ne 0) {
            throw "$($check.Name) failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

Write-Host ""
Write-Host "Phase 12A acceptance passed." -ForegroundColor Green
Write-Host "Validated closed loop:"
Write-Host "  Sogou Search client -> collector -> raw_item -> hot_item -> hot_cluster"
Write-Host "  frontend compatibility -> source enum filters and production bundle"
