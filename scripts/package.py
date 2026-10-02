#!/usr/bin/env python3
"""Package installables and a source archive with an explicit inclusion list."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import shutil
import json

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
dist.mkdir(exist_ok=True)
version = json.loads((root / 'package.json').read_text())['version']
rpk = root / ('band/dist/com.wmahmood.pickleball.release.' + version + '.rpk')
shutil.copyfile(rpk, dist / ('pickleball-band-' + version + '.rpk'))
shutil.copyfile(root / 'README.md', dist / 'README.md')
shutil.copyfile(root / 'THIRD_PARTY.md', dist / 'THIRD_PARTY.md')
sources = [root / p for p in ['.gitignore','README.md','THIRD_PARTY.md','package.json','band/package.json','band/package-lock.json','android/AndroidManifest.xml']]
for folder in ['band/src','android/src','tests','scripts','docs']:
    sources.extend(p for p in (root / folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
with ZipFile(dist / ('pickleball-source-' + version + '.zip'), 'w', ZIP_DEFLATED) as z:
    for path in sorted(sources):
        z.write(path, Path('pickleball-band') / path.relative_to(root))
artifacts = ['pickleball-band-' + version + '.rpk', 'pickleball-phone-' + version + '.apk', 'pickleball-source-' + version + '.zip', 'README.md', 'THIRD_PARTY.md']
(dist / 'SHA256SUMS').write_text(''.join(hashlib.sha256((dist/name).read_bytes()).hexdigest()+'  '+name+'\n' for name in artifacts))
with ZipFile(dist / ('pickleball-prototype-' + version + '.zip'), 'w', ZIP_DEFLATED) as z:
    for name in artifacts + ['SHA256SUMS']:
        z.write(dist / name, name)
for name in artifacts + ['pickleball-prototype-' + version + '.zip']:
    print(name, (dist / name).stat().st_size, 'bytes')
