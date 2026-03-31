package ru.renattele.wizard.engine.generator

import ru.renattele.wizard.contracts.v1.ExportFormatV1
import ru.renattele.wizard.contracts.v1.GeneratedArtifactV1
import ru.renattele.wizard.contracts.v1.GenerationReportV1
import ru.renattele.wizard.engine.configuration.domain.GenerationPlan

interface PatchPipeline {
    fun generate(request: GenerationRequest): GenerationResult
}

data class GenerationRequest(
    val plan: GenerationPlan,
    val files: Map<String, String> = emptyMap(),
    val exportFormat: ExportFormatV1? = null,
)

data class GenerationResult(
    val files: Map<String, String>,
    val generationReport: GenerationReportV1,
    val artifact: GeneratedArtifactV1? = null,
)
