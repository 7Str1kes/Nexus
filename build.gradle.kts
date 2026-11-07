plugins {
    alias(libs.plugins.lombok) apply false
}

allprojects {
    // JitPack serves multi-module builds as com.github.<user>.<repo>:<module>; keeping the same group
    // here lets plugins substitute this build with --include-build during development.
    group = "com.github.7Str1kes.Nexus"
    version = "2.0.0"
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "io.freefair.lombok")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://jitpack.io")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        // Reports public and protected members without Javadoc: ./gradlew compileJava -PlintDocs
        if (project.hasProperty("lintDocs")) {
            options.compilerArgs.addAll(listOf("-Xdoclint:missing/protected", "-Xmaxwarns", "5000"))
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:all,-missing", "-quiet")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
