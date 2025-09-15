// build.gradle.kts
plugins {
    kotlin("jvm") version "2.2.0"
    application
    `maven-publish`
    `java-library`
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "com.kareemessam09"
version = "1.0.0" // Production version

application {
    mainClass.set("com.kareemessam09.bankMessagetokinizer.MainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlinx-dl/maven") }
}

dependencies {
    // Core library dependencies
    api("com.microsoft.onnxruntime:onnxruntime:1.18.0")
    api("com.google.code.gson:gson:2.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Optional dependencies for advanced features
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-api:0.5.2")
    implementation("org.jetbrains.kotlinx:kotlin-deeplearning-onnx:0.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.mockito:mockito-core:5.1.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11) // Changed to Java 11 for better compatibility
}

// JAR configuration for library distribution
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "BankMessageTokinizer",
            "Implementation-Version" to project.version,
            "Main-Class" to "com.kareemessam09.bankMessagetokinizer.MainKt"
        )
    }

    // Include resources in JAR
    from(sourceSets.main.get().resources) {
        include("**/*")
    }
}

// Fat JAR for standalone deployment
tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Create a fat JAR with all dependencies"

    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Implementation-Title" to "BankMessageTokinizer",
            "Implementation-Version" to project.version,
            "Main-Class" to "com.kareemessam09.bankMessagetokinizer.MainKt"
        )
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

// Maven publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "bank-message-tokenizer"
            version = project.version.toString()

            from(components["java"])

            pom {
                name.set("BankMessageTokinizer")
                description.set("Advanced AI-Powered Financial Named Entity Recognition Library for Banking Messages")
                url.set("https://github.com/kareemessam09/BankMessageTokinizer")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("kareemessam09")
                        name.set("Kareem Essam")
                        email.set("kareem.essam09@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/kareemessam09/BankMessageTokinizer.git")
                    developerConnection.set("scm:git:ssh://github.com:kareemessam09/BankMessageTokinizer.git")
                    url.set("https://github.com/kareemessam09/BankMessageTokinizer")
                }
            }
        }
    }
}

// Documentation tasks
tasks.dokkaHtml.configure {
    outputDirectory.set(buildDir.resolve("docs"))

    dokkaSourceSets {
        named("main") {
            moduleName.set("BankMessageTokinizer")
            moduleVersion.set(project.version.toString())

            includes.from("README.md")

            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(uri("https://github.com/kareemessam09/BankMessageTokinizer/tree/main/src/main/kotlin").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}