#!/usr/bin/env python3
"""Build a signed prototype APK using JDK 17 and Android SDK build-tools 35."""
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ['PICKLEBALL_JDK'])
SDK = Path(os.environ['PICKLEBALL_ANDROID_PLATFORM'])
TOOLS = Path(os.environ['PICKLEBALL_ANDROID_TOOLS'])
OUT = ROOT / 'build/android'
DIST = ROOT / 'dist'
SIGN = ROOT / 'sign'
for p in [OUT / 'classes', OUT / 'dex', DIST, SIGN]:
    p.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(JDK), PATH=str(JDK / 'bin') + os.pathsep + os.environ['PATH'])

def run(*args):
    subprocess.run([str(a) for a in args], check=True, env=env, cwd=ROOT)

keystore = SIGN / 'prototype.p12'
password = 'pickleball-prototype'
if not keystore.exists():
    run(JDK / 'bin/keytool', '-genkeypair', '-alias', 'pickleball', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '3650', '-storetype', 'PKCS12', '-keystore', keystore, '-storepass', password,
        '-keypass', password, '-dname', 'CN=Pickleball Prototype')
    os.chmod(keystore, 0o600)
private = SIGN / 'private.pem'
cert = SIGN / 'certificate.pem'
if not private.exists() or not cert.exists():
    run('openssl', 'pkcs12', '-in', keystore, '-passin', 'pass:' + password, '-nocerts', '-nodes', '-out', private)
    run('openssl', 'pkcs12', '-in', keystore, '-passin', 'pass:' + password, '-clcerts', '-nokeys', '-out', cert)
    for path, marker in [(private, 'PRIVATE KEY'), (cert, 'CERTIFICATE')]:
        text = path.read_text()
        path.write_text(text[text.index('-----BEGIN ' + marker):])
        os.chmod(path, 0o600)
for kind in ['debug', 'release']:
    dest = ROOT / 'band/sign' / kind
    dest.mkdir(parents=True, exist_ok=True)
    for name in ['private.pem', 'certificate.pem']:
        (dest / name).write_bytes((SIGN / name).read_bytes())
        os.chmod(dest / name, 0o600)

jar = ROOT / 'android/libs/xms.jar'
run(JDK / 'bin/javac', '--release', '8', '-classpath', str(SDK / 'android.jar') + os.pathsep + str(jar),
    '-d', OUT / 'classes', *sorted((ROOT / 'android/src').rglob('*.java')))
classjar = OUT / 'app.jar'
with zipfile.ZipFile(classjar, 'w') as z:
    for p in sorted((OUT / 'classes').rglob('*.class')):
        z.write(p, p.relative_to(OUT / 'classes'))
run(TOOLS / 'd8', '--min-api', '26', '--lib', SDK / 'android.jar', '--output', OUT / 'dex', classjar, jar)
unsigned = OUT / 'unsigned.apk'
run(TOOLS / 'aapt2', 'link', '-o', unsigned, '-I', SDK / 'android.jar', '--manifest', ROOT / 'android/AndroidManifest.xml')
with zipfile.ZipFile(unsigned, 'a', compression=zipfile.ZIP_DEFLATED) as z:
    for p in (OUT / 'dex').glob('*.dex'):
        z.write(p, p.name)
aligned = OUT / 'aligned.apk'
run(TOOLS / 'zipalign', '-f', '4', unsigned, aligned)
apk = DIST / 'pickleball-phone-0.1.0.apk'
run(TOOLS / 'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', 'pickleball',
    '--ks-pass', 'pass:' + password, '--out', apk, aligned)
run(TOOLS / 'apksigner', 'verify', '--verbose', apk)
print(apk)
