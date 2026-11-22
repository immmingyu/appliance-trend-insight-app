// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // Firebase(FCM 푸시) 설정 파일 app/google-services.json을 읽는 플러그인. 실제 적용은 app/build.gradle.kts에서 파일이 있을 때만
    id("com.google.gms.google-services") version "4.4.4" apply false
}