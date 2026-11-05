param([string]$ProjectDirectory = (Split-Path $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$dependencies = Get-Content -Raw -LiteralPath (Join-Path $ProjectDirectory 'dependencies.lock.json') | ConvertFrom-Json
foreach ($dependency in $dependencies) {
    $folder = if ($dependency.scope -eq 'tool') { 'build/tools' } else { 'lib' }
    $directory = Join-Path $ProjectDirectory $folder
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $target = Join-Path $directory $dependency.name
    if ((Test-Path -LiteralPath $target) -and (Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant() -eq $dependency.sha256) { continue }
    $temporary = Join-Path $directory ([Guid]::NewGuid().ToString('N') + '.download')
    try {
        Write-Output ('Downloading verified dependency: ' + $dependency.name)
        Invoke-WebRequest -Uri $dependency.url -OutFile $temporary -TimeoutSec 120
        if ($dependency.entry) {
            if ((Get-FileHash -LiteralPath $temporary).Hash.ToLowerInvariant() -ne $dependency.archiveSha256) { throw 'Dependency archive checksum mismatch' }
            $archive = [IO.Compression.ZipFile]::OpenRead($temporary)
            try {
                $entry = $archive.GetEntry($dependency.entry)
                if (-not $entry) { throw 'Required Selenium entry is missing' }
                $extracted = $temporary + '.jar'
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $extracted)
            } finally { $archive.Dispose() }
            if ((Get-FileHash -LiteralPath $extracted).Hash.ToLowerInvariant() -ne $dependency.sha256) { throw 'Extracted library checksum mismatch' }
            Copy-Item -LiteralPath $extracted -Destination $target -Force
        } else {
            if ((Get-FileHash -LiteralPath $temporary).Hash.ToLowerInvariant() -ne $dependency.sha256) { throw 'Dependency checksum mismatch' }
            Copy-Item -LiteralPath $temporary -Destination $target -Force
        }
    } finally {
        if (Test-Path -LiteralPath $temporary) { [IO.File]::Delete($temporary) }
        if (Test-Path -LiteralPath ($temporary + '.jar')) { [IO.File]::Delete($temporary + '.jar') }
    }
}
Write-Output 'All runtime libraries and Ant tools match dependencies.lock.json.'
