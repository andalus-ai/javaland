plugins {
    `java-library`
    `maven-publish`
}
group = "tech.kayys.javaland"
version = rootProject.version
java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }
repositories { mavenCentral(); mavenLocal() }
dependencies {
    api(project(":protocol:jupyter"))
    implementation("org.zeromq:jeromq:0.6.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}
tasks.test { useJUnitPlatform() }
tasks.jar {
    archiveBaseName.set("javaland-jupyter-zmq")
    manifest { attributes("Automatic-Module-Name" to "tech.kayys.javaland.transport.jupyter.zmq") }
}
publishing {
    publications { create<MavenPublication>("mavenJava") { from(components["java"]) } }
    repositories { mavenLocal() }
}
