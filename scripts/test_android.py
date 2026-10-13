#!/usr/bin/env python3
"""Compile against platform stubs; run logic checks, not a Bluetooth emulator."""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
jdk = Path(os.environ['PICKLEBALL_JDK'])
platform = Path(os.environ['PICKLEBALL_ANDROID_PLATFORM'])
json_jar = Path(os.environ['PICKLEBALL_TEST_JSON'])
out = root / 'build/java-tests'
out.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(map(str, [json_jar, platform / 'android.jar', root / 'android/libs/xms.jar']))
subprocess.run([str(jdk / 'bin/javac'), '-cp', classpath, '-d', str(out),
                *map(str, sorted((root / 'android/src').rglob('*.java'))),
                *map(str, sorted((root / 'tests').glob('*.java')))], check=True)
for name in ['ScoreTest', 'BandSelectionTest', 'AndroidTransportTest', 'MediaRemoteTest', 'AnnouncementAudioTest', 'WorkoutTest']:
    subprocess.run([str(jdk / 'bin/java'), '-cp', str(out) + os.pathsep + classpath,
                    'com.wmahmood.pickleball.' + name], check=True)
