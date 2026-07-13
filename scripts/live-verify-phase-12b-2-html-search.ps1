# Phase 12B-2 HTML 搜索源 Live 验证脚本
# 此脚本可选用于验证 HTML 搜索源在当前网络环境下的可用性
# 注意：此脚本会向真实搜索引擎发送请求，请谨慎使用

param(
    [switch]$TestBing = $true,
    [switch]$TestDuckDuckGo = $true,
    [switch]$Verbose = $false
)

$ErrorActionPreference = "Stop"
$ProjectRoot = "D:\AiProgram\ai-radar"

function Write-Section {
    param([string]$Message)
    Write-Host "`n=== $Message ===" -ForegroundColor Cyan
}

function Test-Live {
    param([string]$SourceName, [string]$TestUrl)

    Write-Host "`n[TEST] $SourceName" -ForegroundColor Yellow
    Write-Host "请求: $TestUrl" -ForegroundColor Gray

    try {
        $headers = @{
            "User-Agent" = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            "Accept" = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
            "Accept-Language" = "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7"
        }

        $response = Invoke-WebRequest -Uri $TestUrl -Headers $headers -TimeoutSec 10 -UseBasicParsing

        $statusCode = $response.StatusCode
        $contentLength = $response.RawContentLength

        if ($statusCode -eq 200) {
            Write-Host "✓ 状态码: $statusCode" -ForegroundColor Green
            Write-Host "✓ 内容长度: $contentLength bytes" -ForegroundColor Green

            # 检查是否包含被阻止的关键词
            $content = $response.Content
            $blockedKeywords = @("captcha", "recaptcha", "hcaptcha", "cf-challenge", "cloudflare",
                "verify you are human", "access denied", "you have been blocked")

            $isBlocked = $false
            foreach ($keyword in $blockedKeywords) {
                if ($content.ToLower().Contains($keyword)) {
                    $isBlocked = $true
                    Write-Host "✗ 检测到阻止页面: $keyword" -ForegroundColor Red
                    break
                }
            }

            if (-not $isBlocked) {
                # 尝试解析结果数量
                if ($SourceName -eq "Bing") {
                    $resultCount = ([regex]::Matches($content, "<li[^>]*class\s*=\s*""[^""]*b_algo[^""]*""")).Count
                    Write-Host "✓ 解析到结果数: $resultCount" -ForegroundColor Green
                } elseif ($SourceName -eq "DuckDuckGo") {
                    $resultCount = ([regex]::Matches($content, "<div[^>]*class\s*=\s*""[^""]*result[^""]*""")).Count
                    Write-Host "✓ 解析到结果数: $resultCount" -ForegroundColor Green
                }

                return @{
                    Success = $true
                    Status = $statusCode
                    Blocked = $false
                }
            } else {
                return @{
                    Success = $false
                    Status = $statusCode
                    Blocked = $true
                }
            }
        } else {
            Write-Host "✗ 状态码: $statusCode" -ForegroundColor Red
            return @{
                Success = $false
                Status = $statusCode
                Blocked = $false
            }
        }

    } catch [System.Net.WebException] {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "✗ 网络错误: $statusCode" -ForegroundColor Red
        return @{
            Success = $false
            Status = $statusCode
            Blocked = $false
        }
    } catch {
        Write-Host "✗ 错误: $($_.Exception.Message)" -ForegroundColor Red
        return @{
            Success = $false
            Status = 0
            Blocked = $false
        }
    }
}

Push-Location $ProjectRoot

try {
    Write-Section "Phase 12B-2 HTML 搜索源 Live 验证"
    Write-Host "警告: 此脚本会向真实搜索引擎发送测试请求。" -ForegroundColor Yellow
    Write-Host "每个来源最多请求 1 次，输出不包含详细 HTML。" -ForegroundColor Yellow

    $results = @()

    # 测试 Bing
    if ($TestBing) {
        $bingQuery = [System.Web.HttpUtility]::UrlEncode("AI agent")
        $bingUrl = "https://www.bing.com/search?q=$bingQuery&count=10&mkt=en-US"
        $result = Test-Live "Bing" $bingUrl
        $results += @{ Source = "BING_SEARCH"; Result = $result }
    }

    # 测试 DuckDuckGo
    if ($TestDuckDuckGo) {
        $ddgQuery = [System.Web.HttpUtility]::UrlEncode("AI agent")
        $ddgUrl = "https://html.duckduckgo.com/html/?q=$ddgQuery&kl=wt-wt"
        $result = Test-Live "DuckDuckGo" $ddgUrl
        $results += @{ Source = "DUCKDUCKGO_SEARCH"; Result = $result }
    }

    # 总结
    Write-Section "验证结果总结"

    $successCount = 0
    $blockedCount = 0

    foreach ($item in $results) {
        $source = $item.Source
        $result = $item.Result

        if ($result.Success) {
            Write-Host "✓ $source : 可用 (HTTP $($result.Status))" -ForegroundColor Green
            $successCount++
        } elseif ($result.Blocked) {
            Write-Host "✗ $source : 被阻止 (CAPTCHA/challenge)" -ForegroundColor Red
            $blockedCount++
        } else {
            Write-Host "✗ $source : 失败 (HTTP $($result.Status))" -ForegroundColor Red
        }
    }

    Write-Host "`n总计: $($results.Count) 个来源, $successCount 个可用, $blockedCount 个被阻止" -ForegroundColor Cyan

    if ($blockedCount -gt 0) {
        Write-Host "`n提示: 被阻止的来源可能需要："
        Write-Host "  - 更换 IP 地址或网络环境"
        Write-Host "  - 调整请求频率"
        Write-Host "  - 接受该来源在当前环境下不可用" -ForegroundColor Yellow
    }

    if ($successCount -eq $results.Count -and $results.Count -gt 0) {
        Write-Host "`n✓ 所有测试的 HTML 搜索源在当前环境下可用" -ForegroundColor Green
    }

} catch {
    Write-Host "`n错误: $_" -ForegroundColor Red
    exit 1
} finally {
    Pop-Location
}
