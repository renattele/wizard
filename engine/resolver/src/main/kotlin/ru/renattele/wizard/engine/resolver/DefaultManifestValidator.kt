package ru.renattele.wizard.engine.resolver

import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.engine.catalog.CatalogProvider
import ru.renattele.wizard.engine.configuration.application.ConfigurationModelFactory
import ru.renattele.wizard.engine.configuration.application.LoadCatalogUseCase
import ru.renattele.wizard.manifest.ManifestValidationCode
import ru.renattele.wizard.manifest.ManifestValidationIssue
import ru.renattele.wizard.manifest.ManifestValidationResult

class DefaultManifestValidator : ManifestValidator {
    override fun validate(catalog: CatalogBundle): ManifestValidationResult {
        val provider = object : CatalogProvider {
            override fun loadCatalog(request: ru.renattele.wizard.engine.catalog.CatalogRequest): CatalogBundle = catalog
        }
        val loaded = LoadCatalogUseCase(provider, ConfigurationModelFactory())()
        val issues = loaded.problems.map { problem ->
            ManifestValidationIssue(
                code = when (problem.code) {
                    ru.renattele.wizard.engine.configuration.domain.ProblemCode.UNKNOWN_REQUIRED_OPTION ->
                        ManifestValidationCode.UNKNOWN_REQUIRED_OPTION

                    ru.renattele.wizard.engine.configuration.domain.ProblemCode.UNKNOWN_CONFLICT_OPTION ->
                        ManifestValidationCode.UNKNOWN_CONFLICT_OPTION

                    ru.renattele.wizard.engine.configuration.domain.ProblemCode.INVALID_VERSION_RANGE ->
                        ManifestValidationCode.INVALID_VERSION_RANGE

                    else -> ManifestValidationCode.DUPLICATE_OPTION
                },
                message = problem.message,
            )
        }
        return ManifestValidationResult(valid = issues.isEmpty(), issues = issues)
    }
}
