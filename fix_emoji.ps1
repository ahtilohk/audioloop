$files = @(
    "app/src/main/java/com/example/audioloop/ui/PlaylistSheet.kt",
    "app/src/main/java/com/example/audioloop/ui/AudioLoopMainScreen.kt",
    "app/src/main/java/com/example/audioloop/MainActivity.kt",
    "app/src/main/java/com/example/audioloop/ui/Dialogs.kt",
    "app/src/main/java/com/example/audioloop/ui/FileItem.kt",
    "app/src/main/java/com/example/audioloop/ui/CategoryManagementSheet.kt"
)

function Fix-Emoji {
    param($content)
    # Build search/replace for each emoji
    #  U+1F3B6: garbled as ŸŸŽ (U+0178 U+0178 U+017D U+00B6)
    $content = $content.Replace(([string][char]0x0178+[string][char]0x0178+[string][char]0x017D+[string][char]0x00B6), [char]::ConvertFromUtf32(0x1F3B6))
    #  U+1F3B5: garbled as ŸŸŽµ (U+0178 U+0178 U+017D U+00B5)
    $content = $content.Replace(([string][char]0x0178+[string][char]0x0178+[string][char]0x017D+[string][char]0x00B5), [char]::ConvertFromUtf32(0x1F3B5))
    #  U+1F500: garbled (U+0178 U+0178 U+201D U+20AC) or (U+00F0 U+0178 U+201D U+20AC)
    $content = $content.Replace(([string][char]0x0178+[string][char]0x0178+[string][char]0x201D+[string][char]0x20AC), [char]::ConvertFromUtf32(0x1F500))
    $content = $content.Replace(([string][char]0x00F0+[string][char]0x0178+[string][char]0x201D+[string][char]0x20AC), [char]::ConvertFromUtf32(0x1F500))
    #  U+1F319
    $content = $content.Replace(([string][char]0x0178+[string][char]0x0178+[string][char]0x0152+[string][char]0x2122), [char]::ConvertFromUtf32(0x1F319))
    $content = $content.Replace(([string][char]0x00F0+[string][char]0x0178+[string][char]0x0152+[string][char]0x2122), [char]::ConvertFromUtf32(0x1F319))
    #  U+1F4CB
    $content = $content.Replace(([string][char]0x0178+[string][char]0x0178+[string][char]0x201C+[string][char]0x2039), [char]::ConvertFromUtf32(0x1F4CB))
    $content = $content.Replace(([string][char]0x00F0+[string][char]0x0178+[string][char]0x201C+[string][char]0x2039), [char]::ConvertFromUtf32(0x1F4CB))
    #  U+1F4C2
    $content = $content.Replace(([string][char]0x0178+[string][char]0x0178+[string][char]0x201C+[string][char]0x201A), [char]::ConvertFromUtf32(0x1F4C2))
    #  U+1F50A
    $content = $content.Replace(([string][char]0x0178+[string][char]0x0178+[string][char]0x201D+[string][char]0x0160), [char]::ConvertFromUtf32(0x1F50A))
    #  U+2795: E2 9E 95 garbled
    $content = $content.Replace(([string][char]0x00E2+[string][char]0x017E+[string][char]0x2022), [char]0x2795)
    $content = $content.Replace(([string][char]0x00E2+[string][char]0x0178+[string][char]0x2022), [char]0x2795)
    #  em dash U+2014: E2 80 94
    $content = $content.Replace(([string][char]0x00E2+[string][char]0x20AC+[string][char]0x201D), [char]0x2014)
    #  en dash U+2013: E2 80 93
    $content = $content.Replace(([string][char]0x00E2+[string][char]0x20AC+[string][char]0x201C), [char]0x2013)
    #  box drawing U+2500: E2 94 80
    $content = $content.Replace(([string][char]0x00E2+[string][char]0x201D+[string][char]0x20AC), [char]0x2500)
    return $content
}

foreach ($fPath in $files) {
    if (-not (Test-Path $fPath)) { Write-Host "SKIP: $fPath"; continue }
    $fullPath = (Resolve-Path $fPath).Path
    $orig = [IO.File]::ReadAllText($fullPath, [Text.Encoding]::UTF8)
    $fixed = Fix-Emoji $orig
    [IO.File]::WriteAllText($fullPath, $fixed, (New-Object Text.UTF8Encoding $false))
    if ($fixed -ne $orig) { Write-Host "FIXED: $(Split-Path $fPath -Leaf)" }
    else { Write-Host "clean: $(Split-Path $fPath -Leaf)" }
}
Write-Host "All done."
