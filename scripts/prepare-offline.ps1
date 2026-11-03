# Подготовка офлайн-репозитория: нужен интернет (architecture.md §3).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & mvn -B "-Dmaven.repo.local=offline/m2" verify
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $code
