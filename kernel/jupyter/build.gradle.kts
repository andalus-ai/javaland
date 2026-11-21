plugins {
    `java-library`
    `maven-publish`
}

import org.gradle.api.file.DuplicatesStrategy

group = "tech.kayys.javaland"
version = rootProject.version

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
    implementation("org.dflib.jjava:jjava:1.0-a4")
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
            "Main-Class" to "tech.kayys.tafkir.jupyter.KernelLauncher",
            "Automatic-Module-Name" to "tech.kayys.javaland.kernel.jupyter"
        )
    }
}

val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Assemble a runnable standalone JavaLand Jupyter kernel jar."
    archiveBaseName.set("javaland-kernel")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to "tech.kayys.tafkir.jupyter.KernelLauncher",
            "Automatic-Module-Name" to "tech.kayys.javaland.kernel.jupyter"
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
