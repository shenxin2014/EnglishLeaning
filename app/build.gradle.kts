import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val whisperModel = layout.projectDirectory.file("src/main/assets/models/ggml-tiny.en.bin")
val whisperModelSha256 = "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f"

/** 流式校验模型，避免将整个模型文件读入内存。 */
fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        var count = input.read(buffer)
        while (count != -1) {
            digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val prepareWhisperModel by tasks.registering {
    description = "Verify or download the English Whisper model for offline transcription."
    inputs.property("sha256", whisperModelSha256)
    outputs.file(whisperModel)

    doLast {
        val model = whisperModel.asFile
        if (model.isFile && sha256(model) == whisperModelSha256) return@doLast

        check(!gradle.startParameter.isOffline) {
            "离线构建需要已有的 ggml-tiny.en.bin。请联网运行 :app:prepareWhisperModel。"
        }
        model.parentFile.mkdirs()
        val temporaryModel = File.createTempFile("whisper-", ".download", temporaryDir)
        try {
            val connection = URI("https://huggingface.co/ggerganov/whisper.cpp/resolve/80da2d8/ggml-tiny.en.bin")
                .toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                }
            connection.getInputStream().use { input ->
                temporaryModel.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            check(sha256(temporaryModel) == whisperModelSha256) { "下载的 Whisper 模型 SHA-256 校验失败。" }
            Files.move(temporaryModel.toPath(), model.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporaryModel.delete()
        }
    }
}

tasks.named("preBuild") {
    dependsOn(prepareWhisperModel)
}

android {
    namespace = "com.example.listenenglish"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.example.listenenglish"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildFeatures { viewBinding = true }

    androidResources {
        noCompress += "bin"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    testImplementation("junit:junit:4.13.2")
}
