package ru.renattele.wizard.engine.configuration.application

import ru.renattele.wizard.engine.configuration.domain.OptionParameterType
import ru.renattele.wizard.engine.configuration.domain.PatchSpec
import ru.renattele.wizard.engine.configuration.domain.ResolvedOption

data class GenerationCustomization(
    val sharedVariables: Map<String, String> = emptyMap(),
    val optionParameters: Map<String, Map<String, String>> = emptyMap(),
)

class ApplyOptionCustomizationsUseCase {
    operator fun invoke(
        resolvedOptions: List<ResolvedOption>,
        customization: GenerationCustomization,
    ): List<ResolvedOption> {
        val selectedOptionIds = resolvedOptions.map { it.id }.toSet()
        val selectedCapabilities = resolvedOptions
            .flatMap { option -> option.providedCapabilities }
            .toSet()
        customization.optionParameters.keys
            .filterNot(selectedOptionIds::contains)
            .forEach { optionId ->
                throw IllegalArgumentException("Option parameters were provided for unselected option '$optionId'")
            }

        return resolvedOptions.map { option ->
            val parameterValues = resolveParameterValues(
                option = option,
                sharedVariables = customization.sharedVariables,
                providedValues = customization.optionParameters[option.id].orEmpty(),
            )
            val renderVariables = buildRenderVariables(
                sharedVariables = customization.sharedVariables,
                option = option,
                parameterValues = parameterValues,
            )
            option.copy(
                patches = option.patches
                    .filter { patch -> patch.activation.matches(selectedOptionIds, selectedCapabilities) }
                    .map { patch -> patch.renderWith(renderVariables) },
            )
        }
    }

    private fun resolveParameterValues(
        option: ResolvedOption,
        sharedVariables: Map<String, String>,
        providedValues: Map<String, String>,
    ): Map<String, String> {
        val definitions = option.parameters.associateBy { it.id }
        providedValues.keys
            .filterNot(definitions::containsKey)
            .forEach { parameterId ->
                throw IllegalArgumentException("Option '${option.id}' does not define parameter '$parameterId'")
            }

        val resolved = linkedMapOf<String, String>()
        option.parameters.forEach { parameter ->
            val explicitValue = providedValues[parameter.id]?.trim()?.takeIf { it.isNotEmpty() }
            val variables = buildRenderVariables(sharedVariables, option, resolved)
            val defaultValue = parameter.defaultValue?.let { renderTemplate(it, variables) }
            val value = explicitValue ?: defaultValue.orEmpty()

            if (parameter.required && value.isBlank()) {
                throw IllegalArgumentException("Option '${option.id}' requires parameter '${parameter.id}'")
            }

            when (parameter.type) {
                OptionParameterType.BOOLEAN -> {
                    if (value.isNotBlank() && value !in setOf("true", "false")) {
                        throw IllegalArgumentException(
                            "Option '${option.id}' parameter '${parameter.id}' expects a boolean value",
                        )
                    }
                }

                OptionParameterType.ENUM -> {
                    val allowedValues = parameter.allowedValues.map { it.value }.toSet()
                    if (value.isNotBlank() && value !in allowedValues) {
                        throw IllegalArgumentException(
                            "Option '${option.id}' parameter '${parameter.id}' must be one of: ${allowedValues.joinToString()}",
                        )
                    }
                }

                OptionParameterType.STRING,
                OptionParameterType.MULTILINE,
                -> Unit
            }

            resolved[parameter.id] = value
        }

        return resolved
    }

    private fun buildRenderVariables(
        sharedVariables: Map<String, String>,
        option: ResolvedOption,
        parameterValues: Map<String, String>,
    ): Map<String, String> {
        val variables = linkedMapOf<String, String>()
        variables += sharedVariables.toSortedMap()
        variables["Option.Id"] = option.id
        variables["Option.DisplayName"] = option.displayName
        variables["Option.Version"] = option.version
        parameterValues.toSortedMap().forEach { (key, value) ->
            variables["Param.$key"] = value
        }
        return variables
    }

    private fun PatchSpec.renderWith(variables: Map<String, String>): PatchSpec =
        copy(
            targetPath = renderTemplate(targetPath, variables),
            content = content?.let { renderTemplate(it, variables) },
            find = find?.let { renderTemplate(it, variables) },
            replace = replace?.let { renderTemplate(it, variables) },
        )

    private fun ru.renattele.wizard.engine.configuration.domain.PatchActivation.matches(
        selectedOptionIds: Set<String>,
        selectedCapabilities: Set<String>,
    ): Boolean =
        requiresOptionIds.all(selectedOptionIds::contains) &&
            requiresCapabilities.all(selectedCapabilities::contains)

    private fun renderTemplate(template: String, variables: Map<String, String>): String {
        var rendered = template
        variables.forEach { (key, value) ->
            rendered = rendered.replace("\${$key}", value)
        }
        return rendered
    }
}
