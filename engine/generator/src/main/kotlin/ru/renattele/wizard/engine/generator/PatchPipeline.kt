package ru.renattele.wizard.engine.generator

import ru.renattele.wizard.contracts.v1.PatchReportV1
import ru.renattele.wizard.contracts.v1.WizardLockfile
import ru.renattele.wizard.engine.catalog.CatalogBundle

interface PatchPipeline {
    fun generate(request: GenerationRequest): GenerationResult
}

data class GenerationRequest(
    val templateId: String,
    val orderedOptionIds: List<String>,
    val catalog: CatalogBundle,
    val files: Map<String, String> = emptyMap(),
    val strictMode: Boolean = true,
    val lockfile: WizardLockfile? = null,
)

data class GenerationResult(
    val files: Map<String, String>,
    val patchReport: PatchReportV1,
)
