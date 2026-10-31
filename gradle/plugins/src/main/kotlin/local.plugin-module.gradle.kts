import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask

/*
 * Convention plugin for the content modules of the plugin (see settings.gradle.kts).
 * The IntelliJ Platform is inherited from the root project.
 */

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
}

java {
    // See the root project for why we don't use Gradle's toolchain feature
    sourceCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val libs = versionCatalogs.named("libs")
dependencies {
    compileOnly(libs.findLibrary("jetbrains-annotations").get())
}

tasks {
    // The name of the jar file must match the name of the content module, which is "nix-idea.<project path>".
    // By default, the IntelliJ Platform Gradle Plugin would use "<root project name>.<project path>".
    named<ComposedJarTask>("composedJar") {
        archiveBaseName = "nix-idea." + project.path.removePrefix(":").replace(':', '.')
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
    }
}
