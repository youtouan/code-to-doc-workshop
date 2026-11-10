[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$TomcatHome,
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$Port = 8080,
    [string]$DbUrl = 'jdbc:postgresql://127.0.0.1:5432/wholesale',
    [string]$DbUser = 'wholesale',
    [AllowEmptyString()][string]$DbPassword = 'wholesale-local'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$war = Join-Path $root 'wholesale-web\target\wholesale.war'
if (-not (Test-Path -LiteralPath $war -PathType Leaf)) {
    throw 'WAR not found. Run mvn verify from the repository root first.'
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Set JAVA_HOME or pass -JavaHome pointing to JDK 8.'
}
$tomcat = (Resolve-Path -LiteralPath $TomcatHome).Path
if (-not (Test-Path -LiteralPath (Join-Path $tomcat 'bin\catalina.bat'))) {
    throw 'TomcatHome must be an extracted Tomcat 9 distribution.'
}
if ($Port -lt 1024 -or $Port -gt 65535) {
    throw 'Choose a TCP port between 1024 and 65535.'
}
$portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
try {
    $portProbe.Start()
} catch [Net.Sockets.SocketException] {
    throw "Port $Port is unavailable. Stop the existing server or choose another port before deployment."
} finally {
    $portProbe.Stop()
}
$base = Join-Path $root '.runtime\tomcat'
foreach ($directory in @('conf', 'logs', 'temp', 'webapps', 'work')) {
    New-Item -ItemType Directory -Path (Join-Path $base $directory) -Force | Out-Null
}
Copy-Item -Path (Join-Path $tomcat 'conf\*') -Destination (Join-Path $base 'conf') -Force
Copy-Item -LiteralPath (Join-Path $root 'runtime\tomcat\server.xml') -Destination (Join-Path $base 'conf\server.xml') -Force
Copy-Item -LiteralPath (Join-Path $root 'runtime\tomcat\context.xml') -Destination (Join-Path $base 'conf\context.xml') -Force
Copy-Item -LiteralPath $war -Destination (Join-Path $base 'webapps\wholesale.war') -Force
$env:JAVA_HOME = $JavaHome
$env:CATALINA_HOME = $tomcat
$env:CATALINA_BASE = $base
$env:CATALINA_OPTS = "-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo -Dhttp.port=$Port -Dhttp.address=127.0.0.1 " +
    "`"-Ddb.url=$DbUrl`" `"-Ddb.username=$DbUser`" `"-Ddb.password=$DbPassword`""
Write-Output "Opening http://127.0.0.1:$Port/wholesale/ (foreground; Ctrl+C stops this server)"
& (Join-Path $tomcat 'bin\catalina.bat') run
if ($LASTEXITCODE -ne 0) {
    throw "Tomcat exited with code $LASTEXITCODE. See .runtime\tomcat\logs."
}
