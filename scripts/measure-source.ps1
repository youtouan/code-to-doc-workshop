[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Cloc,
    [string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $OutputFile) {
    $OutputFile = Join-Path $root '.runtime\source-metrics.json'
}
$arguments = @('--by-file', '--json', '--quiet', '--include-ext=java,jsp,jspf,xml,sql',
    '--exclude-dir=target,.git,.runtime,.tools,node_modules,.idea,.vscode', $root)
$raw = & $Cloc @arguments
if ($LASTEXITCODE -ne 0) {
    throw "cloc exited with $LASTEXITCODE"
}
$parsed = ($raw -join "`n") | ConvertFrom-Json
$rows = @()
foreach ($property in $parsed.PSObject.Properties) {
    if ($property.Name -in @('header', 'SUM')) {
        continue
    }
    $absolute = $property.Name.Replace('/', '\')
    $relative = if ($absolute.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        $absolute.Substring($root.Length).TrimStart('\')
    } else {
        $absolute
    }
    $bucket = 'build-and-runtime'
    if ($relative -match '\\src\\test\\') {
        $bucket = 'tests'
    } elseif ($relative -match '^database\\0[89][0-9]-') {
        $bucket = 'synthetic-seed'
    } elseif ($relative -match '^wholesale-(core|web|batch)\\src\\main\\' -or
        $relative -match '^database\\0[0-7][0-9]-') {
        $bucket = 'application'
    }
    $rows += [PSCustomObject]@{
        path = $relative
        bucket = $bucket
        language = $property.Value.language
        code = [int]$property.Value.code
        comment = [int]$property.Value.comment
        blank = [int]$property.Value.blank
    }
}
$totals = @()
foreach ($group in ($rows | Group-Object bucket, language)) {
    $totals += [PSCustomObject]@{
        bucket = $group.Group[0].bucket
        language = $group.Group[0].language
        files = $group.Count
        code = ($group.Group | Measure-Object -Property code -Sum).Sum
        comment = ($group.Group | Measure-Object -Property comment -Sum).Sum
        blank = ($group.Group | Measure-Object -Property blank -Sum).Sum
    }
}
$result = [ordered]@{
    measuredAtUtc = [DateTime]::UtcNow.ToString('o')
    method = 'cloc --by-file; application excludes tests, seeds, dependencies, targets, and build/runtime configuration'
    totals = $totals | Sort-Object bucket, language
    files = $rows | Sort-Object bucket, path
}
$outputPath = [IO.Path]::GetFullPath($OutputFile)
$parent = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Path $parent -Force | Out-Null
[IO.File]::WriteAllText($outputPath, ($result | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
$totals | Sort-Object bucket, language | Format-Table -AutoSize
Write-Output "Full per-file metrics: $outputPath"
