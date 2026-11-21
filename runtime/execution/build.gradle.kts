plugins {
    `java-library`
    `maven-publish`
}

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
    // Temporary evaluator implementation; this module has no dependency on the Jupyter kernel.
    implementation("org.dflib.jjava:jjava:1.0-a4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens=jdk.jshell/jdk.jshell=ALL-UNNAMED")
}

tasks.jar {
    archiveBaseName.set("javaland-execution")
    manifest {
        attributes("Automatic-Module-Name" to "tech.kayys.javaland.runtime")
    }
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
