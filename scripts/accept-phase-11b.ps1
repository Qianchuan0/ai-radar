$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"

$checks = @(
    @{
        Name = "Backend Phase 11B scheduled daily report tests"
        Command = ".\mvnw.cmd"
        Args = @("-Dtest=ScheduledDailyReportServiceIntegrationTest,DailyReportIntegrationTest", "test")
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
Write-Host "Phase 11B acceptance passed." -ForegroundColor Green
Write-Host "Validated closed loop:"
Write-Host "  optional Spring Scheduler trigger -> UTC target report date -> existing DailyReportService.generate"
Write-Host "  existing report skip by default, explicit refresh-existing update path, and empty-report generation"
