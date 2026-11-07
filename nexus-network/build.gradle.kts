dependencies {
    api(project(":nexus-core"))

    compileOnly(libs.paper.api)
    compileOnly(libs.jedis)
    compileOnly(libs.gson)

    testImplementation(libs.paper.api)
    testImplementation(libs.gson)
    testImplementation(libs.jedis)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
