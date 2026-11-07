dependencies {
    api(project(":nexus-core"))

    compileOnly(libs.paper.api)
    compileOnly(libs.hikaricp)
    compileOnly(libs.mongodb.driver.sync)

    testImplementation(libs.paper.api)
    testImplementation(libs.hikaricp)
    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
