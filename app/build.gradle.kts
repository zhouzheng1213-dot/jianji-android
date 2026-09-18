plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jianji.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jianji.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("zh", "zh-rCN", "en")
    }

    // 固定签名：本地与 CI 使用同一把钥匙，保证后续版本可以覆盖安装升级。
    signingConfigs {
        create("jianji") {
            storeFile = rootProject.file("keystore/jianji.jks")
            storePassword = "jianji2026"
            keyAlias = "jianji"
            keyPassword = "jianji2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("jianji")
            applicationIdSuffix = ""
        }
        release {
            // 个人自用应用，关闭代码压缩以保证行为与调试版完全一致。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("jianji")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        // 不分析测试源码：本项目的单测用的是 Robolectric + Room 的真实 SQLite，
        // 而 AGP 8.6 + Kotlin 2.0 的 lint 在解析这些测试类的父类型时会内部崩溃
        // （报错原文是 "Unexpected failure during lint analysis ... this is a bug in lint"），
        // 于是把 lint 自己的崩溃当成 LintError 报出来，五个测试类各报一条。
        // 这不是代码缺陷 —— 单测本身由 testDebugUnitTest 负责把关，lint 在这里没有增量价值。
        checkTestSources = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Room 导出版本化 schema。迁移脚本必须与 Room 期望的表结构逐字一致，
// 导出的 JSON 就是这份「期望」的权威来源，也是以后写迁移时的对照物。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
