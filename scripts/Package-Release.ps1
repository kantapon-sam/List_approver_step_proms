param([string]$ProjectDirectory = (Split-Path $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$versionLine = Get-Content -LiteralPath (Join-Path $ProjectDirectory 'version.properties') | Where-Object { $_ -match '^app.version=' }
$version = ($versionLine -split '=',2)[1].Trim()
if ($version -notmatch '^(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})$') { throw 'Invalid version' }
$dist = Join-Path $ProjectDirectory 'dist'
$releaseDirectory = Join-Path $ProjectDirectory 'release'
New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
$archiveName = "List_approver_step_proms-$version.zip"
$archivePath = Join-Path $releaseDirectory $archiveName
$temporary = Join-Path $releaseDirectory ([Guid]::NewGuid().ToString('N') + '.zip')
# Explicit allowlist: never package Login.txt, input.csv, live settings, output, driver, logs or Git metadata.
$files = @('List_approver_step_proms.jar','README.md','CHANGELOG.md','Login.example.txt','input.example.csv','proms.example.properties')
$lockedLibraries = @(Get-Content -Raw -LiteralPath (Join-Path $ProjectDirectory 'dependencies.lock.json') | ConvertFrom-Json | Where-Object { $_.scope -ne 'tool' })
foreach ($dependency in $lockedLibraries) {
    $library = Join-Path $dist ('lib/' + $dependency.name)
    if ((Get-FileHash -LiteralPath $library).Hash.ToLowerInvariant() -ne $dependency.sha256) { throw 'Release dependency checksum mismatch' }
    $files += 'lib/' + $dependency.name
}
$template = [IO.File]::ReadAllText((Join-Path $dist 'Login.example.txt')).Trim()
if ($template -notmatch '^User,\r?\nPassword,$') { throw 'Login example must contain blank User and Password only.' }
$jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $dist 'List_approver_step_proms.jar'))
try {
    $reader = [IO.StreamReader]::new($jar.GetEntry('META-INF/MANIFEST.MF').Open())
    try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($manifest -notmatch ('(?m)^Implementation-Version: ' + [regex]::Escape($version) + '\r?$')) { throw 'JAR version differs from release version.' }
    $reader = [IO.StreamReader]::new($jar.GetEntry('com/java/myapp/resources/Login.example.txt').Open())
    try { $embedded = $reader.ReadToEnd().Trim() } finally { $reader.Dispose() }
    if ($embedded -notmatch '^User,\r?\nPassword,$') { throw 'Embedded Login example is not blank.' }
    foreach ($entry in $jar.Entries) {
        if ($entry.FullName.EndsWith('.class')) {
            $stream = $entry.Open()
            try { $header = New-Object byte[] 8; if ($stream.Read($header,0,8) -ne 8 -or $header[6]*256 + $header[7] -ne 52) { throw 'Release contains non-Java-8 bytecode.' } } finally { $stream.Dispose() }
        }
    }
} finally { $jar.Dispose() }
$zip = [IO.Compression.ZipFile]::Open($temporary,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($name in ($files | Sort-Object)) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $dist $name),$name,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose() }
Copy-Item -LiteralPath $temporary -Destination $archivePath -Force
[IO.File]::Delete($temporary)
$hash = (Get-FileHash -LiteralPath $archivePath).Hash.ToLowerInvariant()
$size = (Get-Item -LiteralPath $archivePath).Length
$metadata = "app.version=$version`narchive.url=https://github.com/kantapon-sam/List_approver_step_proms/releases/download/v$version/$archiveName`narchive.sha256=$hash`narchive.size=$size`n"
[IO.File]::WriteAllText((Join-Path $releaseDirectory 'update.properties'),$metadata,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $releaseDirectory 'SHA256SUMS.txt'),"$hash  $archiveName`n",[Text.UTF8Encoding]::new($false))
Write-Output "Packaged v${version}: $archiveName ($size bytes); no credential/runtime files included."
