pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ExecuTorch 1.5.1 with QNN and MediaTek's NPU runtime, which no published artifact
        // carries: assembled by tools/npu/assemble_runtime.sh.
        maven {
            url = uri("core/engine/maven")
            content { includeGroup("org.experimentalmachines.executorch") }
        }
    }
}

rootProject.name = "OpenWeights"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:engine")
include(":core:hub")
include(":core:tools")
include(":core:device")
include(":core:data")
include(":core:sandbox")

// Not shipped. It drives the app on a device to record the profile the app ships with.
include(":baselineprofile")
