import org.gradle.api.artifacts.ProjectDependency

plugins {
    base
}

/**
 * Architectural rule: which modules each module may depend on.
 *
 * Ports (interfaces) live in :core:oauth; every other module is an adapter that
 * implements a port or consumes the core. Nothing in :core may depend on :platform,
 * :feature or an app module, and :core:oauth stays free of adapter modules.
 */
val allowedProjectDependencies: Map<String, Set<String>> = mapOf(
    ":core:backend-contract" to emptySet(),
    ":core:oauth" to setOf(":core:backend-contract"),
    ":core:crypto" to setOf(":core:oauth"),
    ":core:storage" to setOf(":core:oauth", ":core:backend-contract"),
    ":core:network" to setOf(":core:oauth", ":core:backend-contract"),
    ":platform:browser" to setOf(":core:oauth"),
    ":feature:auth" to setOf(":core:oauth"),
    ":composeApp" to setOf(
        ":core:backend-contract",
        ":core:oauth",
        ":core:crypto",
        ":core:storage",
        ":core:network",
        ":platform:browser",
        ":feature:auth",
    ),
    ":androidApp" to setOf(":composeApp"),
)

val verifyModuleBoundaries by tasks.registering {
    group = "verification"
    description = "Fails if a module depends on a module it is not allowed to depend on."
    doLast {
        val violations = mutableListOf<String>()
        subprojects.forEach { module ->
            val allowed = allowedProjectDependencies[module.path] ?: return@forEach
            module.configurations
                .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                .map { it.path }
                .toSet()
                .filter { it != module.path && it !in allowed }
		.forEach { violations += "${module.path} must not depend on $it" }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Module boundary violations:\n" + violations.joinToString("\n") { " - $it" },
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyModuleBoundaries)
}
