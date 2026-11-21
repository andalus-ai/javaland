allprojects {
    group = "tech.kayys.javaland"
    version = providers.gradleProperty("javalandVersion").getOrElse("0.1.0-SNAPSHOT")
}
