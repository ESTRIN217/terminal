plugins {
    id("com.android.library")
    `maven-publish`
}

android {
    namespace = "com.termux.shared"

    compileSdk = project.properties["compileSdkVersion"]?.toString()?.toInt() ?: 36
    ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.properties["ndkVersion"]?.toString() ?: ""

    dependencies {
        implementation("androidx.appcompat:appcompat:1.6.1")
        implementation("androidx.annotation:annotation:1.9.0")
        implementation("androidx.core:core:1.13.1")
        implementation("com.google.android.material:material:1.12.0")
        implementation("com.google.guava:guava:24.1-jre")
        implementation("io.noties.markwon:core:${project.properties["markwonVersion"]}")
        implementation("io.noties.markwon:ext-strikethrough:${project.properties["markwonVersion"]}")
        implementation("io.noties.markwon:linkify:${project.properties["markwonVersion"]}")
        implementation("io.noties.markwon:recycler:${project.properties["markwonVersion"]}")
        implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
        implementation("androidx.window:window:1.1.0")
        implementation("commons-io:commons-io:2.5")
        implementation(project(":terminal-view"))
        implementation("com.termux:termux-am-library:v2.0.0")
    }

    defaultConfig {
        minSdk = project.properties["minSdkVersion"]?.toString()?.toInt() ?: 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            ndkBuild {
                cppFlags("")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    publishing {
        multipleVariants {
            withSourcesJar()
            withJavadocJar()
            allVariants()
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    add("coreLibraryDesugaring", "com.android.tools:desugar_jdk_libs:1.1.5")
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
                artifactId = "termux-shared"
                version = "0.118.0"
                artifact(tasks.named("sourceJar"))
            }
        }
    }
}
