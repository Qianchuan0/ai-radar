$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"

$checks = @(
    @{
        Name = "Backend Phase 11A scheduled crawl tests"
        Command = ".\mvnw.cmd"
        Args = @("-Dtest=ScheduledCrawlServiceIntegrationTest", "test")
        Workdir = $backendDir
    },
    @{
        Name = "Backend full regression tests"
        Command = ".\mvnw.cmd"
        Args = @("test")
        Workdir = $backendDir
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
Write-Host "Phase 11A acceptance passed." -ForegroundColor Green
Write-Host "Validated closed loop:"
Write-Host "  source_config interval metadata -> scheduled due check -> SCHEDULED crawl_task -> existing crawl pipeline"
Write-Host "  crawl-task list API filters expose scheduled run history"
