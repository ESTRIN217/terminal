plugins {
    id("com.android.library")
    `maven-publish`
}

android {
    namespace = "com.termux.view"

    compileSdk = project.properties["compileSdkVersion"]?.toString()?.toInt() ?: 36

    dependencies {
        implementation("androidx.annotation:annotation:1.9.0")
        api(project(":terminal-emulator"))
    }

    defaultConfig {
        minSdk = project.properties["minSdkVersion"]?.toString()?.toInt() ?: 21
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
                artifactId = "terminal-view"
                version = "0.118.0"
                artifact(tasks.named("sourceJar"))
            }
        }
    }
}
