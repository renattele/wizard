package ru.renattele.wizard.engine.resolver

import ru.renattele.wizard.contracts.v1.ResolveRequestV1
import ru.renattele.wizard.contracts.v1.ResolveResponseV1
import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.configuration.ENGINE_API_RANGE
import ru.renattele.wizard.engine.configuration.ENGINE_GRADLE_RANGE
import ru.renattele.wizard.engine.configuration.ENGINE_KOTLIN_RANGE
import ru.renattele.wizard.engine.configuration.ENGINE_VERSION
import ru.renattele.wizard.manifest.ManifestValidationResult

interface ManifestValidator {
    fun validate(catalog: CatalogBundle): ManifestValidationResult
}

interface ResolutionEngine {
    fun resolve(request: ResolveRequestV1, catalog: CatalogBundle): ResolveResponseV1
}
