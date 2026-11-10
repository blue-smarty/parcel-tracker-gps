# Installs the Parcel Tracker backend (and web portal) natively on Windows.
# Requires JDK 17+, Maven and PostgreSQL 16+ (psql on PATH). No Docker needed.
# Usage: powershell -ExecutionPolicy Bypass -File installer\install.ps1 [-InstallDir <path>]
param(
    [string]$InstallDir = (Join-Path $env:USERPROFILE '.parcel-tracker'),
    [string]$DbName = 'parceltracker',
    [string]$DbUser = 'parceltracker',
    [string]$PgAdmin = 'postgres',
    [string]$PgHost = 'localhost'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot

foreach ($tool in 'java', 'mvn', 'psql') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Missing required tool: $tool" }
}
$javaVersion = (& java -version 2>&1 | Select-Object -First 1).ToString()
if ($javaVersion -notmatch '"(\d+)' -or [int]$Matches[1] -lt 17) { throw 'JDK 17 or newer is required.' }

function New-Secret {
    $bytes = New-Object byte[] 24
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    ([Convert]::ToBase64String($bytes)) -replace '[/+=]', ''
}

function Read-Username([string]$Prompt) {
    do {
        $value = Read-Host $Prompt
        if ($value -cmatch '^[a-zA-Z0-9._-]{1,255}$') { return $value }
        Write-Host 'Use 1-255 letters, digits, dots, underscores or hyphens.'
    } while ($true)
}

function Read-Password([string]$Prompt) {
    do {
        $secureValue = Read-Host -AsSecureString $Prompt
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
        try {
            $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        }
        if ($value -cmatch '^[\x20-\x7E]{12,72}$' -and $value -cmatch '[\x21-\x7E]') { return $value }
        Write-Host 'Use 12-72 printable ASCII characters, including at least one non-space character.'
    } while ($true)
}

function Read-OptionalTomTomApiKey {
    $secureValue = Read-Host -AsSecureString 'TomTom API key (optional; press Enter to skip or keep the current key)'
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$envFile = Join-Path $InstallDir 'parcel-tracker.env.ps1'

if (Test-Path $envFile) {
    Write-Host "Existing install found in $InstallDir; keeping its configuration and rebuilding the jar."
    $tomtomApiKey = Read-OptionalTomTomApiKey
    if (-not [string]::IsNullOrEmpty($tomtomApiKey)) {
        $escapedApiKey = $tomtomApiKey.Replace("'", "''")
        Add-Content -Path $envFile -Value "`$env:TOMTOM_API_KEY = '$escapedApiKey'" -Encoding ASCII
        & icacls $envFile /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
        Write-Host "TomTom API key saved to $envFile."
    }
} else {
    $adminUsername = Read-Username 'Admin username'
    $adminPassword = Read-Password 'Admin password'
    do {
        $courierUsername = Read-Username 'Courier username (courier ID)'
        if ($courierUsername -ceq $adminUsername) {
            Write-Host 'Courier username must differ from the admin username.'
        }
    } while ($courierUsername -ceq $adminUsername)
    $courierPassword = Read-Password 'Courier password'
    $tomtomApiKey = Read-OptionalTomTomApiKey
    $dbPassword = New-Secret
    $secure = Read-Host -AsSecureString "Password for PostgreSQL user '$PgAdmin'"
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    try {
        $exists = & psql -h $PgHost -U $PgAdmin -d postgres -tAc "select 1 from pg_roles where rolname='$DbUser'"
        if ($LASTEXITCODE -ne 0) { throw "Cannot connect to PostgreSQL as '$PgAdmin'." }
        if ($exists -eq '1') { throw "Database role '$DbUser' already exists; choose another -DbUser." }
        & psql -h $PgHost -U $PgAdmin -d postgres -v ON_ERROR_STOP=1 -c "create role `"$DbUser`" login password '$dbPassword'"
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the database role.' }
        & psql -h $PgHost -U $PgAdmin -d postgres -v ON_ERROR_STOP=1 -c "create database `"$DbName`" owner `"$DbUser`""
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the database.' }
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }

    @"
`$env:DATABASE_URL = 'jdbc:postgresql://${PgHost}:5432/$DbName'
`$env:DATABASE_USERNAME = '$DbUser'
`$env:DATABASE_PASSWORD = '$dbPassword'
`$env:PARCEL_ADMIN_USERNAME = '$($adminUsername.Replace("'", "''"))'
`$env:PARCEL_ADMIN_PASSWORD = '$($adminPassword.Replace("'", "''"))'
`$env:PARCEL_BOOTSTRAP_COURIER_ID = '$($courierUsername.Replace("'", "''"))'
`$env:PARCEL_BOOTSTRAP_PASSWORD = '$($courierPassword.Replace("'", "''"))'
"@ | Set-Content -Path $envFile -Encoding ASCII
    if (-not [string]::IsNullOrEmpty($tomtomApiKey)) {
        $escapedApiKey = $tomtomApiKey.Replace("'", "''")
        Add-Content -Path $envFile -Value "`$env:TOMTOM_API_KEY = '$escapedApiKey'" -Encoding ASCII
    }
    & icacls $envFile /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
    Write-Host "Credentials saved to $envFile (current user only)."
}

Write-Host 'Building backend...'
Push-Location (Join-Path $repo 'backend')
try {
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed.' }
} finally { Pop-Location }
Copy-Item (Join-Path $repo 'backend\target\parcel-tracker-backend-1.0.0.jar') (Join-Path $InstallDir 'parcel-tracker-backend.jar') -Force

@'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $dir 'parcel-tracker.env.ps1')
& java -jar (Join-Path $dir 'parcel-tracker-backend.jar')
'@ | Set-Content -Path (Join-Path $InstallDir 'start.ps1') -Encoding ASCII

Write-Host ''
Write-Host "Installed to $InstallDir"
Write-Host "Start:   powershell -File `"$InstallDir\start.ps1`""
Write-Host 'Website: http://localhost:8080'
Write-Host "Admin login and courier credentials: see $envFile"
