plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.salsaldrive.app"
    compileSdk = 34

    // AAOS 표준 차량 API(android.car)를 쓰기 위한 설정
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.salsaldrive.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 공모전 제출용: 디버그 키로 서명한 release APK
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Pleos Vehicle SDK (선택): 저장소 접근이 되면 주석을 풀고 PleosBrake.kt 안내를 따르세요.
    // implementation("ai.pleos.playground:Vehicle:2.3.2")
}
