// Root settings.gradle.kts for neuroassist-ai
// TODO[lote-6a]: render from templates/operational/infra/backend-settings.gradle.kts.j2

rootProject.name = "neuroassist-ai-backend"

// Service modules expected (from project-context.modules.registry).
// Uncomment each line after /be-architect-scaffold scaffolds the service:
include(":services:neuroassist-ai-api-gateway")  // D-528 scaffold 2026-07-27
include(":services:neuroassist-ai-service-registry")  // D-529 scaffold 2026-07-27
include(":services:neuroassist-ai-migrations")  // scaffold 2026-07-27
include(":services:gest-service")  // D-530 scaffold 2026-07-28
include(":services:evnt-service")  // D-531 scaffold 2026-07-28
include(":services:pac-service")  // D-532 scaffold 2026-07-29
// include(":services:acs-service")
// include(":services:hist-service")
// include(":services:dash-service")
include(":services:edge-service")  // D-533 scaffold 2026-07-29
