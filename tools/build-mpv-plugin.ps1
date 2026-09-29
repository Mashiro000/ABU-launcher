param(
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64')]
    [string]$Abi,
    [string]$Version = '1.0.0',
    [string]$OutputDirectory = "$PSScriptRoot\..\dist\plugins"
)

$ErrorActionPreference = 'Stop'
$wrapperAar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\dev.jdtech.mpv\libmpv\1.0.0" -Recurse -Filter '*.aar' |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $wrapperAar) { throw 'MPV JNI wrapper AAR is missing. Build the Android app first.' }

$stage = Join-Path $env:TEMP ("abu-mpv-lgpl-" + [guid]::NewGuid().ToString('N'))
$nativeDir = Join-Path $stage "lib\$Abi"
New-Item -ItemType Directory -Force $nativeDir | Out-Null
try {
    $wrapperZip = Join-Path $stage 'wrapper.zip'
    Copy-Item -LiteralPath $wrapperAar -Destination $wrapperZip
    Expand-Archive -LiteralPath $wrapperZip -DestinationPath (Join-Path $stage 'wrapper')
    # Keep the complete, mutually compatible native set. libplayer is linked against
    # the FFmpeg libraries from this same AAR and cannot be mixed with another build.
    Get-ChildItem -LiteralPath (Join-Path $stage "wrapper\jni\$Abi") -Filter '*.so' |
        Copy-Item -Destination $nativeDir

    @{
        schemaVersion = 1
        id = 'com.limi.player.mpv'
        name = 'MPV 播放器内核'
        version = $Version
        author = 'ABU Launcher / media-kit contributors'
        description = '按需安装的 MPV 播放器内核。默认播放器仍为系统 Media3。'
        kind = 'player'
        abi = $Abi
        permissions = @(
            @{ id = 'media.playback'; title = '播放媒体地址'; sensitive = $false },
            @{ id = 'network.media'; title = '访问媒体服务器'; sensitive = $true }
        )
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $stage 'manifest.json') -Encoding utf8NoBOM

    @"
JNI wrapper source: https://github.com/jarnedemeulemeester/libmpv-android/tree/v1.0.0
The package contains the complete, mutually compatible native library set from
dev.jdtech.mpv:libmpv:1.0.0. See the upstream project and its bundled notices
for the licenses and corresponding source of mpv, FFmpeg and the JNI wrapper.
"@ | Set-Content -LiteralPath (Join-Path $stage 'THIRD_PARTY_NOTICES.txt') -Encoding utf8NoBOM

    New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
    $zipPath = Join-Path $OutputDirectory "mpv-$Version-$Abi.zip"
    $pluginPath = Join-Path $OutputDirectory "mpv-$Version-$Abi.abu-plugin"
    Compress-Archive -Path (Join-Path $stage 'manifest.json'), (Join-Path $stage 'THIRD_PARTY_NOTICES.txt'), (Join-Path $stage 'lib') -DestinationPath $zipPath -Force
    Move-Item -LiteralPath $zipPath -Destination $pluginPath -Force
    Write-Output $pluginPath
    Write-Output "sha256=$((Get-FileHash -LiteralPath $pluginPath -Algorithm SHA256).Hash.ToLowerInvariant())"
} finally {
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
}
