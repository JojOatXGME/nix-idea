// Content module "nix-idea.core.shared": Language basics, loaded in all IDEs and, in split mode, in both processes.

plugins {
    id("local.plugin-module")
    id("org.jetbrains.intellij.platform.grammarkit")
}

dependencies {
    intellijPlatform {
        // version of IntelliJ patched JFlex
        // -> https://github.com/JetBrains/intellij-deps-jflex
        jflex("1.10.17")
        // tag or short commit hash of Grammar-Kit to use
        // -> https://github.com/JetBrains/Grammar-Kit
        grammarKit("2023.3.4")
    }
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
}
