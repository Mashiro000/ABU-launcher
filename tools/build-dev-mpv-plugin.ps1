param(
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64')]
    [string]$Abi = 'x86_64',
    [string]$Version = '1.0.0-dev',
    [string]$OutputDirectory = "$PSScriptRoot\..\dist\plugins"
)

$ErrorActionPreference = 'Stop'
$aar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\dev.jdtech.mpv\libmpv\1.0.0" -Recurse -Filter '*.aar' |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $aar) { throw 'libmpv AAR is not available in the Gradle cache. Build the Android app first.' }

$stage = Join-Path $env:TEMP ("abu-mpv-plugin-" + [guid]::NewGuid().ToString('N'))
$nativeDir = Join-Path $stage "lib\$Abi"
New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null

try {
    $archive = Join-Path $stage 'libmpv.zip'
    Copy-Item -LiteralPath $aar -Destination $archive
    $expanded = Join-Path $stage 'aar'
    Expand-Archive -LiteralPath $archive -DestinationPath $expanded
    Copy-Item -Path (Join-Path $expanded "jni\$Abi\*.so") -Destination $nativeDir

    $manifest = @{
        schemaVersion = 1
        id = 'com.limi.player.mpv'
        name = 'MPV 播放器内核（开发构建）'
        version = $Version
        author = 'ABU Launcher'
        description = '用于本地验证按需播放器插件加载；正式发布包会附带完整第三方许可说明。'
        kind = 'player'
        official = $false
        abi = $Abi
        permissions = @(
            @{ id = 'media.playback'; title = '播放媒体地址'; sensitive = $false },
            @{ id = 'network.media'; title = '访问媒体服务器'; sensitive = $true }
        )
    } | ConvertTo-Json -Depth 6
    Set-Content -LiteralPath (Join-Path $stage 'manifest.json') -Value $manifest -Encoding utf8NoBOM

    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $zipPath = Join-Path $OutputDirectory "mpv-$Version-$Abi.zip"
    $pluginPath = Join-Path $OutputDirectory "mpv-$Version-$Abi.abu-plugin"
    Compress-Archive -Path (Join-Path $stage 'manifest.json'), (Join-Path $stage 'lib') -DestinationPath $zipPath -Force
    Move-Item -LiteralPath $zipPath -Destination $pluginPath -Force
    $hash = (Get-FileHash -LiteralPath $pluginPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Output $pluginPath
    Write-Output "sha256=$hash"
} finally {
    if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
}
