# CasinoTables - multi-version runtime test
#
# Boots a throwaway Paper server for each target Minecraft version, loads the
# plugin, waits for startup, then stops. Catches runtime breakage that the
# compile matrix cannot see (missing enum constants, changed entity behaviour,
# blocks that do not exist on older versions, ...).
#
# Server jars are downloaded once into a cache and reused. Each version needs
# its own libraries set, so Paper is allowed to fetch those on first boot.
#
#   Usage: powershell -ExecutionPolicy Bypass -File test-versions.ps1
#          powershell -ExecutionPolicy Bypass -File test-versions.ps1 -Versions 1.21,26.2

param(
    [string[]] $Versions = @('1.21', '1.21.8', '26.2')
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$serverRoot = Split-Path -Parent (Split-Path -Parent $root)
$jar = Join-Path $root 'CasinoTables.jar'
if (-not (Test-Path $jar)) { throw 'CasinoTables.jar not found. Run build.ps1 first.' }

$cache = Join-Path $env:TEMP 'paper-server-cache'
New-Item -ItemType Directory -Force $cache | Out-Null

function Get-PaperJar([string] $version) {
    $existing = Get-ChildItem $cache -Filter "paper-$version-*.jar" -ErrorAction SilentlyContinue |
        Sort-Object Name | Select-Object -Last 1
    if ($existing) { return $existing.FullName }

    $builds = Invoke-RestMethod "https://fill.papermc.io/v3/projects/paper/versions/$version/builds" `
        -TimeoutSec 30 -Headers @{ 'User-Agent' = 'CasinoTables-test/1.0' }
    $stable = $builds | Where-Object { $_.channel -eq 'STABLE' } | Select-Object -First 1
    if (-not $stable) { $stable = $builds | Select-Object -First 1 }
    $dl = $stable.downloads.'server:default'
    if (-not $dl) { throw "No server download for $version" }

    $target = Join-Path $cache $dl.name
    # Write-Host, not Write-Output: anything written to the success stream inside a
    # function becomes part of its return value and would corrupt the path.
    Write-Host "    downloading $($dl.name) ($([Math]::Round($dl.size / 1MB)) MB)"
    Invoke-WebRequest $dl.url -OutFile $target -UseBasicParsing -TimeoutSec 600
    $hash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne $dl.checksums.sha256.ToLower()) {
        Remove-Item $target -Force
        throw "Checksum mismatch for $($dl.name)"
    }
    return $target
}

$summary = @()
foreach ($version in $Versions) {
    Write-Output ""
    Write-Output "================ $version ================"
    $status = 'UNKNOWN'
    $detail = ''
    $testRoot = [IO.Path]::GetFullPath((Join-Path $env:TEMP "casinotables-mv-$version"))
    try {
        if ($testRoot -like "$([IO.Path]::GetFullPath($serverRoot))*") {
            throw 'Test folder must live outside the real server folder.'
        }
        $paper = Get-PaperJar $version

        if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
        New-Item -ItemType Directory -Force (Join-Path $testRoot 'plugins') | Out-Null
        Copy-Item $paper (Join-Path $testRoot 'server.jar')
        Copy-Item (Join-Path $serverRoot 'plugins/VaultUnlocked-*.jar') (Join-Path $testRoot 'plugins')
        Copy-Item (Join-Path $serverRoot 'plugins/EssentialsX-*.jar') (Join-Path $testRoot 'plugins')
        Copy-Item $jar (Join-Path $testRoot 'plugins')

        $utf8NoBom = New-Object Text.UTF8Encoding($false)
        [IO.File]::WriteAllText((Join-Path $testRoot 'eula.txt'), "eula=true`n", $utf8NoBom)
        $props = "server-port=0`nonline-mode=false`nenable-rcon=false`nlevel-type=minecraft:flat`n" +
                 "spawn-protection=0`nview-distance=4`nsimulation-distance=4`nmax-players=6`n"
        [IO.File]::WriteAllText((Join-Path $testRoot 'server.properties'), $props, $utf8NoBom)

        $info = New-Object Diagnostics.ProcessStartInfo
        $info.FileName = (Get-Command java).Source
        $info.Arguments = '-Xms512M -Xmx1536M -jar server.jar nogui'
        $info.WorkingDirectory = $testRoot
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardInput = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $info
        if (-not $process.Start()) { throw 'Failed to start Paper' }

        # Read BOTH pipes asynchronously. Draining only stdout deadlocks: once the
        # stderr pipe buffer fills, the server blocks on its next stderr write, stops
        # producing stdout, and a blocking ReadLine() waits forever - the deadline below
        # never gets a chance to run. That hung a run for 37 minutes on 1.21.9.
        $lines = New-Object Collections.Concurrent.ConcurrentQueue[string]
        $pump = {
            if ($null -ne $EventArgs.Data) { $Event.MessageData.Enqueue($EventArgs.Data) }
        }
        $outSub = Register-ObjectEvent $process OutputDataReceived -MessageData $lines -Action $pump
        $errSub = Register-ObjectEvent $process ErrorDataReceived -MessageData $lines -Action $pump
        $process.BeginOutputReadLine()
        $process.BeginErrorReadLine()

        $log = New-Object Collections.Generic.List[string]
        $done = $false
        $line = $null
        $deadline = (Get-Date).AddMinutes(8)
        while (-not $done -and (Get-Date) -lt $deadline) {
            if ($lines.TryDequeue([ref] $line)) {
                $log.Add($line)
                if ($line -match 'Done \(') { $done = $true }
            } elseif ($process.HasExited) {
                break
            } else {
                Start-Sleep -Milliseconds 100
            }
        }

        if ($done) {
            $bytes = [Text.Encoding]::UTF8.GetBytes("version CasinoTables`nstop`n")
            $process.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
            $process.StandardInput.BaseStream.Flush()
            $process.WaitForExit(60000) | Out-Null
        } elseif (-not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit(15000) | Out-Null
        }

        # Let the async handlers flush whatever arrived during shutdown, then collect it.
        Start-Sleep -Milliseconds 400
        while ($lines.TryDequeue([ref] $line)) { $log.Add($line) }
        Unregister-Event -SourceIdentifier $outSub.Name -ErrorAction SilentlyContinue
        Unregister-Event -SourceIdentifier $errSub.Name -ErrorAction SilentlyContinue

        $text = $log -join "`n"
        $enabled = $text -match '\[CasinoTables\] Enabling'
        # A self-disable during startup shows up as Disabling BEFORE the Done line.
        $doneIdx = ($log | Select-String 'Done \(' | Select-Object -First 1).LineNumber
        $disIdx = ($log | Select-String '\[CasinoTables\] Disabling' | Select-Object -First 1).LineNumber
        $selfDisabled = $enabled -and $disIdx -and $doneIdx -and ($disIdx -lt $doneIdx)
        $errors = @($log | Select-String 'CasinoTables' | Where-Object { $_ -match 'ERROR|Exception|SEVERE' })

        if (-not $done) { $status = 'BOOT FAIL'; $detail = ($log | Select-Object -Last 3) -join ' / ' }
        elseif (-not $enabled) { $status = 'NOT ENABLED'; $detail = 'plugin never enabled' }
        elseif ($selfDisabled) { $status = 'SELF-DISABLED'; $detail = 'disabled before Done' }
        elseif ($errors.Count -gt 0) { $status = "ERRORS ($($errors.Count))"; $detail = $errors[0].Line }
        else { $status = 'OK'; $detail = (($log | Select-String 'Done \(') | Select-Object -First 1).Line.Trim() }

        $logFile = Join-Path $cache "log-$version.txt"
        [IO.File]::WriteAllLines($logFile, $log)
        Write-Output "    log: $logFile"
    } catch {
        $status = 'SCRIPT ERROR'
        $detail = $_.Exception.Message
    } finally {
        if (Test-Path -LiteralPath $testRoot) {
            Remove-Item -LiteralPath $testRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Output "    -> $status  $detail"
    $summary += [pscustomobject]@{ Version = $version; Status = $status; Detail = $detail }
}

Write-Output ''
Write-Output '================ SUMMARY ================'
$summary | ForEach-Object { "{0,-10} {1,-16} {2}" -f $_.Version, $_.Status, $_.Detail }
if ($summary | Where-Object { $_.Status -ne 'OK' }) { exit 1 }
