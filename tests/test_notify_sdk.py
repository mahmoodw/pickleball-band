"""Regression checks for the SDK/provider mismatch in v0.1.0."""
from pathlib import Path
from io import BytesIO
from zipfile import ZipFile
import sys
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from fetch_notify_sdk import FILENAME, verified_classes

class NotifySdkTests(unittest.TestCase):
    def test_unrecognized_sdk_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'official Xiaomi AAR cannot connect'):
            verified_classes(b'an identically named but incompatible AAR')

    def test_notify_provider_targets_are_visible_to_android(self):
        path = ROOT / 'android/libs' / FILENAME
        if not path.exists():
            self.skipTest('Run scripts/fetch_notify_sdk.py first')
        with ZipFile(BytesIO(verified_classes(path.read_bytes()))) as jar:
            provider = jar.read('com/xiaomi/xms/wearable/d.class')
        manifest = ET.parse(ROOT / 'android/AndroidManifest.xml').getroot()
        attr = '{http://schemas.android.com/apk/res/android}name'
        queries = {p.get(attr) for p in manifest.findall('./queries/package')}
        for name in ['com.mc.xiaomi1', 'com.mc.xiaomi1.huawei']:
            self.assertIn(name.encode(), provider)
            self.assertIn(name, queries)
        self.assertNotIn(b'com.mi.health', provider)
        self.assertNotIn('com.mi.health', queries)
        self.assertNotIn('com.xiaomi.wearable', queries)

    def test_updated_apk_preserves_install_identity(self):
        manifest = ET.parse(ROOT / 'android/AndroidManifest.xml').getroot()
        self.assertEqual(manifest.get('package'), 'com.wmahmood.pickleball')
        attr = '{http://schemas.android.com/apk/res/android}'
        self.assertGreater(int(manifest.get(attr + 'versionCode')), 1)

if __name__ == '__main__':
    unittest.main()
