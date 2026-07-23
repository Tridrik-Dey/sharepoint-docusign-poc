<#
.SYNOPSIS
    Loads KEY=VALUE pairs from a .env file into the current PowerShell
    process's environment, so subsequent commands in this same shell
    (e.g. mvn spring-boot:run) can see them.

.DESCRIPTION
    - Blank lines are ignored.
    - Lines whose first non-whitespace character is '#' are ignored as comments.
    - Each remaining line is split on the FIRST '=' only, so values containing
      '=' (e.g. base64 secrets) are preserved intact.
    - One layer of surrounding matching single or double quotes is trimmed
      from the value.
    - Variables are set at Process scope only (this shell and its child
      processes) - nothing is written to User or Machine environment.
    - Only variable NAMES are ever printed - values (which may be secrets)
      are never echoed to the console.

.PARAMETER Path
    Path to the .env file to load. Defaults to ".env" in the current directory.

.EXAMPLE
    .\scripts\load-env.ps1
    mvn spring-boot:run "-Dspring-boot.run.profiles=sharepoint-test" "-Dspring-boot.run.arguments=--server.port=8081"
#>
param(
    [string]$Path = ".env"
)

if (-not (Test-Path -LiteralPath $Path)) {
    Write-Host "No .env file found at '$Path' - nothing loaded."
    return
}

$loadedNames = @()

foreach ($rawLine in Get-Content -LiteralPath $Path) {
    $line = $rawLine.Trim()

    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    if ($line.StartsWith("#")) {
        continue
    }

    $separatorIndex = $line.IndexOf("=")
    if ($separatorIndex -lt 1) {
        # No '=' found, or line starts with '=' with an empty name - skip it.
        continue
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1).Trim()

    if ($value.Length -ge 2) {
        $firstChar = $value.Substring(0, 1)
        $lastChar = $value.Substring($value.Length - 1, 1)
        $wrappedInDoubleQuotes = ($firstChar -eq '"') -and ($lastChar -eq '"')
        $wrappedInSingleQuotes = ($firstChar -eq "'") -and ($lastChar -eq "'")
        if ($wrappedInDoubleQuotes -or $wrappedInSingleQuotes) {
            $value = $value.Substring(1, $value.Length - 2)
        }
    }

    if ([string]::IsNullOrEmpty($name)) {
        continue
    }

    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    $loadedNames += $name
}

Write-Host "Loaded $($loadedNames.Count) environment variable(s) from '$Path' into this process (values not shown):"
foreach ($name in $loadedNames) {
    Write-Host "  $name"
}
