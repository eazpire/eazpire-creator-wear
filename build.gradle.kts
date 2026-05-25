plugins {
    id("com.android.application") version "8.8.2" apply false
    id("com.android.library") version "8.8.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Keep Gradle outputs off OneDrive (file locks) and avoid packageDebug NPE from java.io.tmpdir splits.
subprojects {
    if (System.getenv("CI") != "true") {
        val localRoot = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
        val safeName = path.removePrefix(":").replace(":", "_")
        layout.buildDirectory.set(file("$localRoot/eazpire-wear-build/$safeName"))
    }
}
