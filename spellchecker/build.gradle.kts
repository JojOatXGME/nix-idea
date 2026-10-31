// Content module "nix-idea.spellchecker": Spellchecking support.
// Separated from nix-idea.core.shared so that nix-idea.core.shared does not depend on intellij.spellchecker.

plugins {
    id("local.plugin-module")
}

dependencies {
    implementation(project(":core:shared"))
    intellijPlatform {
        bundledModule("intellij.spellchecker")
    }
}
