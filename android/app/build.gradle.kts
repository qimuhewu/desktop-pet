// app 模块构建脚本
// 关键点：
// 1. minSdk 26（已拍板），targetSdk/compileSdk 34
// 2. Cubism SDK 源集隔离：third_party/cubism-android 存在时启用 src/live2d/java（真实 Cubism 实现），
//    否则启用 src/nolive2d/java（加载必失败的占位实现），保证无 SDK 时可编译（硬性约束 2）。
// 3. Room 使用 KSP 编译器。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// ---- Cubism SDK 探测（宽松校验：递归查找任意 .aar；实包到位后按实际目录结构固化）----
val cubismRoot: File = File(rootDir, "../third_party/cubism-android")
val cubismAar: File? = if (cubismRoot.isDirectory) {
    cubismRoot.walkTopDown().firstOrNull { it.isFile && it.extension == "aar" }
} else {
    null
}
val hasCubism: Boolean = cubismAar != null

android {
    namespace = "com.mobiledesktoppet.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mobiledesktoppet.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        // 互斥源集：有 SDK 用 live2d（引用 Cubism 类），无 SDK 用 nolive2d（同签名占位实现）
        getByName("main") {
            java.srcDir(if (hasCubism) "src/live2d/java" else "src/nolive2d/java")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.documentfile)

    // Room（schema 对照 shared/schema/pet_db_schema.sql）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager（提醒周期兜底）
    implementation(libs.androidx.work.runtime.ktx)

    // Cubism SDK（脚本放置，不入库；无 SDK 时不参与编译）
    if (hasCubism) {
        implementation(files(cubismAar!!.absolutePath))
        // Framework 源码目录（若实包结构含 Framework/src，请按实际路径补充 srcDir）
    }

    // JVM 单元测试（业务引擎镜像测试，纯 Kotlin 无 Android 依赖）
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
