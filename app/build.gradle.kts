import java.net.URI
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.regex.Pattern

plugins {
    id("com.android.application")
}

val packageVariant: String = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7"
val appVersionName = System.getenv("TERMUX_APP_VERSION_NAME") ?: ""
val apkVersionTag = System.getenv("TERMUX_APK_VERSION_TAG") ?: ""
val splitAPKsForDebugBuilds = System.getenv("TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS") ?: "1"
val splitAPKsForReleaseBuilds = System.getenv("TERMUX_SPLIT_APKS_FOR_RELEASE_BUILDS") ?: "0"

android {
    namespace = "com.termux"

    compileSdk = project.properties["compileSdkVersion"]?.toString()?.toInt() ?: 36
    ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.properties["ndkVersion"]?.toString() ?: ""

    dependencies {
        implementation("androidx.annotation:annotation:1.9.0")
        implementation("androidx.core:core:1.13.1")
        implementation("androidx.drawerlayout:drawerlayout:1.2.0")
        implementation("androidx.preference:preference:1.2.1")
        implementation("androidx.viewpager:viewpager:1.0.0")
        implementation("com.google.android.material:material:1.12.0")
        implementation("com.google.guava:guava:24.1-jre")
        implementation("io.noties.markwon:core:${project.properties["markwonVersion"]}")
        implementation("io.noties.markwon:ext-strikethrough:${project.properties["markwonVersion"]}")
        implementation("io.noties.markwon:linkify:${project.properties["markwonVersion"]}")
        implementation("io.noties.markwon:recycler:${project.properties["markwonVersion"]}")
        implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")

        implementation(project(":terminal-view"))
        implementation(project(":termux-shared"))
    }

    defaultConfig {
        minSdk = project.properties["minSdkVersion"]?.toString()?.toInt() ?: 21
        targetSdk = project.properties["targetSdkVersion"]?.toString()?.toInt() ?: 28
        versionCode = 118
        val verName = appVersionName.ifEmpty { "0.118.0" }
        versionName = verName
        validateVersionName(verName)

        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"$packageVariant\"")

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.estrin217.terminal"
        manifestPlaceholders["TERMUX_APP_NAME"] = "Termux"
        manifestPlaceholders["TERMUX_API_APP_NAME"] = "Termux:API"
        manifestPlaceholders["TERMUX_BOOT_APP_NAME"] = "Termux:Boot"
        manifestPlaceholders["TERMUX_FLOAT_APP_NAME"] = "Termux:Float"
        manifestPlaceholders["TERMUX_STYLING_APP_NAME"] = "Termux:Styling"
        manifestPlaceholders["TERMUX_TASKER_APP_NAME"] = "Termux:Tasker"
        manifestPlaceholders["TERMUX_WIDGET_APP_NAME"] = "Termux:Widget"

        externalNativeBuild {
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            }
        }

        splits {
            abi {
                isEnable = (gradle.startParameter.taskNames.any { it.contains("Debug") } && splitAPKsForDebugBuilds == "1") ||
                    (gradle.startParameter.taskNames.any { it.contains("Release") } && splitAPKsForReleaseBuilds == "1")
                reset()
                include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
                isUniversalApk = true
            }
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }

    lint {
        disable += "ProtectedPermissions"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.10")
    add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:1.1.5")
}

tasks.register("versionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}

fun validateVersionName(versionName: String) {
    if (!Pattern.matches(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?\$",
            versionName
        )
    ) {
        throw GradleException(
            "The versionName '$versionName' is not a valid version as per semantic version '2.0.0' spec in the format 'major.minor.patch(-prerelease)(+buildmetadata)'. https://semver.org/spec/v2.0.0.html."
        )
    }
}

fun downloadBootstrap(arch: String, expectedChecksum: String, version: String) {
    val digest = MessageDigest.getInstance("SHA-256")
    val localUrl = "src/main/cpp/bootstrap-$arch.zip"
    val file = file(localUrl)
    if (file.exists()) {
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            while (true) {
                val readBytes = input.read(buffer)
                if (readBytes < 0) break
                digest.update(buffer, 0, readBytes)
            }
        }
        val checksum = BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
        if (checksum == expectedChecksum) {
            return
        } else {
            logger.quiet("Deleting old local file with wrong hash: $localUrl: expected: $expectedChecksum, actual: $checksum")
            file.delete()
        }
    }

    val remoteUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-$version/bootstrap-$arch.zip"
    logger.quiet("Downloading $remoteUrl ...")

    file.parentFile.mkdirs()
    val digestStream = DigestInputStream(URI(remoteUrl).toURL().openStream(), digest)
    file.outputStream().buffered().use { out ->
        digestStream.transferTo(out)
    }

    val checksum = BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
    if (checksum != expectedChecksum) {
        file.delete()
        throw GradleException("Wrong checksum for $remoteUrl: expected: $expectedChecksum, actual: $checksum")
    }
}

tasks.named<Delete>("clean") {
    doLast {
        fileTree(File(projectDir, "src/main/cpp")).matching { include("bootstrap-*.zip") }.forEach { it.delete() }
    }
}

tasks.register("downloadBootstraps") {
    doLast {
        if (packageVariant == "apt-android-7") {
            val version = "2026.02.12-r1" + "%2B" + "apt.android-7"
            downloadBootstrap("aarch64", "ea2aeba8819e517db711f8c32369e89e7c52cee73e07930ff91185e1ab93f4f3", version)
            downloadBootstrap("arm", "a38f4d3b2f735f83be2bf54eff463e86dc32a3e2f9f861c1557c4378d249c018", version)
            downloadBootstrap("i686", "f5bc0b025b9f3b420b5fcaeefc064f888f5f22a0d6fd7090f4aac0c33eb3555b", version)
            downloadBootstrap("x86_64", "b7fd0f2e3a4de534be3144f9f91acc768630fc463eaf134ab2e64c545e834f7a", version)
        } else if (packageVariant == "apt-android-5") {
            val version = "2022.04.28-r6" + "+" + packageVariant
            downloadBootstrap("aarch64", "913609d439415c828c5640be1b0561467e539cb1c7080662decaaca2fb4820e7", version)
            downloadBootstrap("arm", "26bfb45304c946170db69108e5eb6e3641aad751406ce106c80df80cad2eccf8", version)
            downloadBootstrap("i686", "46dcfeb5eef67ba765498db9fe4c50dc4690805139aa0dd141a9d8ee0693cd27", version)
            downloadBootstrap("x86_64", "615b590679ee6cd885b7fd2ff9473c845e920f9b422f790bb158c63fe42b8481", version)
        } else {
            throw GradleException("Unsupported TERMUX_PACKAGE_VARIANT \"$packageVariant\"")
        }
    }
}
