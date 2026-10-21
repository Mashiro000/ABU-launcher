#!/usr/bin/env bash
# ABU Launcher: ordinary app installation by default; SET_HOME=1 is opt-in.
set -euo pipefail
ADB="${ADB:-adb}"
PKG="${PKG:-com.limi.tvdesktop}"
TV="${TV:-${ANDROID_SERIAL:-}}"
PORT="${PORT:-5555}"
SET_HOME="${SET_HOME:-0}"
VERSION="${VERSION:-0.07}"
[ -n "$TV" ] || { echo '指定设备：TV=192.168.1.20 bash tools/install.sh' >&2; exit 1; }
command -v "$ADB" >/dev/null || { echo '请先安装 Android SDK Platform-Tools，并将 adb 加入 PATH。' >&2; exit 1; }
case "$TV" in *:*) serial="$TV";; *.*) serial="$TV:$PORT";; *) serial="$TV";; esac
case "$serial" in *:*) "$ADB" connect "$serial";; esac
adb_device() { "$ADB" -s "$serial" "$@"; }
[ "$(adb_device get-state 2>/dev/null)" = device ] || { echo '设备未连接或未授权；请在电视上确认调试授权。' >&2; exit 1; }
sdk="$(adb_device shell getprop ro.build.version.sdk | tr -d '\r')"
[ "${sdk:-0}" -ge 25 ] || { echo '需要 Android 7.1 / API 25 或更高版本。' >&2; exit 1; }
abis="$(adb_device shell getprop ro.product.cpu.abilist | tr -d '\r')"
[ -n "$abis" ] || abis="$(adb_device shell getprop ro.product.cpu.abi | tr -d '\r')"
abi="${abis%%,*}"
case "$abi" in arm64-v8a|armeabi-v7a|x86|x86_64) ;; *) abi=universal;; esac
work="$(mktemp -d "${TMPDIR:-/tmp}/abu-install.XXXXXX")"
trap 'rm -rf "$work"' EXIT
apk="${APK_FILE:-}"
if [ -z "$apk" ]; then
  name="ABU-Launcher-$VERSION-$abi.apk"
  base="https://github.com/Mashiro000/ABU-launcher/releases/download/v$VERSION"
  url="${APK_URL:-$base/$name}"
  echo "下载 $url"
  curl -fL --retry 2 "$url" -o "$work/$name"
  apk="$work/$name"
  if [ -z "${APK_URL:-}" ]; then
    curl -fsSL --retry 2 "$base/SHA256SUMS.txt" -o "$work/SHA256SUMS.txt"
    expected="$(awk -v name="$name" '$2 == name { print $1 }' "$work/SHA256SUMS.txt")"
    [ -n "$expected" ] || { echo '未找到安装包校验值。' >&2; exit 1; }
    SHA256="$expected"
  fi
fi
[ -f "$apk" ] || { echo "APK 不存在：$apk" >&2; exit 1; }
if [ -n "${SHA256:-}" ]; then
  if command -v shasum >/dev/null; then actual="$(shasum -a 256 "$apk" | awk '{print $1}')"
  else actual="$(sha256sum "$apk" | awk '{print $1}')"; fi
  [ "$actual" = "$SHA256" ] || { echo 'SHA-256 校验失败，停止安装。' >&2; exit 1; }
fi
# Do not uninstall or erase data on a signature conflict.
adb_device install -r "$apk" 2>&1 | tee "$work/install.log"
grep -q '^Success' "$work/install.log" || { echo '安装未成功；检查架构、签名与电视安装授权。' >&2; exit 1; }
adb_device shell pm path "$PKG" | tee "$work/package.log"
grep -q 'package:' "$work/package.log" || { echo '包名不匹配，请检查 APK 或 PKG 参数。' >&2; exit 1; }
if [ "${SKIP_AOT:-0}" != 1 ]; then
  if adb_device shell cmd package compile -m speed -f "$PKG" >"$work/aot.log" 2>&1 && ! grep -qiE 'error|failure|failed|unknown' "$work/aot.log"; then
    echo 'AOT 编译完成。'
  else echo 'AOT 未完成；可正常使用，性能需实际测试。'; fi
fi
adb_device shell am start -W -n "$PKG/.MainActivity" | tee "$work/start.log"
grep -q 'Status: ok' "$work/start.log" || { echo '应用启动验证失败。' >&2; exit 1; }
# Confirm an ordinary launch entry independently of HOME support.
adb_device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$PKG" | tee "$work/entry.log"
grep -Fq "$PKG/" "$work/entry.log" || { echo '应用图标入口未找到。' >&2; exit 1; }
if [ "$SET_HOME" = 1 ]; then
  previous="$(adb_device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
  case "$previous" in
    "$PKG/"*) ;; # Preserve the original recovery record on upgrades.
    */*) adb_device shell settings put global abu_prev_home "$previous";;
    *) echo '未识别原桌面，停止自动接管；普通安装已完成。' >&2; exit 2;;
  esac
  adb_device shell cmd package set-home-activity "$PKG/.MainActivity"
  resolved="$(adb_device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
  case "$resolved" in "$PKG/"*) ;; *) echo '系统未接受默认桌面；普通安装已完成。' >&2; exit 2;; esac
  adb_device shell input keyevent KEYCODE_HOME
  sleep 1
  adb_device shell dumpsys activity activities > "$work/home.log"
  grep -E '(mResumedActivity|topResumedActivity)' "$work/home.log" | grep -F "$PKG/" || { echo 'HOME 未进入阿布：该机型接管未通过。应用图标仍可打开；请恢复原桌面。' >&2; exit 2; }
fi
printf '\n安装完成：%s；设备：%s；系统 ABI：%s。\n' "$PKG" "$serial" "$abis"
[ "$SET_HOME" = 1 ] || echo '未更改默认桌面。请从电视应用列表点击阿布桌面图标打开。'
echo '当前版本不提供保持 ADB 常开或无障碍桌面守护。'
