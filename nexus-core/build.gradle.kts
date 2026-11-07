dependencies {
    compileOnly(libs.paper.api)
    // Optional integrations: referenced only from classes that are loaded after a presence check.
    compileOnly(libs.placeholderapi)
    compileOnly(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
