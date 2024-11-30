import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.apache.commons.io.output.ByteArrayOutputStream
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}
apply(from = "download.gradle")

fun releaseTime(): String {
    val dateFormat = SimpleDateFormat("yy.MMddHH")
    dateFormat.timeZone = TimeZone.getTimeZone("GMT+8:00")
    return dateFormat.format(Date())
}

fun commitCount(): Int {
    val output = ByteArrayOutputStream()
    project.exec {
        commandLine = "git rev-list HEAD --count".split(" ")
        standardOutput = output
    }

    return String(output.toByteArray()).trim().toInt()
}

val appName = "legado"
val version = "3." + releaseTime()
val gitCommits = commitCount()

val CronetVersion: String by project
val CronetMainVersion: String by project

kotlin {
    jvmToolchain(17)
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)

            implementation(libs.bundles.jetbrains.lifecycle)
            implementation(libs.bundles.jetbrains.lifecycle.viewmodel)

            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
        }

        androidMain.dependencies {
//            implementation(compose.preview)
//            implementation(libs.androidx.activity.compose)

            //lifecycle
//            implementation(libs.androidx.lifecycle.common.java8)
            implementation(libs.androidx.lifecycle.service)
        }
        androidUnitTest.dependencies {

        }
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.room.testing)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    namespace = "io.legado.app"
    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    signingConfigs {
        if (project.hasProperty("RELEASE_STORE_FILE")) {
            create("myConfig") {
                storeFile = file(project.properties["RELEASE_STORE_FILE"]!!)
                storePassword = project.properties["RELEASE_STORE_PASSWORD"] as String?
                keyAlias = project.properties["RELEASE_KEY_ALIAS"] as String?
                keyPassword = project.properties["RELEASE_KEY_PASSWORD"] as String?
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }
    defaultConfig {
        applicationId = "io.legado.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 10000 + gitCommits
        versionName = version
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        project.ext.set("archivesBaseName", name + "_" + version)

        buildConfigField("String", "Cronet_Version", "\"$CronetVersion\"")
        buildConfigField("String", "Cronet_Main_Version", "\"$CronetMainVersion\"")
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }
    buildTypes {
        release {
            if (project.hasProperty("RELEASE_STORE_FILE")) {
                signingConfig = signingConfigs.findByName("myConfig")
            }
            applicationIdSuffix = ".release"

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "cronet-proguard-rules.pro"
            )
        }
        debug {
            if (project.hasProperty("RELEASE_STORE_FILE")) {
                signingConfig = signingConfigs.findByName("myConfig")
            }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "debug"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "cronet-proguard-rules.pro"
            )
        }
    }
    //noinspection GrDeprecatedAPIUsage
    flavorDimensions += listOf("mode")
    productFlavors {
        create("app") {
            dimension = "mode"
            manifestPlaceholders["APP_CHANNEL_VALUE"] = "app"
        }
    }

    applicationVariants.all {
        outputs.map {
            it as BaseVariantOutputImpl
        }.forEach { output ->
            val flavor = productFlavors[0].name
            output.outputFileName = "${appName}_${flavor}_${defaultConfig.versionName}.apk"
        }
    }


    compileOptions {
        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true
        // Sets Java compatibility
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes.add("META-INF/*")
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    lint {
        checkDependencies = true
    }
    tasks.withType(JavaCompile::class.java).configureEach {
        options.compilerArgs.add("-Xlint:unchecked")
    }
}

dependencies {
    //compileOnly "com.android.tools.build:gradle:$agp_version"
    //noinspection GradleDependency,GradlePackageUpdate
    //coreLibraryDesugaring('com.android.tools:desugar_jdk_libs_nio:2.0.4')
    coreLibraryDesugaring(libs.desugar)
    testImplementation(libs.junit)
    androidTestImplementation(libs.bundles.androidTest)
    //kotlin
    //noinspection GradleDependency,DifferentStdlibGradleVersion
    implementation(libs.kotlin.stdlib)
    //Kotlin反射
    //noinspection GradleDependency,DifferentStdlibGradleVersion
    implementation(libs.kotlin.reflect)


    //协程
    //def coroutines_version = '1.7.3'
    implementation(libs.bundles.coroutines)


    //图像处理库Toolkit
    implementation(libs.renderscript.intrinsics.replacement.toolkit)

    //androidX
    implementation(libs.androidx.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.startup.runtime)

    //google
    implementation(libs.material)
    implementation(libs.flexbox)
    implementation(libs.gson)

    //media
    implementation(libs.androidx.media)
    // For media playback using ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    // For loading data using the OkHttp network stack
    implementation(libs.androidx.media3.datasource.okhttp)
    // For exposing and controlling media sessions
    //implementation "androidx.media3:media3-session:$media3_version"

    //Splitties
    implementation(libs.splitties.appctx)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views)

    //room sql语句不高亮解决方法https://issuetracker.google.com/issues/234612964#comment6
//    implementation(libs.androidx.room.runtime)
//    implementation(libs.androidx.room.ktx)
    //kapt("androidx.room:room-compiler:$room_version")
//    ksp(libs.androidx.room.compiler)
//    androidTestImplementation(libs.androidx.room.testing)

    //liveEventBus
    implementation(libs.liveeventbus)

    //规则相关
    implementation(libs.jsoup)
    implementation(libs.json.path)
    implementation(libs.jsoupxpath)
    implementation(project(":modules:book"))
    implementation(project(":modules:rhino"))

    //JS rhino

    //网络
    implementation(libs.okhttp)
    implementation(fileTree("dir" to "cronetlib", "include" to listOf("*.jar", "'*.aar")))
    implementation(libs.protobuf.javalite)

    //Glide
    implementation(libs.glide.glide)
    ksp(libs.glide.ksp)

    //Svg
    implementation(libs.androidsvg)
    //Glide svg plugin
    implementation(libs.glide.svg)

    //webServer
    implementation(libs.nanohttpd.nanohttpd)
    implementation(libs.nanohttpd.websocket)

    //二维码
    //noinspection GradleDependency
    implementation(libs.zxing.lite)

    //颜色选择
    implementation(libs.colorpicker)

    //压缩解压
    implementation(libs.libarchive)

    //apache
    implementation(libs.commons.text)

    //MarkDown
    implementation(libs.markwon.core)
    implementation(libs.markwon.image.glide)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.html)

    //转换繁体
    implementation(libs.quick.chinese.transfer.core)

    //加解密类库,有些书源使用
    //noinspection GradleDependency,GradlePackageUpdate
    implementation(libs.hutool.crypto)

    //firebase, 崩溃统计和性能统计
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)

    //LeakCanary, 内存泄露检测
    //debugImplementation('com.squareup.leakcanary:leakcanary-android:2.7')

    //com.github.AmrDeveloper:CodeView代码编辑已集成到应用内
    //epubLib集成到应用内
}

compose.desktop {
    application {
        mainClass = "com.coopsrc.matrix.reader.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.coopsrc.matrix.reader"
            packageVersion = "1.0.0"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
// 设定Room的KSP参数
ksp {
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
    arg("room.generateKotlin", "true")
//    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    ksp(libs.androidx.room.compiler)
}
