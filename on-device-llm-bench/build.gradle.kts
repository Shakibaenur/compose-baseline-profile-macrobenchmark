plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}

// This project lives on an external volume that writes ._* AppleDouble files next to
// every file, which breaks D8. Keep build output on the local disk instead.
allprojects {
    layout.buildDirectory.set(file("${System.getProperty("user.home")}/Library/Caches/llm-bench/${project.name}"))
}
