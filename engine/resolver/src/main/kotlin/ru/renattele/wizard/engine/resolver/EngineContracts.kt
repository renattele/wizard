package ru.renattele.wizard.engine.resolver

import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.manifest.ManifestValidationResult

const val ENGINE_VERSION: String = "1.0.0"
const val ENGINE_API_RANGE: String = "2.x"
const val ENGINE_KOTLIN_RANGE: String = "2.3.x"
const val ENGINE_GRADLE_RANGE: String = "8.x"

interface ManifestValidator {
    fun validate(catalog: CatalogBundle): ManifestValidationResult
}

interface ResolutionEngine {
    fun resolve(request: ResolveRequestV1, catalog: CatalogBundle): ResolveResponseV1
}
