#!/usr/bin/env bash
# Restore only the HOME choice recorded by install.sh. No blanket permission revocation.
set -euo pipefail
ADB="${ADB:-adb}"
PKG="${PKG:-com.limi.tvdesktop}"
TV="${TV:-${ANDROID_SERIAL:-}}"
PORT="${PORT:-5555}"
[ -n "$TV" ] || { echo '指定设备：TV=192.168.1.20 bash tools/restore.sh' >&2; exit 1; }
case "$TV" in *:*) serial="$TV";; *.*) serial="$TV:$PORT";; *) serial="$TV";; esac
case "$serial" in *:*) "$ADB" connect "$serial";; esac
adb_device() { "$ADB" -s "$serial" "$@"; }
[ "$(adb_device get-state 2>/dev/null)" = device ] || { echo '设备未连接或未授权。' >&2; exit 1; }
previous="$(adb_device shell settings get global abu_prev_home | tr -d '\r')"
current="$(adb_device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
case "$previous" in
  */*)
    case "$previous" in "$PKG/"*) echo '原桌面记录无效；请先在电视设置里手动选择系统桌面。' >&2; exit 2;; esac
    adb_device shell cmd package set-home-activity "$previous"
    restored="$(adb_device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
    [ "$restored" = "$previous" ] || { echo '系统未接受恢复，请手动选择原桌面；应用已保留。' >&2; exit 2; }
    adb_device shell settings delete global abu_prev_home
    echo "已恢复默认桌面：$previous";;
  *) case "$current" in "$PKG/"*) echo '缺少原桌面记录；请在电视设置中选择原厂桌面后再卸载。' >&2; exit 2;; esac
    case "$current" in */*) echo "默认桌面不是阿布，无需恢复：$current";; *) echo '无法确认系统默认桌面，保留应用。' >&2; exit 2;; esac;;
esac
adb_device shell input keyevent KEYCODE_HOME
if [ "${UNINSTALL:-0}" = 1 ]; then
  adb_device uninstall "$PKG"
else
  echo '保留应用和数据。需要删除时，确认备份后加 UNINSTALL=1。'
fi
