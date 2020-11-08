import java.util.regex.Pattern;
import java.util.Properties;

val GRADLE_PROPERTIES = "gradle.properties"
val VERSION_PROPERTY = "pluginVersion"

task("bumpVersion") {
    description = "Bumps version and updates some documents"
    dependsOn("patchChangelog")
    outputs.upToDateWhen { false }

    doLast {
        val prevVersion = project.property(VERSION_PROPERTY) as String
        val nextVersion = incrementVersion(prevVersion)
        replaceInProperties(prevVersion, nextVersion)
    }
}

fun replaceInProperties(prevVersion: String, nextVersion: String): Unit {
    val pProperty = Regex.escape(VERSION_PROPERTY)
    val pVersion = Regex.escape(prevVersion)
    val rVersion = Regex.escapeReplacement(nextVersion)

    val file = file(GRADLE_PROPERTIES)
    val previousContent = file.readText()
    file.writeText(previousContent.replace(
            Regex("^(\\s*$pProperty\\s*=\\s*)$pVersion(\\s*)$", RegexOption.MULTILINE),
            "$1${rVersion}$2"))
}

fun incrementVersion(previous: String): String {
    val matcher = Pattern.compile("(.*[^\\d])(\\d+)([^\\d]*)").matcher(previous)
    if (matcher.matches()) {
        val incrementedNumber = Integer.parseInt(matcher.group(2)) + 1
        return matcher.group(1) + incrementedNumber + matcher.group(3)
    }
    else {
        throw GradleException("Unsupported version: " + previous)
    }
}
