// Content module "nix-idea.backend": Functionality which needs the project model, the file system or external processes.
// In split mode, the module is only loaded in the backend process (see nix-idea.backend.xml).

plugins {
    id("local.plugin-module")
}

dependencies {
    implementation(project(":core"))
}
