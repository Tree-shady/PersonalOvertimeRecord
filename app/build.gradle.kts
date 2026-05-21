plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // id("com.google.gms.google-services") // 已禁用云同步
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.personalovertimerecord"
    compileSdk = 36
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.personalovertimerecord"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
}

// APK 重命名功能已禁用，让 Android Studio 能正确部署
// afterEvaluate {
//     tasks.named("assembleRelease").configure {
//         doLast {
//             renameApk("release")
//         }
//     }
//     
//     tasks.named("assembleDebug").configure {
//         doLast {
//             renameApk("debug")
//         }
//     }
// }

// fun renameApk(buildType: String) {
//     val versionName = android.defaultConfig.versionName ?: "1.1"
//     val newFileName = "GenerateAPK_${buildType}_$versionName.apk"
//     val outputDir = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
//     
//     println("Looking for APK in: ${outputDir.absolutePath}")
//     
//     if (outputDir.exists() && outputDir.isDirectory) {
//         val apkFiles = outputDir.listFiles { file -> 
//             file.isFile && file.name.endsWith(".apk") && !file.name.startsWith("GenerateAPK") 
//         }
//         
//         if (apkFiles != null && apkFiles.isNotEmpty()) {
//             apkFiles.forEach { apkFile ->
//                 val newFile = outputDir.resolve(newFileName)
//                 println("Renaming: ${apkFile.name} -> $newFileName")
//                 
//                 if (newFile.exists()) {
//                     newFile.delete()
//                 }
//                 
//                 apkFile.renameTo(newFile)
//                 println("Successfully renamed APK to: ${newFile.absolutePath}")
//             }
//         } else {
//             println("No APK files found in ${outputDir.absolutePath}")
//         }
//     } else {
//         println("Output directory does not exist: ${outputDir.absolutePath}")
//     }
// }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation(libs.androidx.fragment)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    implementation(libs.mpchart)
    
    // Gson - JSON 序列化/反序列化
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // SQLCipher - Database Encryption
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.2.0")
    
    // Firebase dependencies - 已禁用云同步
    // implementation(libs.firebase.auth.ktx)
    // implementation(libs.firebase.firestore.ktx)
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
