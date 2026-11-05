param([string]$Commit)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path $PSScriptRoot
$repo = 'kantapon-sam/List_approver_step_proms'
$previousToken = $env:GH_TOKEN
Push-Location $projectDirectory
try {
    if (-not $env:GH_TOKEN) {
        # Use the normal Git credential helper in memory only. Never print or save the returned credential.
        $env:GIT_TERMINAL_PROMPT = '0'
        $env:GCM_INTERACTIVE = 'Never'
        $credentialLines = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
        $passwordLine = @($credentialLines | Where-Object { $_.StartsWith('password=') }) | Select-Object -First 1
        if (-not $passwordLine) { throw 'Authenticate GitHub CLI or configure normal Git authentication first.' }
        $env:GH_TOKEN = $passwordLine.Substring(9)
        $credentialLines = $null; $passwordLine = $null
    }
    if (-not $Commit) { $Commit = (git rev-parse HEAD).Trim() }
    if ($Commit -notmatch '^[a-f0-9]{40}$') { throw 'Release requires an exact commit SHA.' }
    if (@(git status --porcelain).Count -ne 0) { throw 'Commit all task source changes before publishing a release.' }
    $version = ((Get-Content version.properties | Where-Object { $_ -match '^app.version=' }) -split '=',2)[1].Trim()
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid release version.' }
    $tag = 'v' + $version
    $existing = gh release view $tag --repo $repo --json isDraft,url 2>$null
    $exists = $LASTEXITCODE -eq 0
    if ($exists -and -not ($existing | ConvertFrom-Json).isDraft) {
        Write-Output "Release $tag is already published; existing assets remain unchanged."
        exit 0
    }
    & (Join-Path $PSScriptRoot 'Package-Release.ps1')
    $archive = "release/List_approver_step_proms-$version.zip"
    if (-not $exists) {
        gh release create $tag --repo $repo --target $Commit --title "List Approver Step PROMs $tag" --notes-file CHANGELOG.md --draft
        if ($LASTEXITCODE -ne 0) { throw 'Could not create release draft.' }
    }
    gh release upload $tag $archive release/update.properties release/SHA256SUMS.txt --repo $repo --clobber
    if ($LASTEXITCODE -ne 0) { throw 'Release upload failed; draft remains unpublished.' }
    gh release edit $tag --repo $repo --draft=false --latest
    if ($LASTEXITCODE -ne 0) { throw 'Could not publish completed release.' }
    gh release view $tag --repo $repo --json url,tagName,isDraft,assets
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify published release.' }
} finally {
    $env:GH_TOKEN = $previousToken
    Pop-Location
}
