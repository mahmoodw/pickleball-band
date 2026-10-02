#!/usr/bin/env python3
"""Fetch the Xiaomi Interconnect SDK used by this prototype, with a pinned hash."""
from pathlib import Path
from urllib.request import urlopen
from io import BytesIO
from zipfile import ZipFile
import hashlib

URL = 'https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip'
SHA256 = '8e3d74eebda558e2bef45e32f0965c5b77ef939a06cdc55136929f9427a95ca7'
with urlopen(URL, timeout=60) as response:
    data = response.read()
if hashlib.sha256(data).hexdigest() != SHA256:
    raise SystemExit('Xiaomi demo archive changed. Review the new SDK before updating the pinned hash.')
dest = Path(__file__).resolve().parents[1] / 'android/libs'
dest.mkdir(parents=True, exist_ok=True)
with ZipFile(BytesIO(data)) as archive:
    aar = archive.read('interconnect_dev_test_demo/libs/xms-wearable-lib_1.4_release.aar')
(dest / 'xms-wearable-lib_1.4_release.aar').write_bytes(aar)
with ZipFile(BytesIO(aar)) as archive:
    (dest / 'xms.jar').write_bytes(archive.read('classes.jar'))
print('Xiaomi XMS Wearable SDK 1.4 ready in', dest)
