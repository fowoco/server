$ErrorActionPreference = "Stop"

$serverDirectory = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $serverDirectory ".env.local"

if (-not (Test-Path -LiteralPath $environmentFile)) {
    throw ".env.local is missing. Copy .env.example to .env.local and set local values."
}

Get-Content -LiteralPath $environmentFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
        return
    }

    $parts = $line -split "=", 2
    if ($parts.Count -ne 2 -or [string]::IsNullOrWhiteSpace($parts[0])) {
        throw "Invalid environment variable in .env.local: $line"
    }

    $name = $parts[0].Trim()
    $value = $parts[1].Trim()
    if ($value.Length -ge 2) {
        $isDoubleQuoted = $value.StartsWith('"') -and $value.EndsWith('"')
        $isSingleQuoted = $value.StartsWith("'") -and $value.EndsWith("'")
        if ($isDoubleQuoted -or $isSingleQuoted) {
            $value = $value.Substring(1, $value.Length - 2)
        }
    }
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

$env:SPRING_PROFILES_ACTIVE = "dev"
if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
    $env:DB_URL = "jdbc:postgresql://localhost:5432/fowoco_test"
}
if ([string]::IsNullOrWhiteSpace($env:DB_MIGRATION_USERNAME)) {
    $env:DB_MIGRATION_USERNAME = "postgres"
}
if ([string]::IsNullOrWhiteSpace($env:DB_RUNTIME_USERNAME)) {
    $env:DB_RUNTIME_USERNAME = "postgres"
}

$requiredVariables = @(
    "DB_MIGRATION_PASSWORD",
    "DB_RUNTIME_PASSWORD",
    "JWT_SECRET_BASE64",
    "DEMO_SEED_ADMIN_PASSWORD"
)

foreach ($variableName in $requiredVariables) {
    $value = [Environment]::GetEnvironmentVariable($variableName, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$variableName is required. Check .env.local."
    }
}

$exitCode = 0
Push-Location $serverDirectory
try {
    & .\gradlew.bat bootRun --args="--app.demo-seed.enabled=true"
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $exitCode
