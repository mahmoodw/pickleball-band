#!/usr/bin/env python3
"""Fetch Notify's Interconnect SDK. The official Xiaomi AAR is incompatible."""
from pathlib import Path
from urllib.request import urlopen
from io import BytesIO
from zipfile import ZipFile
import hashlib

URL = 'https://www.mibandnotify.com/xiaomi-mi-band/xms/xms-wearable-lib_1.4_release.aar'
SHA256 = '173220d1e000e3d968e9bf2119bd2837bdd125919cdfdbc881af894c88589278'
FILENAME = 'notify-xms-wearable-lib_1.4_release.aar'

def verified_classes(data):
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise ValueError('Wrong Interconnect SDK. Run scripts/fetch_notify_sdk.py. The official Xiaomi AAR cannot connect through Notify.')
    with ZipFile(BytesIO(data)) as archive:
        return archive.read('classes.jar')

def main():
    with urlopen(URL, timeout=60) as response:
        data = response.read()
    classes = verified_classes(data)
    dest = Path(__file__).resolve().parents[1] / 'android/libs'
    dest.mkdir(parents=True, exist_ok=True)
    (dest / FILENAME).write_bytes(data)
    (dest / 'xms.jar').write_bytes(classes)
    print('Notify Interconnect SDK 1.4 ready in', dest)

if __name__ == '__main__':
    main()
