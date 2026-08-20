# CasinoTables - local isolated test server
#
# Spins up a throwaway Paper instance in the system temp folder, loads only
# CasinoTables plus the Vault/Essentials economy pair, waits for startup,
# runs a few console commands, stops, then deletes the whole thing.
#
# It never writes inside the real server folder - that folder is only read
# for the Paper jar and its libraries.
#
#   Usage: powershell -ExecutionPolicy Bypass -File test-server.ps1

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRoot = Split-Path -Parent (Split-Path -Parent $root)
$jar = Join-Path $root 'CasinoTables.jar'
if (-not (Test-Path $jar)) { throw "CasinoTables.jar not found. Run build.ps1 first." }

# Exact-path guard: refuse to touch anything other than this one temp folder.
$expected = [IO.Path]::GetFullPath((Join-Path $env:TEMP 'casinotables-test'))
$testRoot = [IO.Path]::GetFullPath((Join-Path $env:TEMP 'casinotables-test'))
if ($testRoot -ne $expected) { throw "Refusing to use unexpected path: $testRoot" }
if ($testRoot -like "$([IO.Path]::GetFullPath($serverRoot))*") {
    throw "Test folder must live outside the real server folder."
}
if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
New-Item -ItemType Directory -Force (Join-Path $testRoot 'plugins') | Out-Null

Write-Output ">>> Staging a throwaway server in $testRoot"
foreach ($d in 'libraries', 'versions', 'cache') {
    Copy-Item (Join-Path $serverRoot $d) $testRoot -Recurse
}
Copy-Item (Join-Path $serverRoot 'paper-26.2-65.jar') $testRoot
# Vault provides the API, Essentials provides the actual economy implementation.
Copy-Item (Join-Path $serverRoot 'plugins/VaultUnlocked-*.jar') (Join-Path $testRoot 'plugins')
Copy-Item (Join-Path $serverRoot 'plugins/EssentialsX-*.jar') (Join-Path $testRoot 'plugins')
Copy-Item $jar (Join-Path $testRoot 'plugins')

$utf8NoBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $testRoot 'eula.txt'), "eula=true" + [Environment]::NewLine, $utf8NoBom)
$properties = @'
server-port=25601
online-mode=false
enable-rcon=false
level-name=world
level-type=minecraft:flat
spawn-protection=0
view-distance=4
simulation-distance=4
max-players=6
motd=CasinoTables temporary test
'@
[IO.File]::WriteAllText((Join-Path $testRoot 'server.properties'), $properties, $utf8NoBom)

$info = New-Object Diagnostics.ProcessStartInfo
$info.FileName = (Get-Command java).Source
$info.Arguments = '-Xms512M -Xmx1536M -jar paper-26.2-65.jar nogui'
$info.WorkingDirectory = $testRoot
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardInput = $true
$info.RedirectStandardOutput = $true
$info.RedirectStandardError = $true
$process = New-Object Diagnostics.Process
$process.StartInfo = $info
if (-not $process.Start()) { throw 'Failed to start the temporary Paper server' }

Write-Output ">>> Booting"
$done = $false
while (-not $process.HasExited) {
    $line = $process.StandardOutput.ReadLine()
    if ($null -eq $line) { break }
    Write-Output $line
    if ($line -match 'Done \(') { $done = $true; break }
}
if (-not $done) {
    if (-not $process.HasExited) { $process.Kill() }
    $err = $process.StandardError.ReadToEnd()
    throw "Server never reached 'Done'. stderr: $err"
}

$newline = [char]10
$commandText = "plugins" + $newline + "version CasinoTables" + $newline +
    "casino help" + $newline + "casino casino" + $newline + "casino shape" + $newline +
    "stop" + $newline
$bytes = [Text.Encoding]::UTF8.GetBytes($commandText)
$process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
$process.StandardInput.BaseStream.Flush()
Write-Output ($process.StandardOutput.ReadToEnd())
$stderr = $process.StandardError.ReadToEnd()
if ($stderr) { Write-Output $stderr }
if (-not $process.WaitForExit(30000)) { $process.Kill(); throw 'Server did not stop in time' }

$code = $process.ExitCode
Remove-Item -LiteralPath $testRoot -Recurse -Force
Write-Output ">>> Temporary server removed. Exit code: $code"
if ($code -ne 0) { throw "Temporary server exited with code $code" }
