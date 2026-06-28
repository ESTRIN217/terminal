plugins {
    id("com.android.library")
    `maven-publish`
}

android {
    namespace = "com.termux.emulator"

    compileSdk = project.properties["compileSdkVersion"]?.toString()?.toInt() ?: 36
    ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.properties["ndkVersion"]?.toString() ?: ""

    defaultConfig {
        minSdk = project.properties["minSdkVersion"]?.toString()?.toInt() ?: 21

        externalNativeBuild {
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            }
        }

        ndk {
            abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        multipleVariants {
            withSourcesJar()
            withJavadocJar()
            allVariants()
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.register<Jar>("sourceJar") {
    from(project.android.sourceSets.getByName("main").java)
    archiveClassifier.set("sources")
}

afterEvaluate {
    publishing {
        publications {
            register("release", MavenPublication::class) {
                from(components["default"])
                groupId = "com.estrin217"
                artifactId = "terminal-emulator"
                version = "0.118.0"
                artifact(tasks.named("sourceJar"))
            }
        }
    }
}
