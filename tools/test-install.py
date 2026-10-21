"""Offline installer contract tests. No real ADB, network, or device changes."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
FAKE_ADB = '''#!/usr/bin/env python3
import os, sys
from pathlib import Path
a = sys.argv[1:]
with open(os.environ['ADB_LOG'], 'a') as f: f.write(' '.join(a)+'\\n')
if a[:1] == ['-s']: a = a[2:]
state = Path(os.environ['ADB_LOG']+'.home')
if a == ['get-state']: print('device')
elif a[:3] == ['shell','getprop','ro.build.version.sdk']: print('32')
elif a[:3] == ['shell','getprop','ro.product.cpu.abilist']: print('arm64-v8a')
elif a[:1] == ['install']: print('Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]' if os.environ.get('FAIL_INSTALL') else 'Success')
elif a[:3] == ['shell','pm','path']: print('package:/data/app/base.apk')
elif a[:3] == ['shell','am','start']: print('Status: ok')
elif 'set-home-activity' in a: state.write_text(a[-1]); print('Success')
elif 'resolve-activity' in a:
 if 'android.intent.category.LAUNCHER' in a: print('com.limi.tvdesktop/.MainActivity')
 else: print(state.read_text() if state.exists() else 'com.android.launcher/.Launcher')
elif a[:4] == ['shell','settings','get','global']: print('com.android.launcher/.Launcher')
elif a[:4] == ['shell','dumpsys','activity','activities']: print('mResumedActivity: com.limi.tvdesktop/.MainActivity')
'''


class InstallerContract(unittest.TestCase):
    def run_script(self, script, **overrides):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            adb = folder / 'adb'
            adb.write_text(FAKE_ADB)
            adb.chmod(0o700)
            apk = folder / 'package with spaces.apk'
            apk.write_bytes(b'fixture')
            log = folder / 'commands'
            env = dict(os.environ, ADB=str(adb), TV='emulator-5554', APK_FILE=str(apk),
                       ADB_LOG=str(log), SKIP_AOT='1', **overrides)
            result = subprocess.run(['bash', str(ROOT / 'tools' / script)], env=env,
                                    text=True, capture_output=True)
            return result, log.read_text()

    def test_ordinary_install_preserves_default_home_and_permissions(self):
        result, commands = self.run_script('install.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn('set-home-activity', commands)
        self.assertNotIn('grant', commands)
        self.assertNotIn('uninstall', commands)
        self.assertIn('category.LAUNCHER', commands)
        self.assertIn('-s emulator-5554 install -r', commands)

    def test_signature_conflict_does_not_erase_app(self):
        result, commands = self.run_script('install.sh', FAIL_INSTALL='1')
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('uninstall', commands)
        self.assertNotIn('am start', commands)

    def test_home_is_opt_in_and_records_previous_home(self):
        result, commands = self.run_script('install.sh', SET_HOME='1')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('settings put global abu_prev_home com.android.launcher/.Launcher', commands)
        self.assertIn('set-home-activity com.limi.tvdesktop/.MainActivity', commands)

    def test_restore_keeps_app_and_does_not_revoke_unrelated_permissions(self):
        result, commands = self.run_script('restore.sh')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('set-home-activity com.android.launcher/.Launcher', commands)
        self.assertNotIn('uninstall', commands)
        self.assertNotIn('revoke', commands)


if __name__ == '__main__':
    unittest.main()
