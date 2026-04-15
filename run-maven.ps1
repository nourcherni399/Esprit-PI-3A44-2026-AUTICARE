# Lance Maven Wrapper sans installation globale de Maven.
# Usage : .\run-maven.ps1 compile   |   .\run-maven.ps1 javafx:run
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not $env:JAVA_HOME) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd -and $javaCmd.Source) {
        $bin = Split-Path -Parent $javaCmd.Source
        $env:JAVA_HOME = Split-Path -Parent $bin
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    Write-Host "JAVA_HOME n'est pas defini et 'java' est introuvable dans le PATH." -ForegroundColor Red
    Write-Host "Installez un JDK 17+ (Eclipse Temurin, Oracle, etc.) puis soit :" -ForegroundColor Yellow
    Write-Host "  - ajoutez le dossier bin du JDK au PATH, soit" -ForegroundColor Yellow
    Write-Host "  - definissez JAVA_HOME vers le dossier d'installation du JDK (ex. C:\Program Files\Java\jdk-17)." -ForegroundColor Yellow
    exit 1
}

$mvnw = Join-Path $root "mvnw.cmd"
if (-not (Test-Path $mvnw)) {
    Write-Host "mvnw.cmd introuvable dans $root" -ForegroundColor Red
    exit 1
}

& $mvnw @args
exit $LASTEXITCODE
