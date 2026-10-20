import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/** Reads a version from gradle/libs.versions.toml. */
internal fun Project.catalogVersion(alias: String): String =
    extensions.getByType(VersionCatalogsExtension::class.java)
        .named("libs")
        .findVersion(alias)
        .orElseThrow { IllegalStateException("Version '$alias' not found in libs.versions.toml") }
        .requiredVersion

/**
 * Derives an Android namespace from the base package and the module path,
 * e.g. ":core:backend-contract" -> "com.baloomba.oauth.core.backendcontract".
 */
internal fun Project.androidNamespace(): String {
    val base = providers.gradleProperty("oauth.basePackage").get()
    val suffix = path.removePrefix(":").replace(":", ".").replace("-", "")
    return "$base.$suffix"
}

internal fun Project.warningsAsErrors(): Boolean =
    providers.gradleProperty("oauth.warningsAsErrors").map(String::toBoolean).orElse(false).get()
