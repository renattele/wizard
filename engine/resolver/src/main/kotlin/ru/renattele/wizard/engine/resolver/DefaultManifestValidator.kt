package ru.renattele.wizard.engine.resolver

import ru.renattele.wizard.engine.catalog.CatalogBundle
import ru.renattele.wizard.manifest.ManifestValidationCode
import ru.renattele.wizard.manifest.ManifestValidationIssue
import ru.renattele.wizard.manifest.ManifestValidationResult

class DefaultManifestValidator : ManifestValidator {
    override fun validate(catalog: CatalogBundle): ManifestValidationResult {
        val issues = mutableListOf<ManifestValidationIssue>()

        val optionIds = mutableSetOf<String>()
        catalog.options.forEach { option ->
            if (!optionIds.add(option.id)) {
                issues += ManifestValidationIssue(
                    code = ManifestValidationCode.DUPLICATE_OPTION,
                    message = "Duplicate option id '${option.id}'",
                )
            }

            option.dependency.requiresOptionIds
                .filterNot { required -> catalog.options.any { it.id == required } }
                .forEach { missing ->
                    issues += ManifestValidationIssue(
                        code = ManifestValidationCode.UNKNOWN_REQUIRED_OPTION,
                        message = "Option '${option.id}' requires unknown option '$missing'",
                    )
                }

            option.dependency.conflictsHard
                .filterNot { conflict -> catalog.options.any { it.id == conflict } }
                .forEach { missing ->
                    issues += ManifestValidationIssue(
                        code = ManifestValidationCode.UNKNOWN_CONFLICT_OPTION,
                        message = "Option '${option.id}' conflicts with unknown option '$missing'",
                    )
                }

            val range = option.version.range
            if (range != null && !VersionRangeMatcher.looksLikeSupportedRange(range)) {
                issues += ManifestValidationIssue(
                    code = ManifestValidationCode.INVALID_VERSION_RANGE,
                    message = "Option '${option.id}' has unsupported range syntax '$range'",
                )
            }
        }

        val templateIds = mutableSetOf<String>()
        catalog.templates.forEach { template ->
            if (!templateIds.add(template.id)) {
                issues += ManifestValidationIssue(
                    code = ManifestValidationCode.DUPLICATE_TEMPLATE,
                    message = "Duplicate template id '${template.id}'",
                )
            }
        }

        return ManifestValidationResult(valid = issues.isEmpty(), issues = issues)
    }
}
