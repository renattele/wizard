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
    val resourceLoader: TemplateResourceLoader = EmptyTemplateResourceLoader,
)

data class GenerationResult(
    val files: Map<String, String>,
    val generationReport: GenerationReportV1,
    val artifact: GeneratedArtifactV1? = null,
)

interface TemplateResourceLoader {
    fun readText(path: String): String
    fun readDirectory(path: String): Map<String, String>
    fun readBinaryDirectory(path: String): Map<String, ByteArray> = emptyMap()
}

object EmptyTemplateResourceLoader : TemplateResourceLoader {
    override fun readText(path: String): String = error("Template resource loader is not configured for '$path'")

    override fun readDirectory(path: String): Map<String, String> =
        error("Template resource loader is not configured for '$path'")
}
