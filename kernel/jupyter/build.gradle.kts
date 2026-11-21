import org.gradle.api.file.DuplicatesStrategy

plugins {
    `java-library`
    `maven-publish`
}


group = "tech.kayys.javaland"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(group = "org.dflib.jjava", name = "jjava", version = "1.0-a4")
    // The kernel itself only needs Javaland ML jars at runtime so notebook
    // snippets can import them; source compilation uses reflection.
    runtimeOnly("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    runtimeOnly(project(":ml:javaland-ml-autograd"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    testImplementation("tech.kayys.aljabr:aljabr-core:0.1.0-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED")
}

tasks.jar {
    archiveBaseName.set("javaland-kernel")
    manifest {
        attributes(
            mapOf(
                "Main-Class" to "tech.kayys.javaland.jupyter.KernelLauncher",
                "Automatic-Module-Name" to "tech.kayys.javaland.jupyter.kernel"
            )
        )
    }
}

val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assemble a runnable standalone Javaland Jupyter kernel jar."
    archiveBaseName.set("javaland-kernel")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            mapOf(
                "Main-Class" to "tech.kayys.javaland.jupyter.KernelLauncher",
                "Automatic-Module-Name" to "tech.kayys.javaland.jupyter.kernel"
            )
        )
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}

tasks.assemble {
    dependsOn(fatJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
