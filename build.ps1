# CasinoTables - local build
#
# Builds entirely on this machine: local JDK plus the paper-api jar that already
# sits in the server folder's libraries directory. No network, no remote host.
#
#   Usage:  powershell -ExecutionPolicy Bypass -File build.ps1
#   Output: CasinoTables.jar next to this script

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
# Dependencies are read (never written) from the local server folder.
$serverRoot = Split-Path -Parent (Split-Path -Parent $root)
$libraries = Join-Path $serverRoot 'libraries'
$pluginsDir = Join-Path $serverRoot 'plugins'

$classes = Join-Path $root 'build/classes'
$testClasses = Join-Path $root 'build/test-classes'
$jar = Join-Path $root 'CasinoTables.jar'

foreach ($tool in 'javac', 'java', 'jar') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "$tool not found on PATH. Install JDK 21 or newer and try again."
    }
}
if (-not (Test-Path $libraries)) { throw "Dependency folder not found: $libraries" }

# ---------- classpath ----------
$api = Get-ChildItem (Join-Path $libraries 'io/papermc/paper/paper-api') -Recurse -Filter *.jar |
    Select-Object -First 1
if (-not $api) { throw "paper-api not found under $libraries" }

$libJars = Get-ChildItem $libraries -Recurse -Filter *.jar | ForEach-Object { $_.FullName }
$vault = Get-ChildItem $pluginsDir -Filter 'Vault*.jar' -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName }
$cp = (@($api.FullName) + $libJars + $vault | Where-Object { $_ }) -join ';'

# ---------- compile main sources ----------
foreach ($dir in $classes, $testClasses) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force $dir | Out-Null
}

$sources = Get-ChildItem (Join-Path $root 'src/main/java') -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
Write-Output ">>> Compiling $($sources.Count) source files"
# javac's @argsfile must be BOM-free, so write it with a plain UTF-8 encoder.
$argsFile = Join-Path $env:TEMP 'casinotables-sources.txt'
[IO.File]::WriteAllLines($argsFile, $sources, (New-Object Text.UTF8Encoding($false)))
& javac -encoding UTF-8 --release 21 -Xlint:all,-classfile,-serial,-this-escape,-deprecation,-removal -cp $cp -d $classes "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

# ---------- compile and run self-tests ----------
$tests = Get-ChildItem (Join-Path $root 'src/test/java') -Recurse -Filter *.java -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName }
if ($tests) {
    Write-Output ">>> Compiling self-tests"
    [IO.File]::WriteAllLines($argsFile, $tests, (New-Object Text.UTF8Encoding($false)))
    $testCp = $cp + ';' + $classes
    & javac -encoding UTF-8 --release 21 -cp $testCp -d $testClasses "@$argsFile"
    if ($LASTEXITCODE -ne 0) { throw "Test sources failed to compile ($LASTEXITCODE)" }

    Write-Output ">>> Running self-tests"
    # Language files must be on the test classpath so the key-parity test can read them.
    $runCp = $testCp + ';' + $testClasses + ';' + (Join-Path $root 'src/main/resources')
    # Pass the project root so the self-test can scan the sources for message keys.
    & java -cp $runCp io.github.casinotables.tests.SelfTest $root
    if ($LASTEXITCODE -ne 0) { throw "Self-tests failed ($LASTEXITCODE)" }
}
Remove-Item $argsFile -ErrorAction SilentlyContinue

# ---------- package ----------
Copy-Item (Join-Path $root 'src/main/resources/*') $classes -Recurse -Force
if (Test-Path $jar) { Remove-Item $jar -Force }
& jar --create --file $jar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar packaging failed" }

$size = (Get-Item $jar).Length
Write-Output ">>> Done: $jar ($size bytes)"
