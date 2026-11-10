[CmdletBinding()]
param(
    [string]$Psql = 'psql',
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [string]$DatabaseName = 'wholesale',
    [string]$UserName = 'wholesale',
    [switch]$SchemaOnly
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$env:PGCLIENTENCODING = 'UTF8'
$connection = @('-X', '-h', $HostName, '-p', "$Port", '-U', $UserName, '-d', $DatabaseName,
    '-v', 'ON_ERROR_STOP=1')
$existing = & $Psql @connection -A -t -c "select count(*) from information_schema.tables where table_schema='public'"
if ($LASTEXITCODE -ne 0) {
    throw 'Could not connect to the requested PostgreSQL database.'
}
if ($existing.Trim() -ne '0') {
    throw 'Database public schema is not empty. Create a new dedicated database; this command never resets data.'
}
$files = Get-ChildItem -LiteralPath (Join-Path $root 'database') -Filter '*.sql' -File |
    Where-Object { $_.Name -match '^\d{3}-' -and (-not $SchemaOnly -or [int]$_.Name.Substring(0, 3) -lt 80) } |
    Sort-Object Name
if ($files.Count -eq 0) {
    throw 'No database scripts found.'
}
$arguments = $connection + @('--single-transaction')
foreach ($file in $files) {
    $arguments += @('-f', $file.FullName)
}
& $Psql @arguments
if ($LASTEXITCODE -ne 0) {
    throw 'Database initialization failed. The transaction was rolled back.'
}
Write-Output "Initialized $DatabaseName with $($files.Count) SQL scripts."
