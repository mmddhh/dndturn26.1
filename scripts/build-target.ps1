param(
    [Parameter(Mandatory = $true)]
    [string] $Target
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetsRoot = Join-Path $repoRoot 'targets'

if ([string]::IsNullOrWhiteSpace($Target) -or
    [System.IO.Path]::IsPathRooted($Target) -or
    $Target.IndexOfAny([System.IO.Path]::GetInvalidFileNameChars()) -ge 0 -or
    $Target.Contains('/') -or
    $Target.Contains('\') -or
    $Target.Contains('..')) {
    throw "Target must be a direct directory name under targets/: $Target"
}

$targetDirectory = Join-Path $targetsRoot $Target

if (-not (Test-Path -LiteralPath $targetDirectory -PathType Container)) {
    throw "Target directory not found: $targetDirectory"
}

$wrapper = Join-Path $targetDirectory 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) {
    throw "Target wrapper not found: $wrapper"
}

Push-Location $targetDirectory
try {
    & .\gradlew.bat :clean :build :stageVerifiedJar --console plain --no-daemon
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
