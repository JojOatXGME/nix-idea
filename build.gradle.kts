import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    kotlin("jvm") version embeddedKotlinVersion
    alias(libs.plugins.jetbrains.changelog)
    alias(libs.plugins.jetbrains.intellij.platform)
    alias(libs.plugins.jetbrains.intellij.grammarkit)
    id("local.bump-version")
    id("local.jbr-guidance")
}

// Import variables from gradle.properties file
val pluginGroup = providers.gradleProperty("pluginGroup")
val pluginName = providers.gradleProperty("pluginName")
val pluginVersion = providers.gradleProperty("pluginVersion")
val pluginSinceBuild = providers.gradleProperty("pluginSinceBuild")

val platformType = providers.gradleProperty("platformType")
val platformVersion = providers.gradleProperty("platformVersion")

group = pluginGroup.get()
version = pluginVersion.get()

java {
    // Don't use Gradle's toolchain feature as it prevents building the project with more recent JDKs. Related issues:
    // https://github.com/gradle/gradle/issues/16256 - Ability to set a min language version for a toolchain
    // https://github.com/gradle/gradle/issues/17444 - Toolchains feature does not appear to treat Java as backwards compatible
    // https://github.com/gradle/gradle/issues/18894 - More flexibility in querying Java toolchains
    sourceCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Disable compatibility with Kotlin 2.1 and earlier, or builds using `-jvm-default=disable`.
        // Otherwise, classes implementing platform interfaces (e.g. ToolWindowFactory) override and call
        // @ApiStatus.Internal default methods, which the Plugin Verifier reports as INTERNAL_API_USAGES.
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        // The plugin uses the Kotlin standard library bundled with the IDE (`kotlin.stdlib.default.dependency = false`),
        // so it must not depend on a newer standard library than the oldest supported IDE bundles (2025.2: Kotlin 2.2).
        // Without this, the compiler writes coroutine debug metadata in a newer format, and the bundled standard
        // library fails with "Debug metadata version mismatch" whenever it computes the stack trace of a coroutine.
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val bytebuddyAgent = configurations.register("bytebuddyAgent")
dependencies {
    compileOnly(libs.jetbrains.annotations)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.junit5.platform.testkit)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit5.vintage.engine)
    testImplementation(libs.bytebuddy)
    testImplementation(libs.bytebuddy.agent)
    bytebuddyAgent(libs.bytebuddy.agent)

    intellijPlatform {
        create(platformType, platformVersion)
        bundledModule("intellij.spellchecker")
        testFramework(TestFrameworkType.Platform)
        //testFramework(TestFrameworkType.JUnit5)

        // version of IntelliJ patched JFlex
        // -> https://github.com/JetBrains/intellij-deps-jflex
        jflex("1.10.18")
        // tag or short commit hash of Grammar-Kit to use
        // -> https://github.com/JetBrains/Grammar-Kit
        grammarKit("2023.3.4")
    }
}

// Configure intellij-platform-gradle-plugin plugin.
// Read more: https://github.com/JetBrains/intellij-platform-gradle-plugin
intellijPlatform {
    projectName = pluginName
    pluginConfiguration {
        id = "dev.johanness.nixos-idea-publication-testing"
        name = "NixIDEA (Publication Testing)"
        version = pluginVersion
        vendor {
            name = "johanness.dev"
        }
        ideaVersion {
            sinceBuild = pluginSinceBuild
        }
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.provider {
            projectDir.resolve("README.md").readText().lines()
                .run {
                    val start = "<!-- Plugin description -->"
                    val end = "<!-- Plugin description end -->"
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end))
                }
                .joinToString("\n")
                .run { markdownToHTML(this) }
        }
        // Get the latest available change notes from the changelog file
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion.get()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML
                )
            }
        }
    }
    pluginVerification {
        freeArgs = listOf("-mute", "TemplateWordInPluginName")
        ides {
            create(
                providers.gradleProperty("verifierIdeVersionOverride")
                    // Verify only against the IDE specified by the property
                    .map { listOf(it) }
                    // If property is not set, verify against the IDEs in gradle/productsReleases.txt
                    .orElse(
                        layout.projectDirectory.file("gradle/productsReleases.txt")
                            .let { providers.fileContents(it).asText }
                            .map { it.lines().map(String::trim).filter(String::isNotEmpty) }
                    )
            )
        }
    }
    publishing {
        token = providers.environmentVariable("JETBRAINS_TOKEN")
        // Note: `listOf("foo").first()` does not what you think on Java 21 and Gradle 8.6. (The return type is TaskProvider<Task>)
        // See https://github.com/gradle/gradle/issues/27699 and https://youtrack.jetbrains.com/issue/KT-65235.
        channels = pluginVersion
            .map { it.split('-').getOrNull(1) }
            .map { listOf(it.split('.')[0]) }
            .orElse(listOf("private"))
    }
}

changelog {
    repositoryUrl = "https://github.com/NixOS/nix-idea"
    lineSeparator = "\n"
    // Workarounds because our version numbers do not match the format of semantic versioning:
    headerParserRegex = "^[-._+0-9a-zA-Z]+$"
    combinePreReleases = false
}

sourceSets {
    main {
        java {
            srcDir(tasks.generateLexer.flatMap { it.targetRootOutputDir })
            srcDir(tasks.generateParser.flatMap { it.targetRootOutputDir })
        }
    }
}

tasks {

    intellijPlatformTesting.runIde.register("runIntellij") {
        type = IntelliJPlatformType.IntellijIdeaCommunity
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    withType<Javadoc> {
        options.encoding = "UTF-8"
    }

    register<MetadataTask>("metadata") {
        description = "Writes metadata used by the CI to build/metadata"
        outputDir = layout.buildDirectory.dir("metadata")
        file("version.txt", pluginVersion)
        file("zipfile.txt") { buildPlugin.get().archiveFile.get().toString() }
        file("latest_changelog.md") {
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion.get()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.MARKDOWN
                )
            }
        }
    }

    register<MetadataTask>("updateProductsReleases") {
        description = "Updates gradle/productsReleases.txt used by Plugin Verifier"
        doNotTrackState("Updates files outside of build directory")
        outputDir = layout.projectDirectory.dir("gradle")
        file(
            "productsReleases.txt",
            printProductsReleases.flatMap {
                it.productsReleases
            }.map {
                it.joinToString("\n", "", "\n")
            },
        )
    }

    generateLexer {
        sourceFile = layout.projectDirectory.file("src/main/lang/Nix.flex")
        pathToClass = "org/nixos/idea/lang/_NixLexer.java"
    }

    generateParser {
        sourceFile = layout.projectDirectory.file("src/main/lang/Nix.bnf")
        // Maybe we can remove the following properties in the future
        // https://github.com/JetBrains/gradle-grammar-kit-plugin/issues/178
        pathToParser = "org/nixos/idea/lang/NixParser.java"
        pathToPsiRoot = "org/nixos/idea/psi"
    }

    compileJava {
        dependsOn(generateLexer)
        dependsOn(generateParser)
    }

    compileKotlin {
        dependsOn(generateLexer)
        dependsOn(generateParser)
    }

    test {
        val testDataPath = rootProject.rootDir.resolve("src/test/testData").path
        inputs.dir(testDataPath).withPathSensitivity(PathSensitivity.RELATIVE)

        useJUnitPlatform {
            excludeTags("mock")
        }

        // Print the message and stack trace of failing tests to the console, so failures in CI can be analyzed
        // without downloading the test reports.
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        systemProperty("idea.test.execution.policy", "org.nixos.idea.NixTestExecutionPolicy")
        systemProperty("plugin.testDataPath", testDataPath)

        jvmArgs(
            "-javaagent:${bytebuddyAgent.get().singleFile}"
        )
    }

}
