import java.util.Properties

// 카카오 네이티브 앱 키: local.properties(git 제외) 또는 환경 변수에서 읽는다. 없으면 카카오 로그인 버튼을 숨긴다
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val kakaoKey: String = localProps.getProperty("KAKAO_NATIVE_APP_KEY")
    ?: System.getenv("KAKAO_NATIVE_APP_KEY") ?: ""
// Google 로그인 웹 클라이언트 ID (Credential Manager의 serverClientId). 없으면 Google 로그인 버튼을 숨긴다
val googleWebClientId: String = localProps.getProperty("GOOGLE_WEB_CLIENT_ID")
    ?: System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.gajeontiming"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.gajeonmusang.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        // 카카오톡 로그인 후 앱으로 돌아오는 주소 (kakao{앱 키}://oauth)
        manifestPlaceholders["kakaoScheme"] = if (kakaoKey.isNotEmpty()) "kakao$kakaoKey" else "kakaonotconfigured"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("androidx.core:core-splashscreen:1.0.1")   // F-01 시작 화면
    implementation("androidx.viewpager2:viewpager2:1.1.0")    // 앱 소개 화면 넘기기
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.kakao.sdk:v2-user:2.20.6")             // 카카오 로그인
    // Google 로그인 (Credential Manager → Google ID 토큰)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}