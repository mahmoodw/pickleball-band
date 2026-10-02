# Third-party components

- **Xiaomi XMS Wearable SDK 1.4:** the Android APK includes `classes.jar` from `xms-wearable-lib_1.4_release.aar`, provided in Xiaomi's Interconnect development/test demo. Source and applicable terms remain Xiaomi's. The SDK binds to the Mi Fitness service (`com.xiaomi.wearable` / `com.mi.health`); it does not bind to Notify. The binary SDK is fetched separately for source builds. Official source: https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip
- **Xiaomi aiot-toolkit 2.0.5:** used to compile and sign the Vela RPK. Build dependency versions and provenance are captured in `band/package-lock.json`.
- **Android platform/build tools** and **Eclipse Temurin JDK 17:** used for APK compilation and signing; not included in the source archive.
- **JSON-java 20240303:** used only for JVM tests of Android JSON parsing; not included in the APK. Android supplies `org.json` at runtime.

The supplied app icons and application code were created for this project. No Mi Fitness, Notify or Xiaomi signing identity is reused.
