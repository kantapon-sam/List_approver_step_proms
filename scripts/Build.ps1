param([string]$JavaHome = $env:JAVA_HOME, [string]$FixtureDriver)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME or pass -JavaHome pointing to JDK 8.' }
$javaExe = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) { $javaExe = Join-Path $JavaHome 'bin/java' }
$versionText = & $javaExe -version 2>&1 | Out-String
if ($versionText -notmatch 'version "1\.8\.') { throw 'JDK 8 is required. Other Java versions are not allowed.' }
$projectDirectory = Split-Path $PSScriptRoot
& (Join-Path $PSScriptRoot 'Prepare-Libraries.ps1') -ProjectDirectory $projectDirectory
$separator = [IO.Path]::PathSeparator
$classpath = (Join-Path $projectDirectory 'build/tools/ant-1.10.15.jar') + $separator + (Join-Path $projectDirectory 'build/tools/ant-launcher-1.10.15.jar')
Push-Location $projectDirectory
try {
    $targets = @('-Dbuild.dir=build/java8', 'test')
    if ($FixtureDriver) { $targets += 'test-fixture'; $targets += ('-Dfixture.driver=' + $FixtureDriver) }
    $targets += 'jar'
    & $javaExe -cp $classpath org.apache.tools.ant.Main @targets
    if ($LASTEXITCODE -ne 0) { throw 'Java 8 build or tests failed.' }
    & $javaExe -jar dist/List_approver_step_proms.jar --version
    if ($LASTEXITCODE -ne 0) { throw 'Packaged JAR could not start on Java 8.' }
} finally { Pop-Location }
