plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // Used by :downloadReference
    implementation("it.skrape:skrapeit-dsl:1.1.5")
    implementation("it.skrape:skrapeit-html-parser:1.1.5")
    implementation("it.skrape:skrapeit-http-fetcher:1.1.5")
}
