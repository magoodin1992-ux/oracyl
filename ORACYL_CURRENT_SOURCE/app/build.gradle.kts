plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("com.chaquo.python") }

val releaseApplicationId = providers.gradleProperty("oracylApplicationId").orElse("com.oracyl.workstation").get()
val releaseVersionCode = providers.gradleProperty("oracylVersionCode").orElse("1").get().toInt()
val releaseVersionName = providers.gradleProperty("oracylVersionName").orElse("0.2-precompile").get()
val stagedLlamaDir = layout.buildDirectory.dir("generated/oracylJniLibs/arm64-v8a")
val stageLlamaRuntime by tasks.registering(Exec::class) {
 commandLine("python3", "${rootProject.projectDir}/tools/stage_llama_runtime.py",
  "${rootProject.projectDir}/../dependencies/llama-b11707-bin-android-arm64.tar.gz",
  stagedLlamaDir.get().asFile.absolutePath)
 inputs.file("${rootProject.projectDir}/../dependencies/llama-b11707-bin-android-arm64.tar.gz")
 outputs.dir(stagedLlamaDir)
}
android {
 namespace = "com.oracyl.workstation"
 compileSdk = 36
 defaultConfig {
  applicationId = releaseApplicationId
  minSdk = 29
  targetSdk = 35
  versionCode = releaseVersionCode
  versionName = releaseVersionName
  ndk { abiFilters += listOf("arm64-v8a") }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 packaging {
  resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*")
  jniLibs.useLegacyPackaging = true
  jniLibs.keepDebugSymbols += setOf("**/*.so")
 }
 // Package the one protected baseline in place; never make a source-tree copy.
 sourceSets.getByName("main").assets.srcDir("../../victor")
 sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/oracylJniLibs"))
}
chaquopy { defaultConfig { version = "3.13" } }
tasks.named("preBuild").configure { dependsOn(stageLlamaRuntime) }
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.webkit:webkit:1.12.1")
 implementation("androidx.work:work-runtime:2.12.0")
 implementation("org.mozilla.geckoview:geckoview-arm64-v8a:158.0.20261007201254")
}