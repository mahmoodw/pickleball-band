# Third-party components

- **Notify's custom XMS Wearable SDK 1.4:** the Android APK includes `classes.jar` from the custom `xms-wearable-lib_1.4_release.aar` distributed by Notify for Xiaomi. It binds to Notify (`com.mc.xiaomi1` / `com.mc.xiaomi1.huawei`), using the Xiaomi-named Interconnect service and API. This is **not interchangeable with Xiaomi's official AAR**, used in v0.1.0. The binary SDK is fetched separately for source builds; rights and applicable SDK terms remain with the respective vendors. Documentation: https://www.mibandnotify.com/xiaomi-mi-band/notify-xms-app-instructions.php . Download: https://www.mibandnotify.com/xiaomi-mi-band/xms/xms-wearable-lib_1.4_release.aar . SHA-256: `173220d1e000e3d968e9bf2119bd2837bdd125919cdfdbc881af894c88589278`.
- **Xiaomi aiot-toolkit 2.0.5:** used to compile and sign the Vela RPK. Build dependency versions and provenance are captured in `band/package-lock.json`.
- **Android platform/build tools** and **Eclipse Temurin JDK 17:** used for APK compilation and signing; not included in the source archive.
- **JSON-java 20240303:** used only for JVM tests of Android JSON parsing; not included in the APK. Android supplies `org.json` at runtime.
- **Playwright / Chromium:** optional development tools for the browser layout preview. Install separately; neither is bundled with the APK, RPK or source archive. The preview approximates Vela layout and is not a wearable emulator.

The supplied app icons and application code were created for this project. No Mi Fitness, Notify or Xiaomi signing identity is reused.

## Generated announcer audio

The APK and source archive include synthetic score clips rendered through the community [Morgan Freeman RVC model](https://huggingface.co/DeputyRipper/Morgan_Freeman_RVCV2) and [Kermit RVC model](https://huggingface.co/RosinoSuperRosa/Kermit-The-Frog). Both model cards declare `license: openrail`; no model weights are bundled. These are community model outputs with no claim of official endorsement. See `docs/VOICE-MODELS.md` and each pack's `manifest.json` for source attribution, hashes and generation parameters.

Offline build tools used to generate the clips include **Applio** (MIT), **kokoro-onnx** (MIT), **Kokoro-82M** (Apache-2.0 model), ContentVec, RMVPE and Torchaudio/Wav2Vec2 for source alignment. Their model runtimes are not bundled in Android. The fixed app vocabulary is original to this project; the two voice models perform the final voice conversion. The generated audio's provenance does not assert ownership of any speaker or character identity.
