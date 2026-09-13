import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("openweights.android.application")
    id("openweights.android.compose")
    id("openweights.android.hilt")
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * The lowest version code this repository may build from.
 *
 * A ratchet, and the only state the scheme keeps: raised by hand, and only ever when a
 * rewritten history has taken the commit count below a code already uploaded. Left alone it
 * costs nothing, because the count passes it on every ordinary commit.
 *
 * Declared before [gitCommitCount] because a script initialises its properties in the order
 * they are written, and a forward reference here would read zero and check nothing.
 */
val versionCodeFloor = 498

/**
 * The number of commits on this branch, used as the version code.
 *
 * Play's one rule for a version code is that it must be higher than the last one uploaded,
 * ever, and it is not recoverable: a code that has been used is used, and a build that
 * repeats one is rejected at the door. Typing it by hand is therefore a promise to remember
 * something, indefinitely, while doing something else, and the failure mode is finding out
 * at upload time with a bundle already built.
 *
 * The commit count is the cheapest thing that is nearly always going up. It needs no service
 * account, no secret, and no state anywhere outside the repository, and it produces the same
 * answer on this laptop as in CI, which a build-number counter does not: a workflow renamed
 * or recreated resets that counter, and a version code that goes down cannot be undone.
 *
 * Three things to know about it.
 *
 * It is per branch, so a release must be cut from `main`. A build from a branch with fewer
 * commits produces a lower code, which Play will refuse rather than accept, so the failure
 * is loud.
 *
 * And it *can* go backwards, which the note here used to deny: a squash merge replaces many
 * commits with one, a rebase can drop them, and a history cleanup rewrites the lot. Play
 * refuses the bundle at the door in that case, after it has been built and signed, with no
 * way to recover the codes already used. So the count is ratcheted against
 * [versionCodeFloor] below and the build stops here instead, before anything is built,
 * with the one instruction that fixes it.
 *
 * And it is wrong on a shallow clone, quietly, which is the one that would actually have
 * bitten: `actions/checkout` fetches a single commit by default, so a naive count returns 1
 * in CI while returning a hundred and something locally. That is why the shallow case throws
 * rather than falling back, and why the workflow asks for the full history.
 */
val gitCommitCount: Int = run {
    val git = { args: List<String> ->
        runCatching {
            providers.exec {
                commandLine(args)
                isIgnoreExitValue = true
            }.standardOutput.asText.get().trim()
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    if (git(listOf("git", "rev-parse", "--is-shallow-repository")) == "true") {
        throw GradleException(
            "This is a shallow clone, so the commit count is not the real one and the " +
                "version code built from it would be wrong. Fetch the full history " +
                "(actions/checkout with fetch-depth: 0) and build again.",
        )
    }

    val counted = git(listOf("git", "rev-list", "--count", "HEAD"))?.toIntOrNull()

    // Absent git entirely, which is a source archive rather than a checkout. One is the
    // lowest code Play accepts and nothing built this way is publishable anyway. Only that
    // case falls back: a checkout that has git and still cannot be counted is a broken
    // build, not a source archive, and the difference decides what gets uploaded.
    if (counted == null && rootProject.file(".git").exists()) {
        throw GradleException(
            "There is a .git here but the commit count could not be read, so the version " +
                "code would silently be 1. Build again and check that git runs.",
        )
    }
    val code = counted?.takeIf { it > 0 } ?: 1

    // Only when git answered. The line above deliberately reads 1 for a source archive with
    // no history at all, and nothing built that way is publishable, so it is not held to a
    // floor it cannot meet.
    if (counted != null && code < versionCodeFloor) {
        throw GradleException(
            "The commit count is $code, below the $versionCodeFloor this repository has " +
                "already built from, so this bundle's version code would go backwards and " +
                "Play would refuse it. History was rewritten by a squash, a rebase, " +
                "or a cleanup. Raise versionCodeFloor in app/build.gradle.kts above the " +
                "highest code ever uploaded, and add that difference to the count.",
        )
    }
    code
}

android {
    namespace = "io.github.alpharomercoma.openweights"

    defaultConfig {
        applicationId = "io.github.alpharomercoma.openweights"
        // Counted, not typed. See gitCommitCount.
        versionCode = gitCommitCount
        // Typed, not counted, and deliberately the other way round from the line above. A
        // version name is editorial: it says how big a change this is, which is a judgement
        // no tool can make. A version code is a counter Play uses to order uploads and
        // nothing else, and the one requirement on it is that it never repeats, which is
        // exactly the kind of promise a person forgets and a machine does not.
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Off by default since AGP 8, and About needs the version name it generates. Nothing
    // else here reads BuildConfig, so this exists to let one screen say which build it is.
    buildFeatures { buildConfig = true }

    /*
     * Release signing.
     *
     * Read from a properties file or the environment, never from the repository. Play App
     * Signing holds the key that users verify against; this is only the upload key, but an
     * upload key in git is still an upload key anyone can use.
     *
     * Local:  keystore.properties in the project root, git-ignored.
     * CI:     OPENWEIGHTS_KEYSTORE and friends in the environment.
     *
     * Absent, the release build is simply unsigned, which is what a contributor building
     * from a fresh clone should get rather than a confusing failure.
     */
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

    fun secret(key: String, env: String): String? =
        keystoreProperties.getProperty(key) ?: System.getenv(env)

    val storePath = secret("storeFile", "OPENWEIGHTS_KEYSTORE")

    signingConfigs {
        if (storePath != null) {
            create("release") {
                // Resolved against the root, where keystore.properties lives. file()
                // here would resolve a relative path under app/ instead.
                storeFile = rootProject.file(storePath)
                storePassword = secret("storePassword", "OPENWEIGHTS_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "OPENWEIGHTS_KEY_ALIAS")
                keyPassword = secret("keyPassword", "OPENWEIGHTS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Play requires 16 KB-aligned uncompressed shared libraries.
        jniLibs.useLegacyPackaging = false

        // Two copies of the C++ runtime arrive in the APK: ours, from the CMake builds in
        // core:engine and core:sandbox, and a prebuilt one inside fbjni, which ExecuTorch
        // depends on for its JNI. They are the same library from different NDKs, and the
        // merger will not choose between them.
        //
        // Taking the first is what every project carrying fbjni does. It is safe because
        // libc++_shared is backward compatible and both are recent, but it is a real
        // decision rather than boilerplate: if ExecuTorch ever ships against an NDK newer
        // than ours, the copy that wins could be older than the one its .so was linked
        // against, and the symptom would be a link error at load rather than here.
        jniLibs.pickFirsts += "**/libc++_shared.so"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:engine"))
    implementation(project(":core:hub"))
    implementation(project(":core:tools"))
    implementation(project(":core:device"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    // Coil 3 split network loading into its own artifact. Without it AsyncImage has no
    // fetcher for an https model, fails silently, and every publisher tile in Discover fell
    // back to drawing initials: the avatars were being looked up correctly and thrown away.
    implementation(libs.coil.network.okhttp)
    // Hugging Face gives an account that never uploaded a picture a generated identicon,
    // served from /avatars/<hash>.svg. Coil decodes no SVG without this artifact, so those
    // publishers drew an empty slot: on a trending page of GGUF repositories that is nine
    // of the thirty-eight individual accounts and none of the nineteen organisations,
    // which is why it read as personal publishers having no logo at all.
    implementation(libs.coil.svg)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    // Downloads outlive the screen that started them, so they run as WorkManager jobs and
    // the worker is built by Hilt rather than by the default factory that cannot inject.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    // Installs the shipped profile on the devices whose Play Store does not do it for us,
    // which is most of them below API 31 and any sideloaded build. Without it the profile
    // is carried and never applied, which is the quiet way to have done this work twice.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.core.splashscreen)

    // Not shipped: the module that records the profile above by driving the app on a device.
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.room.testing)
    // A real WorkManager on the host, so ModelsViewModel can be driven rather than mocked:
    // the behaviour worth testing there is what it does with the queue's emissions, and a
    // fake queue would be a fake of the exact thing that went wrong.
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp)
    // The catalogue test builds the real tool set to check every schema a model is shown,
    // which means reaching the two things core:tools keeps to itself.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:sandbox"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.okhttp)
    // Only the benchmark reaches the sandbox directly; core:tools keeps it internal.
    androidTestImplementation(project(":core:sandbox"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Proves the names JNI resolves at runtime survived R8.
 *
 * A release build once aborted the process on the first token of every reply because a keep
 * rule said `allowobfuscation` and R8 duly renamed `onToken`, the one string the native side
 * looks up. No debug build can catch that, because R8 does not run there, and the checklist
 * that said to re-test the release build by hand did not get followed.
 *
 * So it is a build step now. Dex stores method names as plain UTF-8, so this reads the
 * shipped artifact rather than the rules that were meant to protect it: it fails on what
 * actually got built, not on what was intended.
 *
 * A name is not enough, and that was learned in production. From version code 604 the
 * native side asked for `onToken(Ljava/lang/String;F)Z` while the keep rule still named the
 * one-argument method, R8 dropped the method, and the string "onToken" was still somewhere
 * in the dex, so this check passed on every build that aborted on every GGUF reply
 * (2026-09-14). The callbacks are therefore also checked by name and signature against
 * the dex's own method table, and the pairs are read out of llama_jni.cpp rather than
 * typed here, so a signature changed in C++ is a signature checked in the next build.
 */
val jniSymbols = listOf(
    // Resolved by GetMethodID during generation. The class names are free to change,
    // because native code reaches them through GetObjectClass.
    "onToken",
    "onReply",
    // Resolved by the dynamic linker as Java_..._LlamaBridge_nativeGenerate and friends,
    // so for these the package and class name have to survive as well.
    "nativeGenerate",
    "nativeLoadModel",
    "LlamaBridge",
    "LlamaException",
    // fbjni finds every one of these from native code by descriptor: the exception
    // classes when it raises a C++ exception, the destructor when it wraps a native
    // pointer. Stripped, it aborts the process instead (core/engine/consumer-rules.pro).
    // Nothing here that R8 may legitimately inline away, such as a config builder.
    "com.facebook.jni.CppException",
    "com.facebook.jni.UnknownCppException",
    "com.facebook.jni.CppSystemErrorException",
    "com.facebook.jni.HybridData",
    "com.facebook.jni.HybridData\$Destructor",
    "com.facebook.jni.HybridClassBase",
    "com.facebook.jni.ExceptionHelper",
    "com.facebook.jni.DestructorThread",
    "com.facebook.jni.ThreadScopeSupport",
    "org.pytorch.executorch.extension.llm.LlmModule",
    "org.pytorch.executorch.extension.llm.LlmCallback",
)

tasks.register("verifyJniSymbols") {
    group = "verification"
    description = "Fails if R8 renamed or removed a name the native library resolves."

    // Both artifacts, because they are not the same file and only one of them is what
    // Play receives. The guard was written against the APK, which is the one nobody
    // uploads: bundleRelease produces the AAB, and it was going out unchecked.
    // Matched by pattern rather than path so that a stale artifact from an earlier layout
    // (the flavour directories that existed until 2026-09-06) is checked too rather than
    // silently passed over; a wrong file failing is better than a wrong file being skipped.
    val artifacts = fileTree(layout.buildDirectory.dir("outputs/apk")) {
        include("**/release/*.apk")
    } + fileTree(layout.buildDirectory.dir("outputs/bundle")) {
        include("**/*.aab")
    }
    val symbols = jniSymbols
    val jniSource = rootProject.layout.projectDirectory.file(
        "core/engine/src/main/cpp/llama_jni.cpp",
    )
    val jniSourceText = providers.fileContents(jniSource).asText
    inputs.files(artifacts)
    inputs.file(jniSource)

    doLast {
        val built = artifacts.files.filter { it.isFile }
        check(built.isNotEmpty()) {
            "Nothing to check. Run assembleRelease or bundleRelease first."
        }
        // Every instance method the native library resolves with GetMethodID, as the
        // (name, JNI signature) pair it passes. Constructors are left out: `<init>` with a
        // String argument exists on half the classes in the dex and proves nothing.
        val lookups = Regex(
            """GetMethodID\(\s*[^,]+,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)""",
        ).findAll(jniSourceText.get())
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { (name, _) -> name != "<init>" }
            .toSet()
        check(lookups.isNotEmpty()) {
            "verifyJniSymbols found no GetMethodID lookups in llama_jni.cpp; the pattern " +
                "no longer matches the source, so the signature check would pass on nothing."
        }

        // The dex method table, read directly: every method_id is a name, a prototype and
        // a class, and a method R8 renamed or removed is absent from it under its old
        // name and prototype. Offsets are the dex header's (DEX format, "header_item").
        fun dexMethods(dex: ByteArray): Set<Pair<String, String>> {
            val buffer = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
            fun u4(at: Int) = buffer.getInt(at)
            fun u2(at: Int) = buffer.getShort(at).toInt() and 0xFFFF
            fun string(index: Int): String {
                var at = u4(u4(0x3C) + index * 4)
                while ((dex[at].toInt() and 0x80) != 0) at++ // skip the ULEB128 length
                val start = at + 1
                var end = start
                while (dex[end].toInt() != 0) end++
                // Modified UTF-8; every name and descriptor compared here is ASCII.
                return String(dex, start, end - start, Charsets.UTF_8)
            }
            fun type(index: Int) = string(u4(u4(0x44) + index * 4))
            fun proto(index: Int): String {
                val at = u4(0x4C) + index * 12
                val parameters = u4(at + 8)
                val arguments = if (parameters == 0) {
                    ""
                } else {
                    (0 until u4(parameters)).joinToString("") { type(u2(parameters + 4 + it * 2)) }
                }
                return "($arguments)" + type(u4(at + 4))
            }
            val methods = u4(0x5C)
            return (0 until u4(0x58)).map { index ->
                val at = methods + index * 8
                string(u4(at + 4)) to proto(u2(at + 2))
            }.toSet()
        }

        built.forEach { artifact ->
            val found = mutableSetOf<String>()
            val methods = mutableSetOf<Pair<String, String>>()
            ZipFile(artifact).use { zip ->
                zip.entries().asSequence()
                    // An AAB keeps its dex under base/dex/ rather than at the root, so
                    // match on the extension alone and both layouts are covered.
                    .filter { it.name.endsWith(".dex") }
                    .forEach { entry ->
                        val bytes = zip.getInputStream(entry).readBytes()
                        methods += dexMethods(bytes)
                        val text = String(bytes, Charsets.ISO_8859_1)
                        // A bare name is a method or a simple class name, stored as is. A
                        // qualified class name is stored as a descriptor, slashes and all:
                        // `Lcom/facebook/jni/CppException;`. Searching the dotted form
                        // would fail every qualified name, including the ones that survived.
                        symbols.forEach { symbol ->
                            val qualified = '.' in symbol
                            val needle = if (qualified) {
                                "L" + symbol.replace(
                                    '.',
                                    '/',
                                ) + ";"
                            } else {
                                symbol
                            }
                            if (text.contains(needle)) found += symbol
                        }
                    }
            }
            val missing = symbols - found
            check(missing.isEmpty()) {
                "R8 removed or renamed names the native library resolves by string, in " +
                    "${artifact.name}: $missing. Generation would abort the process at " +
                    "runtime. Check core/engine/consumer-rules.pro."
            }
            val lost = lookups - methods
            check(lost.isEmpty()) {
                "No method in ${artifact.name} has the name and signature llama_jni.cpp " +
                    "asks GetMethodID for: ${lost.joinToString { (n, d) -> "$n$d" }}. R8 " +
                    "renamed or removed it, or the Kotlin signature no longer matches the " +
                    "native one; generation would abort the process at runtime. Check " +
                    "core/engine/consumer-rules.pro."
            }
            logger.lifecycle(
                "verifyJniSymbols: all ${symbols.size} names and ${lookups.size} native " +
                    "method signatures survived R8 in ${artifact.name}",
            )
        }
    }
}

// `bundleRelease` as well as `assembleRelease`: the bundle is what Play receives, and
// it was once not on this list.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    finalizedBy("verifyJniSymbols")
}
