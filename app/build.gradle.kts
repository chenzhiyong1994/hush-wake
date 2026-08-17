plugins {
    id("com.android.application")
}

android {
    namespace = "com.hushwake.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hushwake.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 8
        versionName = "0.3.3-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
