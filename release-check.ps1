# CasinoTables - pre-release checklist
#
# Fails loudly on anything that would embarrass a public release: leftover
# hardcoded CJK player text, placeholder metadata, missing files.
#
#   Usage: powershell -ExecutionPolicy Bypass -File release-check.ps1
#
# This file is deliberately pure ASCII. Windows PowerShell 5.1 reads a BOM-less
# .ps1 as ANSI, so a literal CJK character here would arrive mangled and break
# the regex. CJK ranges are written as \u escapes instead.

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$problems = New-Object Collections.Generic.List[string]
$notes = New-Object Collections.Generic.List[string]

function Fail([string] $message) { $problems.Add($message) }
function Note([string] $message) { $notes.Add($message) }

# Source files are UTF-8 without BOM. Always say so explicitly, or 5.1 decodes
# them as ANSI and every CJK string turns into mojibake that matches nothing.
$utf8 = New-Object Text.UTF8Encoding($false)
function ReadLines([string] $path) { [IO.File]::ReadAllLines($path, $utf8) }

# --- 1. Hardcoded CJK inside Java string literals -------------------------
# Comments and switch-case aliases are allowed; anything else is a leaked string.
$cjk = [regex] '"[^"]*[\u4e00-\u9fa5][^"]*"'
$leaked = 0
Get-ChildItem (Join-Path $root 'src/main/java') -Recurse -Filter *.java | ForEach-Object {
    $file = $_
    $lineNo = 0
    foreach ($line in (ReadLines $file.FullName)) {
        $lineNo++
        $trimmed = $line.TrimStart()
        if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*')) { continue }
        if ($trimmed -match '^case ') { continue }              # command aliases
        if ($trimmed -match 'equalsIgnoreCase\(') { continue }  # alias comparisons
        if ($trimmed -match 'List\.of\("on"|List\.of\("off"') { continue }
        if ($cjk.IsMatch($line)) {
            $leaked++
            if ($leaked -le 15) {
                Fail ("leaked string  {0}:{1}  {2}" -f $file.Name, $lineNo, $trimmed.Trim())
            }
        }
    }
}
if ($leaked -gt 15) { Fail ("... and {0} more leaked strings" -f ($leaked - 15)) }
if ($leaked -eq 0) { Note 'no hardcoded CJK left in player-facing code' }

# --- 2. Stale references to the plugin this was extracted from ------------
$stale = 0
Get-ChildItem (Join-Path $root 'src') -Recurse -Include *.java, *.yml | ForEach-Object {
    $hit = Select-String -Path $_.FullName -Pattern 'GameArena|starmc|zhajinhua' -Encoding UTF8 -ErrorAction SilentlyContinue
    foreach ($h in $hit) {
        $stale++
        if ($stale -le 10) { Fail ("stale reference  {0}:{1}  {2}" -f $_.Name, $h.LineNumber, $h.Line.Trim()) }
    }
}
if ($stale -gt 10) { Fail ("... and {0} more stale references" -f ($stale - 10)) }
if ($stale -eq 0) { Note 'no leftover references to the original plugin' }

# --- 3. Language files ----------------------------------------------------
$langDir = Join-Path $root 'src/main/resources/lang'
foreach ($code in 'en_US', 'zh_CN') {
    $path = Join-Path $langDir "$code.yml"
    if (-not (Test-Path $path)) { Fail "missing language file $code.yml"; continue }
    $lines = ReadLines $path
    if (($lines -join "`n") -match "`t") { Fail "$code.yml contains a tab (YAML forbids tabs for indentation)" }
    Note ("{0}.yml: {1} lines" -f $code, $lines.Count)
}
$en = Join-Path $langDir 'en_US.yml'
if (Test-Path $en) {
    $bad = @(ReadLines $en | Where-Object { $_ -match '[\u4e00-\u9fa5]' })
    if ($bad.Count -gt 0) {
        Fail ("en_US.yml still contains CJK on {0} line(s), first: {1}" -f $bad.Count, $bad[0].Trim())
    } else {
        Note 'en_US.yml is free of CJK'
    }
}

# --- 3b. YAML 1.1 boolean keys --------------------------------------------
# SnakeYAML (what Bukkit uses) is YAML 1.1: a bare on/off/yes/no/y/n/true/false
# key parses as a BOOLEAN, so "peek.self.on" silently becomes "peek.self.true"
# and the lookup misses. Quote them or rename them.
foreach ($code in 'en_US', 'zh_CN') {
    $path = Join-Path $langDir "$code.yml"
    if (-not (Test-Path $path)) { continue }
    $lineNo = 0
    foreach ($line in (ReadLines $path)) {
        $lineNo++
        if ($line -match '^\s*(on|off|yes|no|y|n|true|false)\s*:') {
            Fail ("{0}.yml:{1} bare '{2}:' key parses as a boolean in YAML 1.1 - rename it" -f $code, $lineNo, $matches[1])
        }
    }
}

# --- 4. Metadata ----------------------------------------------------------
$meta = [IO.File]::ReadAllText((Join-Path $root 'src/main/resources/plugin.yml'), $utf8)
if ($meta -match 'author:\s*TODO') { Fail 'plugin.yml: author is still TODO' }
if ($meta -match 'casinotables/CasinoTables') { Fail 'plugin.yml: website points at a placeholder repo' }

foreach ($f in 'README.md', 'LICENSE') {
    if (-not (Test-Path (Join-Path $root $f))) { Fail "missing $f" }
}

# --- 5. Build leftovers ---------------------------------------------------
if (Test-Path (Join-Path $root 'lang-fragments')) {
    Fail 'lang-fragments/ still present - merge it into the language files and delete it'
}

# --- report ---------------------------------------------------------------
Write-Output ''
foreach ($n in $notes) { Write-Output "  ok    $n" }
Write-Output ''
if ($problems.Count -eq 0) {
    Write-Output 'RELEASE CHECK PASSED'
    exit 0
}
Write-Output ("RELEASE CHECK FAILED - {0} problem(s):" -f $problems.Count)
foreach ($p in $problems) { Write-Output "  FAIL  $p" }
exit 1
