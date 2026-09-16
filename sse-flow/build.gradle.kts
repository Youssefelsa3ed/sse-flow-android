import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish") version "0.37.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("com.squareup.okhttp3:okhttp:5.4.0")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    configure(KotlinJvm())
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(project.group.toString(), "sse-flow", project.version.toString())

    pom {
        name.set("sse-flow")
        description.set(
            "A lightweight, Flow-based Server-Sent Events (SSE) client for Kotlin/Android, " +
                "built on OkHttp. Provides connection lifecycle management, " +
                "configurable retry/backoff policies, and SSE wire-format parsing."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/youssefelsa3ed/sse-flow-android")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("youssefelsa3ed")
                name.set("Youssef Farahat")
                url.set("https://github.com/youssefelsa3ed")
            }
        }

        scm {
            url.set("https://github.com/youssefelsa3ed/sse-flow-android")
            connection.set("scm:git:git://github.com/youssefelsa3ed/sse-flow-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/youssefelsa3ed/sse-flow-android.git")
        }
    }
}
