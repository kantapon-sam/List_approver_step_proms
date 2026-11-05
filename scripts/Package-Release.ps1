param([string]$ProjectDirectory = (Split-Path $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$versionLine = Get-Content -LiteralPath (Join-Path $ProjectDirectory 'version.properties') | Where-Object { $_ -match '^app.version=' }
$version = ($versionLine -split '=',2)[1].Trim()
if ($version -notmatch '^(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})$') { throw 'Invalid version' }
$dist = Join-Path $ProjectDirectory 'dist'
$defaults = Join-Path $ProjectDirectory 'defaults'
$releaseDirectory = Join-Path $ProjectDirectory 'release'
New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
$archiveName = "List_approver_step_proms-$version.zip"
$portableName = "List_approver_step_proms-$version-windows.zip"
# Only these application files are allowed in the updater ZIP, including for v1.0.0 clients.
$files = @('List_approver_step_proms.jar','README.md','CHANGELOG.md')
$lockedLibraries = @(Get-Content -Raw -LiteralPath (Join-Path $ProjectDirectory 'dependencies.lock.json') | ConvertFrom-Json | Where-Object { $_.scope -ne 'tool' })
foreach ($dependency in $lockedLibraries) {
    $library = Join-Path $dist ('lib/' + $dependency.name)
    if ((Get-FileHash -LiteralPath $library).Hash.ToLowerInvariant() -ne $dependency.sha256) { throw 'Release dependency checksum mismatch' }
    $files += 'lib/' + $dependency.name
}
$expectedDefaults = @{
    'Login.txt' = "User,`nPassword,`n"
    'input.csv' = "Ref No,Project ID`n"
    'proms.properties' = ''
}
foreach ($name in $expectedDefaults.Keys) {
    $actual = [IO.File]::ReadAllText((Join-Path $defaults $name)).Replace("`r`n","`n")
    if ($actual -cne $expectedDefaults[$name]) { throw "Default $name must contain only its blank template." }
}
$jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $dist 'List_approver_step_proms.jar'))
try {
    $reader = [IO.StreamReader]::new($jar.GetEntry('META-INF/MANIFEST.MF').Open())
    try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($manifest -notmatch ('(?m)^Implementation-Version: ' + [regex]::Escape($version) + '\r?$')) { throw 'JAR version differs from release version.' }
    foreach ($name in $expectedDefaults.Keys) {
        $entry = $jar.GetEntry('com/java/myapp/resources/defaults/' + $name)
        if (-not $entry) { throw "Embedded default is missing: $name" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $embedded = $reader.ReadToEnd().Replace("`r`n","`n") } finally { $reader.Dispose() }
        if ($embedded -cne $expectedDefaults[$name]) { throw "Embedded $name must be blank." }
    }
    foreach ($entry in $jar.Entries) {
        if ($entry.FullName -match '\.example\.') { throw 'JAR contains an obsolete example file.' }
        if ($entry.FullName.EndsWith('.class')) {
            $stream = $entry.Open()
            try { $header = New-Object byte[] 8; if ($stream.Read($header,0,8) -ne 8 -or $header[6]*256 + $header[7] -ne 52) { throw 'Release contains non-Java-8 bytecode.' } } finally { $stream.Dispose() }
        }
    }
} finally { $jar.Dispose() }

$checksums = ''
foreach ($name in @($archiveName,$portableName)) {
    $temporary = Join-Path $releaseDirectory ([Guid]::NewGuid().ToString('N') + '.zip')
    $archivePath = Join-Path $releaseDirectory $name
    $zip = [IO.Compression.ZipFile]::Open($temporary,[IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($file in ($files | Sort-Object)) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $dist $file),$file,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
        if ($name -eq $portableName) {
            # Never copy working Login.txt/input.csv/settings from dist, even on a developer's machine.
            foreach ($file in ($expectedDefaults.Keys | Sort-Object)) {
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $defaults $file),$file,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
            }
        }
    } finally { $zip.Dispose() }
    Copy-Item -LiteralPath $temporary -Destination $archivePath -Force
    [IO.File]::Delete($temporary)
    $hash = (Get-FileHash -LiteralPath $archivePath).Hash.ToLowerInvariant()
    $size = (Get-Item -LiteralPath $archivePath).Length
    $checksums += "$hash  $name`n"
    if ($name -eq $archiveName) {
        $metadata = "app.version=$version`narchive.url=https://github.com/kantapon-sam/List_approver_step_proms/releases/download/v$version/$archiveName`narchive.sha256=$hash`narchive.size=$size`n"
        [IO.File]::WriteAllText((Join-Path $releaseDirectory 'update.properties'),$metadata,[Text.UTF8Encoding]::new($false))
    }
    Write-Output "Packaged v${version}: $name ($size bytes)."
}
[IO.File]::WriteAllText((Join-Path $releaseDirectory 'SHA256SUMS.txt'),$checksums,[Text.UTF8Encoding]::new($false))
