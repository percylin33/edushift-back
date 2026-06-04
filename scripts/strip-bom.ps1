param(
    [string]$Path = "src"
)

$ErrorActionPreference = "Stop"

$files = Get-ChildItem -Path $Path -Recurse -Filter *.java
$total = $files.Count
$fixed = 0

foreach ($f in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $newBytes = New-Object byte[] ($bytes.Length - 3)
        [Array]::Copy($bytes, 3, $newBytes, 0, $bytes.Length - 3)
        [System.IO.File]::WriteAllBytes($f.FullName, $newBytes)
        $fixed++
        Write-Host ("FIXED: " + $f.FullName)
    }
}

Write-Host ""
Write-Host ("Archivos .java totales: " + $total)
Write-Host ("Archivos con BOM corregidos: " + $fixed)
