<#
.SYNOPSIS
    Lists jars in a mods folder that ship experience-like fluid textures.

.DESCRIPTION
    A mod that registers a fluid almost always ships a still/flowing texture pair for it, so the
    texture names are the cheapest way to find "which of my 200 mods might hold player experience".
    This is a *candidate* finder, not proof: a hit still has to be confirmed in game with
    /data get block <x> <y> <z> Fluid, and the rate still has to be measured.
    See the built-in datapack SOP under docs/ for the rest of the procedure.

.PARAMETER ModsDirectory
    A mods folder, e.g. <instance>\minecraft\mods.

.PARAMETER Pattern
    Regex matched against texture entry names. Defaults to experience-like words.

.EXAMPLE
    .\tools\find-xp-fluids.ps1 -ModsDirectory 'D:\Minecraft\...\minecraft\mods'
#>
param(
    [Parameter(Mandatory = $true)]
    [string] $ModsDirectory,

    [string] $Pattern = '(?i)(xp|experience|juice|essence)'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $ModsDirectory)) {
    Write-Error "no such directory: $ModsDirectory"
    exit 1
}

$jars = Get-ChildItem $ModsDirectory -Filter '*.jar' -ErrorAction SilentlyContinue
Write-Output "scanning $($jars.Count) jar(s) in $ModsDirectory"

$candidates = 0
foreach ($jar in $jars) {
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    } catch {
        Write-Output "skip (unreadable): $($jar.Name)"
        continue
    }
    try {
        $hits = @()
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName
            if ($name -match '^assets/[^/]+/textures/' -and $name -match $Pattern -and $name -match '(?i)(still|flow)') {
                $hits += $name
            }
        }
        if ($hits.Count -gt 0) {
            $candidates++
            Write-Output "=== $($jar.Name)"
            foreach ($hit in $hits) {
                Write-Output "    $hit"
            }
        }
    } finally {
        $zip.Dispose()
    }
}

Write-Output "candidate jars: $candidates"
Write-Output 'next: confirm the fluid id in game, then measure xp_per_bucket - see the built-in datapack SOP under docs/'
