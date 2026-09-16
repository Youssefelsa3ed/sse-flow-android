plugins {
    kotlin("jvm") version "2.2.20" apply false
}

allprojects {
    group = "io.github.youssefelsa3ed"
    version = project.findProperty("libraryVersion") as? String ?: "0.1.0"
}
