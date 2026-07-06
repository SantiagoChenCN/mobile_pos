# Build Environment

The current Windows build environment is portable and lives on the E drive:

- Build tools root: `E:\AndroidBuildEnv`
- Android SDK: `E:\AndroidBuildEnv\android-sdk`
- Gradle: `E:\AndroidBuildEnv\gradle\gradle-8.11`
- JDK 17: `E:\AndroidBuildEnv\jdk\jdk-17.0.19+10`
- Gradle cache: `E:\AndroidBuildEnv\.gradle-cache`

The project is also mirrored to an ASCII-only build path because Android Gradle Plugin path checks can be fragile with non-ASCII Windows paths:

- Source project: `E:\手机收银软件开发\android-emergency-pos`
- Build mirror: `E:\AndroidEmergencyPos`

Build command used on this machine:

```powershell
$env:JAVA_HOME='E:\AndroidBuildEnv\jdk\jdk-17.0.19+10'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
powershell -ExecutionPolicy Bypass -File 'E:\AndroidEmergencyPos\scripts\build-debug-apk.ps1'
```

Debug APK output:

```text
E:\AndroidEmergencyPos\app\build\outputs\apk\debug\app-debug.apk
```

For a fresh checkout on another Windows machine:

1. Install JDK 17.
2. Install Android SDK with Android Gradle Plugin compatible build tools.
3. Create `local.properties` in the project root with `sdk.dir=...`.
4. Run `scripts\build-debug-apk.ps1`.
