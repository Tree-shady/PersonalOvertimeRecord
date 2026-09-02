import java.util.Properties
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // id("com.google.gms.google-services") // 已禁用云同步
    id("com.google.devtools.ksp")
}

// 版本号自动管理
// 优先级：CI 传入（git tag，如 -PciVersionName=1.3.0 -PciVersionCode=10300） > 本地 version.properties 自动递增
// versionName 为三段格式（如 0.1.4）；versionCode 用递增构建号（BUILD_NUMBER）
// 注意：变量必须加 app 前缀，避免与 defaultConfig 的属性（versionCode/versionName）同名被遮蔽，
// 否则 defaultConfig 内赋值时右侧会解析成 self-assignment，导致 APK 版本号为空（与下方签名配置同理）
val versionPropsFile = file("version.properties")
var appVersionCode = 1
var appVersionName = "0.1.4"

val ciVersionName = project.findProperty("ciVersionName") as String?
val ciVersionCode = project.findProperty("ciVersionCode") as String?

if (!ciVersionName.isNullOrBlank() && !ciVersionCode.isNullOrBlank()) {
    // CI（GitHub Actions）根据 git tag 传入版本号，不修改 version.properties
    appVersionCode = ciVersionCode.toInt()
    appVersionName = ciVersionName
    println("🔢 CI version from tag: $appVersionName (build #$appVersionCode)")
} else if (versionPropsFile.exists()) {
    val props = Properties()
    versionPropsFile.inputStream().use { props.load(it) }
    val buildNumber = props.getProperty("BUILD_NUMBER", "1").toInt()
    val baseVersion = props.getProperty("BASE_VERSION", "0.1.4")
    
    // 递增构建号
    val newBuildNumber = buildNumber + 1
    props.setProperty("BUILD_NUMBER", newBuildNumber.toString())
    versionPropsFile.outputStream().use { props.store(it, "Auto-incremented build number") }
    
    appVersionCode = newBuildNumber
    // versionName 只保留三段基础版本号（如 0.1.4），不再追加构建号
    appVersionName = baseVersion
    
    println("🔢 Version auto-incremented: $appVersionName (build #$appVersionCode)")
} else {
    // 首次创建版本文件
    val props = Properties()
    props.setProperty("BASE_VERSION", "0.1.4")
    props.setProperty("BUILD_NUMBER", "1")
    versionPropsFile.outputStream().use { props.store(it, "Initial version properties") }
    
    appVersionCode = 1
    appVersionName = "0.1.4"
    
    println("🔢 Version initialized: $appVersionName (build #$appVersionCode)")
}

// 签名配置：仅当 CI 注入环境变量（GitHub Secrets）时启用，本地构建保持不签名/自动签名，不受影响
// 注意：变量名加 env 前缀，避免与 SigningConfig 的属性（keyAlias/keyPassword 等）同名，
// 否则 create("release") 内赋值时右侧会被遮蔽成 self-assignment，导致签名配置缺属性
val envKeystoreBase64 = System.getenv("KEYSTORE_BASE64")
val envKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
val envKeyAlias = System.getenv("KEY_ALIAS")
val envKeyPassword = System.getenv("KEY_PASSWORD")
val hasSigningEnv = !envKeystoreBase64.isNullOrBlank() && !envKeystorePassword.isNullOrBlank()
        && !envKeyAlias.isNullOrBlank() && !envKeyPassword.isNullOrBlank()

android {
    namespace = "com.example.personalovertimerecord"
    compileSdk = 36

    signingConfigs {
        if (hasSigningEnv) {
            create("release") {
                // 把 Secrets 里的 base64 keystore 解码到构建目录（临时文件，不入库）
                val keystoreFile = layout.buildDirectory.file("release.keystore").get().asFile
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(Base64.getDecoder().decode(envKeystoreBase64))
                storeFile = keystoreFile
                storePassword = envKeystorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
                // minSdk=24 时 AGP 默认只打 v2 签名；显式开启 v1（JAR）签名，
                // 保证旧版签名读取路径/第三方工具也能校验，v2/v3 保持默认开启
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.personalovertimerecord"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // CI 注入签名环境变量时使用 release 签名；本地构建仍为未签名/调试行为
            if (hasSigningEnv) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    
    // Biometric Authentication
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    
    // WebDAV will use built-in HttpURLConnection
    
    // Firebase dependencies - 已禁用云同步
    // implementation(libs.firebase.auth.ktx)
    // implementation(libs.firebase.firestore.ktx)
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
