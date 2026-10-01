// 律动课表 - 应用模块构建配置

import java.net.HttpURLConnection
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ===== Release 签名（可选）=====
// 本地与 CI 统一走这四个环境变量；未配置时保持 unsigned，方便 fork 与本地调试。
//   NEXIO_KEYSTORE_FILE      签名库路径（CI 中由 secret 落盘后传入）
//   NEXIO_KEYSTORE_PASSWORD  签名库口令
//   NEXIO_KEY_ALIAS          key alias
//   NEXIO_KEY_PASSWORD       key 口令（多数签名库与库口令相同）
val keystoreFilePath = System.getenv("NEXIO_KEYSTORE_FILE")
val keystoreFile = keystoreFilePath?.takeIf { it.isNotBlank() }?.let { file(it) }
val hasSigningConfig = keystoreFile?.exists() == true

android {
    namespace = "com.cadence.schedule"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cadence.schedule"
        minSdk = 31
        targetSdk = 37
        // versionCode 继续递增（更名前最大为 158），不因更名回退：
        // Android 用它判断能否覆盖安装，回退会带来不必要的风险。
        versionCode = 159
        versionName = "1.0.0-1001"

        // 更新包签名指纹（SHA-256 大写十六进制，无分隔符）。
        // 经 Gradle 属性注入，勿把指纹硬编码进仓库；未配置时回退为「与当前安装包同签名」。
        // 构建命令示例：-Pcadence.expectedSignerSha256=AB12...（也可写进 ~/.gradle/gradle.properties）
        val expectedSigner = (project.findProperty("cadence.expectedSignerSha256") as String?)
            ?.replace(":", "")
            ?.uppercase()
            .orEmpty()
        buildConfigField("String", "EXPECTED_SIGNER_SHA256", "\"$expectedSigner\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("NEXIO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NEXIO_KEY_ALIAS")
                keyPassword = System.getenv("NEXIO_KEY_PASSWORD")
                // 走 v1+v2 签名：minSdk 31 其实只需 v2，但保留 v1 便于个别设备校验
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 未提供签名环境变量时保持 unsigned：CI 缺 secret 或 fork 构建不应直接失败
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
}

composeCompiler {
    stabilityConfigurationFiles.set(listOf(project.layout.projectDirectory.file("compose-stability.conf")))
}

// miuix-ui 已 fork 到本地源码，排除传递依赖中的 miuix-ui jar 避免 R8 重复定义
configurations.all {
    exclude(group = "top.yukonga.miuix.kmp", module = "miuix-ui-android")
}

dependencies {
    // ===== AndroidX / Compose 基础 =====
    // Compose BOM：统一管理所有 Compose 库版本
    implementation(platform(libs.androidx.compose.bom))
    // Activity 与 Compose 集成（setContent 入口）
    implementation(libs.androidx.activity.compose)
    // Compose UI 核心运行时
    implementation(libs.androidx.compose.ui)
    // Compose 图形模块（Canvas、绘制等）
    implementation(libs.androidx.compose.ui.graphics)
    // Material3 组件库
    implementation(libs.androidx.compose.material3)
    // AndroidX 核心 KTX 扩展
    implementation(libs.androidx.core.ktx)
    // 生命周期感知型运行时 KTX
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // ViewModel 与 Compose 集成
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // AndroidX WebKit，桌面版视口必须在页面脚本前注入
    implementation(libs.androidx.webkit)

    // ===== Miuix UI =====
    // miuix-ui 已 fork 到本地源码，不再使用 jar 依赖
    // 偏好设置组件
    implementation(libs.miuix.preference)
    // 图标资源
    implementation(libs.miuix.icons)
    // Squircle形状支持
    implementation(libs.miuix.squircle)
    // 模糊效果支持
    implementation(libs.miuix.blur)
    // Navigation3 导航组件
    implementation(libs.miuix.navigation3)

    // ===== NavigationEvent =====
    // SearchBar 返回键处理
    implementation(libs.navigationevent.compose)

    // 其编译的 AGSL 运行时需要 org.jetbrains 注解
    implementation("org.jetbrains:annotations:26.1.0")
    // Material Color（miuix theme 依赖）
    implementation(libs.materialKolor.utilities)

    // ===== 序列化 =====
    // Gson：JSON 序列化/反序列化（课表数据持久化）
    implementation(libs.gson)

    // ===== Shizuku =====
    // Shizuku API：运行时服务调用
    implementation(libs.shizuku.api)
    // Shizuku Provider：进程间通信接入
    implementation(libs.shizuku.provider)

    // ===== 网络 =====
    // OkHttp：HTTP 客户端
    implementation(libs.okhttp)

    // ===== 调试专用 =====
    // Compose UI Tooling：Layout Inspector
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ===== 单元测试（纯 JVM，无 Robolectric）=====
    testImplementation(libs.junit)
}

// ===== 教务索引内置 =====
// Release 构建时把 school_index.pb 打包进 assets，使用户首次使用教务导入时
// 无需联网即可获得学校/适配器索引（含 importUrl、脚本路径），进入后脚本仍按需下载。
//
// 该文件在 .gitignore 内，因此**全新检出时必须联网拉取**，否则产出的 APK 首启没有学校列表
// （SchoolRepository.getSchools 会返回空列表，用户只看到空白且没有报错）。
// 已存在合法索引时直接复用：避免每次构建都发一次无意义的网络请求，
// 也避免在 assets 只读或受限的环境（部分本地环境、沙箱）里因无法覆盖而失败。
val eduIndexMinValidBytes = 16L * 1024L

/**
 * 内置索引的处置结论。
 *
 * 判定逻辑放在独立对象里而不是任务动作内，是为了让"拉取失败且无可用索引 →
 * 构建必须失败"这条分支能被单独验证（真实构建里很难安全地走到它）。
 *
 * 注意：这里刻意不接收 lambda 参数，也不引用任何 project/script 对象，
 * 否则任务动作会捕获脚本对象引用，在 `org.gradle.configuration-cache=true` 下直接构建失败。
 */
object EduIndexPolicy {
    /** 已有可用索引，无需联网 */
    const val REUSE = "reuse"
    /** 成功写入新索引 */
    const val DOWNLOADED = "downloaded"
    /** 拉取失败且没有可用索引，构建必须失败 */
    const val NO_FALLBACK = "no-fallback"

    /**
     * 现有索引是否达到可用下限。
     * 下限不只是"非空"：半截响应或残留的防盗链 HTML 都可能非空但不可用。
     */
    fun isUsable(length: Long, minValidBytes: Long): Boolean = length >= minValidBytes

    /**
     * 依据"下载前是否已有可用索引 + 下载结果"给出结论。
     *
     * 只要能走到下载分支，就说明下载前的索引**不可用**，因此不存在
     * "拉取失败但沿用已有索引"的情形——那正是 SchoolRepository 会静默返回空学校列表的原因，
     * 必须让构建失败。
     *
     * @param existingBytes 下载前已有文件的字节数（不存在为 0）
     * @param downloadSucceeded 下载是否成功完成
     * @param resultBytes 下载后文件的字节数（不存在为 0）
     */
    fun decide(
        existingBytes: Long,
        minValidBytes: Long,
        downloadSucceeded: Boolean,
        resultBytes: Long,
    ): String = when {
        isUsable(existingBytes, minValidBytes) -> REUSE
        downloadSucceeded && isUsable(resultBytes, minValidBytes) -> DOWNLOADED
        else -> NO_FALLBACK
    }
}

tasks.register<DefaultTask>("downloadEduIndex") {
    group = "eduimport"
    description = "确保 assets/eduloader/school_index.pb 存在：已有则复用，缺失则从云端拉取"
    val targetLocation = project.layout.projectDirectory.dir("src/main/assets/eduloader/school_index.pb").asFile
    val minValidBytes = eduIndexMinValidBytes
    outputs.upToDateWhen { false } // 判定放在 doLast 内：已有合法索引即跳过网络
    doLast {
        val url = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/index-pb-release/school_index.pb"
        val beforeBytes = targetLocation.takeIf { it.isFile }?.length() ?: 0L
        var downloadSucceeded = false
        var failureReason: String? = null

        if (!EduIndexPolicy.isUsable(beforeBytes, minValidBytes)) {
            try {
                targetLocation.parentFile?.mkdirs()
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "okhttp/4.12.0")
                    setRequestProperty("Accept", "*/*")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val contentType = connection.contentType?.lowercase() ?: ""
                if (contentType.contains("text/html")) {
                    throw RuntimeException("gitee 返回了 HTML 页面（可能被反爬拦截）")
                }
                connection.inputStream.use { inbound ->
                    targetLocation.outputStream().use { outbound ->
                        inbound.copyTo(outbound)
                    }
                }
                downloadSucceeded = true
            } catch (e: Exception) {
                failureReason = e.message ?: e.toString()
            }
        }

        val afterBytes = targetLocation.takeIf { it.isFile }?.length() ?: 0L
        when (EduIndexPolicy.decide(beforeBytes, minValidBytes, downloadSucceeded, afterBytes)) {
            EduIndexPolicy.REUSE ->
                println("[EduIndex] 复用已有内置索引（${beforeBytes / 1024}KB）-> ${targetLocation.absolutePath}")

            EduIndexPolicy.DOWNLOADED ->
                println("[EduIndex] 已拉取最新索引（${afterBytes / 1024}KB）-> ${targetLocation.absolutePath}")

            else ->
                throw GradleException(
                    "[EduIndex] 索引拉取失败且无可用的内置索引，Release 产物会缺失教务导入数据" +
                        "（首启进入教务导入只能看到空白学校列表）。" +
                        "请检查网络或 Gitee 可达性后重试。原因: $failureReason",
                )
        }
    }
}

// 仅 Release 变体打包资产时拉取内嵌索引：Debug 等构建不触发。
// configureEach 惰性挂依赖，规避 release 任务未实例化时的急切解析。
tasks.configureEach {
    if (name == "mergeReleaseAssets") {
        dependsOn("downloadEduIndex")
    }
}


