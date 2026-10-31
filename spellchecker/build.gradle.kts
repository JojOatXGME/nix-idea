// Content module "nix-idea.spellchecker": Spellchecking support.
// Separated from nix-idea.core so that nix-idea.core does not depend on intellij.spellchecker.

plugins {
    id("local.plugin-module")
}

dependencies {
    implementation(project(":core"))
    intellijPlatform {
        bundledModule("intellij.spellchecker")
    }
}
