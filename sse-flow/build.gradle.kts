plugins {
    kotlin("jvm")
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("com.squareup.okhttp3:okhttp:5.4.0")
    api("com.squareup.retrofit2:retrofit:3.0.0")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "sse-flow"
            version = project.version.toString()

            pom {
                name.set("sse-flow")
                description.set(
                    "A lightweight, Flow-based Server-Sent Events (SSE) client for Kotlin/Android, " +
                        "built on OkHttp + Retrofit. Provides connection lifecycle management, " +
                        "configurable retry/backoff policies, and SSE wire-format parsing."
                )
                url.set("https://github.com/youssefelsa3ed/sse-flow-android")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("youssefelsa3ed")
                        name.set("Youssef Farahat")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/youssefelsa3ed/sse-flow-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/youssefelsa3ed/sse-flow-android.git")
                    url.set("https://github.com/youssefelsa3ed/sse-flow-android")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/youssefelsa3ed/sse-flow-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String?
            }
        }
    }
}
