$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"
$frontendDir = Join-Path $repoRoot "frontend"

$checks = @(
    @{
        Name = "Backend Phase 10 OpenAI provider tests"
        Command = ".\mvnw.cmd"
        Args = @("-Dtest=OpenAiAnalysisPromptFactoryTest,OpenAiAnalysisResponseMapperTest,OpenAiStructuredAnalysisClientTest,OpenAiAnalysisProviderIntegrationTest,ClusterAnalysisIntegrationTest", "test")
        Workdir = $backendDir
    },
    @{
        Name = "Backend full regression tests"
        Command = ".\mvnw.cmd"
        Args = @("test")
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
Write-Host "Phase 10 acceptance passed." -ForegroundColor Green
Write-Host "Validated closed loop:"
Write-Host "  OpenAI Chat Completions (response_format=json_object) -> cluster_analysis -> /analysis"
Write-Host "  provider selection (openai default + fake fallback) preserved"
Write-Host "  frontend detail page shows provider metadata and stable failure copy"
Write-Host ""
Write-Host "Optional live verification (requires OPENAI_API_KEY and a reachable OpenAI-compatible endpoint):"
Write-Host "  powershell -File .\scripts\live-verify-openai.ps1 -ClusterId <id>"
