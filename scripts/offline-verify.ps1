# Сборка и тесты без сети из offline/m2 (architecture.md §3).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $pom = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'pom.xml')
    $pgv = [regex]::Match($pom, '<pg\.binaries\.version>(.*?)</pg\.binaries\.version>').Groups[1].Value
    $dir = Join-Path $root "offline/m2/io/zonky/test/postgres/embedded-postgres-binaries-windows-amd64/$pgv"
    if (-not (Test-Path (Join-Path $dir '*.jar'))) {
        [Console]::Error.WriteLine("Нет jar в $dir : сначала scripts/prepare-offline.ps1")
        $code = 1
    } else {
        & mvn -B -o "-Dmaven.repo.local=offline/m2" verify
        $code = $LASTEXITCODE
    }
} finally {
    Pop-Location
}
exit $code
