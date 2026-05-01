package ru.renattele.wizard.engine.generator

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import ru.renattele.wizard.contracts.v1.ExportFormatV1
import ru.renattele.wizard.contracts.v1.GeneratedArtifactV1
import ru.renattele.wizard.contracts.v1.GenerationReportV1
import ru.renattele.wizard.contracts.v1.SkippedPatchV1
import ru.renattele.wizard.engine.configuration.domain.PatchConflictStrategy
import ru.renattele.wizard.engine.configuration.domain.PatchOperation

class DeterministicPatchPipeline : PatchPipeline {
    override fun generate(request: GenerationRequest): GenerationResult {
        val files = VirtualFileTree(request.files + request.plan.seedFiles)
        val appliedFiles = linkedSetOf<String>()
        val skipped = mutableListOf<SkippedPatchV1>()
        val optionById = request.plan.resolvedOptions.associateBy { it.id }

        request.plan.templatePatchBatches.forEach { batch ->
            applyPatches(
                sourceId = batch.sourceId,
                patches = batch.patches,
                resourceLoader = request.resourceLoader,
                files = files,
                appliedFiles = appliedFiles,
                skipped = skipped,
            )
        }

        request.plan.applyOrder.forEach { optionId ->
            val option = optionById[optionId] ?: return@forEach
            applyPatches(
                sourceId = optionId,
                patches = option.patches,
                resourceLoader = request.resourceLoader,
                files = files,
                appliedFiles = appliedFiles,
                skipped = skipped,
            )
        }

        request.plan.additionalPatchBatches.forEach { batch ->
            applyPatches(
                sourceId = batch.sourceId,
                patches = batch.patches,
                resourceLoader = request.resourceLoader,
                files = files,
                appliedFiles = appliedFiles,
                skipped = skipped,
            )
        }

        files.removeTemplateMarkers()

        val textFiles = files.snapshot()
        val binaryFiles = files.binarySnapshot()

        val report = GenerationReportV1(
            appliedOptionIds = request.plan.applyOrder,
            appliedFiles = appliedFiles.toList().sorted(),
            skippedPatches = skipped,
        )

        return GenerationResult(
            files = textFiles,
            generationReport = report,
            artifact = request.exportFormat?.let { format ->
                ArchiveBuilder().build(
                    templateId = request.plan.templateId,
                    format = format,
                    files = textFiles,
                    binaryFiles = binaryFiles,
                )
            },
        )
    }

    private fun applyPatches(
        sourceId: String,
        patches: List<ru.renattele.wizard.engine.configuration.domain.PatchSpec>,
        resourceLoader: TemplateResourceLoader,
        files: VirtualFileTree,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        patches.forEach { patch ->
            when (patch.operation) {
                PatchOperation.ADD_FILE -> files.addFile(
                    optionId = sourceId,
                    targetPath = patch.targetPath,
                    content = patch.content.orEmpty(),
                    strategy = patch.conflictStrategy,
                    appliedFiles = appliedFiles,
                    skipped = skipped,
                )

                PatchOperation.ADD_TEMPLATE_FILE -> files.addFile(
                    optionId = sourceId,
                    targetPath = patch.targetPath,
                    content = renderTemplate(resourceLoader.readText(requireNotNull(patch.resourcePath)), patch.templateVariables),
                    strategy = patch.conflictStrategy,
                    appliedFiles = appliedFiles,
                    skipped = skipped,
                )

                PatchOperation.ADD_TEMPLATE_DIRECTORY -> {
                    resourceLoader.readDirectory(requireNotNull(patch.resourcePath))
                        .toSortedMap()
                        .forEach { (relativePath, rawContent) ->
                            val renderedRelativePath = renderTemplate(relativePath, patch.templateVariables)
                            val normalizedPath = listOf(patch.targetPath.trimEnd('/'), renderedRelativePath.trimStart('/'))
                                .filter(String::isNotBlank)
                                .joinToString("/")
                            files.addFile(
                                optionId = sourceId,
                                targetPath = normalizedPath,
                                content = renderTemplate(rawContent, patch.templateVariables),
                                strategy = patch.conflictStrategy,
                                appliedFiles = appliedFiles,
                                skipped = skipped,
                            )
                        }
                    resourceLoader.readBinaryDirectory(requireNotNull(patch.resourcePath))
                        .toSortedMap()
                        .forEach { (relativePath, rawContent) ->
                            val renderedRelativePath = renderTemplate(relativePath, patch.templateVariables)
                            val normalizedPath = listOf(patch.targetPath.trimEnd('/'), renderedRelativePath.trimStart('/'))
                                .filter(String::isNotBlank)
                                .joinToString("/")
                            files.addBinaryFile(
                                optionId = sourceId,
                                targetPath = normalizedPath,
                                content = rawContent,
                                strategy = patch.conflictStrategy,
                                appliedFiles = appliedFiles,
                                skipped = skipped,
                            )
                        }
                }

                PatchOperation.APPEND_FILE -> files.appendFile(
                    optionId = sourceId,
                    targetPath = patch.targetPath,
                    content = patch.content.orEmpty(),
                    strategy = patch.conflictStrategy,
                    appliedFiles = appliedFiles,
                    skipped = skipped,
                )

                PatchOperation.REPLACE_IN_FILE -> files.replaceInFile(
                    optionId = sourceId,
                    targetPath = patch.targetPath,
                    find = patch.find.orEmpty(),
                    replace = patch.replace.orEmpty(),
                    strategy = patch.conflictStrategy,
                    appliedFiles = appliedFiles,
                    skipped = skipped,
                )

                PatchOperation.REMOVE_FILE -> files.removeFile(
                    optionId = sourceId,
                    targetPath = patch.targetPath,
                    strategy = patch.conflictStrategy,
                    appliedFiles = appliedFiles,
                    skipped = skipped,
                )
            }
        }
    }

    private fun renderTemplate(
        template: String,
        variables: Map<String, String>,
    ): String {
        var rendered = template
        variables.toSortedMap().forEach { (key, value) ->
            rendered = rendered.replace("\${$key}", value)
        }
        return rendered
    }
}

private class VirtualFileTree(initialFiles: Map<String, String>) {
    private val templateMarkerLine = Regex("""(?m)^.*__[A-Z0-9_]+__.*(?:\r?\n|$)""")
    private val files = initialFiles.toMutableMap()
    private val binaryFiles = linkedMapOf<String, ByteArray>()

    fun snapshot(): Map<String, String> = files.toSortedMap()
    fun binarySnapshot(): Map<String, ByteArray> = binaryFiles.toSortedMap()

    fun removeTemplateMarkers() {
        files.replaceAll { _, content ->
            content.replace(templateMarkerLine, "")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trimEnd() + "\n"
        }
    }

    fun addFile(
        optionId: String,
        targetPath: String,
        content: String,
        strategy: PatchConflictStrategy,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        val existing = files[targetPath]
        when {
            existing == null -> {
                files[targetPath] = content
                appliedFiles += targetPath
            }

            strategy == PatchConflictStrategy.SKIP -> skipped += SkippedPatchV1(optionId, targetPath, "file already exists")
            strategy == PatchConflictStrategy.FAIL -> throw IllegalStateException("Patch conflict on '$targetPath'")
            !existing.contains(content) -> {
                files[targetPath] = existing + content
                appliedFiles += targetPath
            }

            else -> Unit
        }
    }

    fun addBinaryFile(
        optionId: String,
        targetPath: String,
        content: ByteArray,
        strategy: PatchConflictStrategy,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        val existingBinary = binaryFiles[targetPath]
        val existingText = files[targetPath]
        when {
            existingText != null -> when (strategy) {
                PatchConflictStrategy.FAIL -> throw IllegalStateException("Patch conflict on '$targetPath'")
                PatchConflictStrategy.SKIP, PatchConflictStrategy.MERGE_WITH_RULE ->
                    skipped += SkippedPatchV1(optionId, targetPath, "text file already exists")
            }

            existingBinary == null -> {
                binaryFiles[targetPath] = content
                appliedFiles += targetPath
            }

            strategy == PatchConflictStrategy.FAIL -> throw IllegalStateException("Patch conflict on '$targetPath'")
            strategy == PatchConflictStrategy.SKIP ->
                skipped += SkippedPatchV1(optionId, targetPath, "binary file already exists")

            !existingBinary.contentEquals(content) -> {
                binaryFiles[targetPath] = content
                appliedFiles += targetPath
            }

            else -> Unit
        }
    }

    fun appendFile(
        optionId: String,
        targetPath: String,
        content: String,
        strategy: PatchConflictStrategy,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        val existing = files[targetPath]
        if (existing == null) {
            files[targetPath] = content
            appliedFiles += targetPath
            return
        }

        if (existing.contains(content)) return
        files[targetPath] = existing + content
        appliedFiles += targetPath
        if (strategy == PatchConflictStrategy.SKIP && content.isBlank()) {
            skipped += SkippedPatchV1(optionId, targetPath, "empty append content")
        }
    }

    fun replaceInFile(
        optionId: String,
        targetPath: String,
        find: String,
        replace: String,
        strategy: PatchConflictStrategy,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        val existing = files[targetPath]
        if (existing == null) {
            handleMissingTarget(optionId, targetPath, replace, strategy, appliedFiles, skipped)
            return
        }

        if (find.isBlank()) {
            mergeReplace(optionId, targetPath, existing, replace, strategy, appliedFiles, skipped)
            return
        }

        if (!existing.contains(find)) {
            mergeReplace(optionId, targetPath, existing, replace, strategy, appliedFiles, skipped)
            return
        }

        files[targetPath] = existing.replace(find, replace)
        appliedFiles += targetPath
    }

    fun removeFile(
        optionId: String,
        targetPath: String,
        strategy: PatchConflictStrategy,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        if (files.remove(targetPath) != null) {
            appliedFiles += targetPath
            return
        }

        when (strategy) {
            PatchConflictStrategy.FAIL -> throw IllegalStateException("Patch conflict on '$targetPath'")
            PatchConflictStrategy.SKIP, PatchConflictStrategy.MERGE_WITH_RULE ->
                skipped += SkippedPatchV1(optionId, targetPath, "file not found")
        }
    }

    private fun handleMissingTarget(
        optionId: String,
        targetPath: String,
        replace: String,
        strategy: PatchConflictStrategy,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        when (strategy) {
            PatchConflictStrategy.FAIL -> throw IllegalStateException("Patch conflict on '$targetPath'")
            PatchConflictStrategy.SKIP -> skipped += SkippedPatchV1(optionId, targetPath, "target file is missing")
            PatchConflictStrategy.MERGE_WITH_RULE -> {
                files[targetPath] = replace
                appliedFiles += targetPath
            }
        }
    }

    private fun mergeReplace(
        optionId: String,
        targetPath: String,
        existing: String,
        replace: String,
        strategy: PatchConflictStrategy,
        appliedFiles: MutableSet<String>,
        skipped: MutableList<SkippedPatchV1>,
    ) {
        when (strategy) {
            PatchConflictStrategy.FAIL -> throw IllegalStateException("Patch conflict on '$targetPath'")
            PatchConflictStrategy.SKIP -> skipped += SkippedPatchV1(optionId, targetPath, "pattern not found")
            PatchConflictStrategy.MERGE_WITH_RULE -> {
                if (!existing.contains(replace)) {
                    files[targetPath] = existing + replace
                    appliedFiles += targetPath
                }
            }
        }
    }
}

private class ArchiveBuilder {
    fun build(
        templateId: String,
        format: ExportFormatV1,
        files: Map<String, String>,
        binaryFiles: Map<String, ByteArray>,
    ): GeneratedArtifactV1 {
        val payload = when (format) {
            ExportFormatV1.ZIP -> buildZip(files, binaryFiles)
            ExportFormatV1.TAR_GZ -> buildTarGz(files, binaryFiles)
        }

        return GeneratedArtifactV1(
            fileName = "$templateId.${format.extension()}",
            mediaType = format.mediaType(),
            sizeBytes = payload.size.toLong(),
            archiveBase64 = Base64.getEncoder().encodeToString(payload),
        )
    }

    private fun buildZip(
        files: Map<String, String>,
        binaryFiles: Map<String, ByteArray>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.toSortedMap().forEach { (path, content) ->
                val entry = ZipEntry(path)
                zip.putNextEntry(entry)
                zip.write(content.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            binaryFiles.toSortedMap().forEach { (path, content) ->
                val entry = ZipEntry(path)
                zip.putNextEntry(entry)
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun buildTarGz(
        files: Map<String, String>,
        binaryFiles: Map<String, ByteArray>,
    ): ByteArray {
        val tarBytes = ByteArrayOutputStream()
        files.toSortedMap().forEach { (path, content) ->
            writeTarEntry(
                output = tarBytes,
                path = path,
                bytes = content.toByteArray(StandardCharsets.UTF_8),
            )
        }
        binaryFiles.toSortedMap().forEach { (path, content) ->
            writeTarEntry(
                output = tarBytes,
                path = path,
                bytes = content,
            )
        }
        tarBytes.write(ByteArray(1024))

        val gzipped = ByteArrayOutputStream()
        GZIPOutputStream(gzipped).use { gzip ->
            gzip.write(tarBytes.toByteArray())
        }
        return gzipped.toByteArray()
    }

    private fun writeTarEntry(output: ByteArrayOutputStream, path: String, bytes: ByteArray) {
        val header = ByteArray(512)
        writeString(header, 0, 100, path)
        writeOctal(header, 100, 8, 0b110100100)
        writeOctal(header, 108, 8, 0)
        writeOctal(header, 116, 8, 0)
        writeOctal(header, 124, 12, bytes.size.toLong())
        writeOctal(header, 136, 12, Instant.EPOCH.epochSecond)
        repeat(8) { index -> header[148 + index] = ' '.code.toByte() }
        header[156] = '0'.code.toByte()
        writeString(header, 257, 6, "ustar")
        writeString(header, 263, 2, "00")
        val checksum = header.sumOf { it.toUByte().toInt() }
        writeOctal(header, 148, 8, checksum.toLong())

        output.write(header)
        output.write(bytes)
        val remainder = bytes.size % 512
        if (remainder != 0) {
            output.write(ByteArray(512 - remainder))
        }
    }

    private fun writeString(buffer: ByteArray, offset: Int, length: Int, value: String) {
        value.toByteArray(StandardCharsets.US_ASCII)
            .take(length)
            .forEachIndexed { index, byte -> buffer[offset + index] = byte }
    }

    private fun writeOctal(buffer: ByteArray, offset: Int, length: Int, value: Long) {
        val raw = value.toString(8).padStart(length - 1, '0')
        writeString(buffer, offset, length - 1, raw)
        buffer[offset + length - 1] = 0
    }
}

private fun ExportFormatV1.extension(): String = when (this) {
    ExportFormatV1.ZIP -> "zip"
    ExportFormatV1.TAR_GZ -> "tar.gz"
}

private fun ExportFormatV1.mediaType(): String = when (this) {
    ExportFormatV1.ZIP -> "application/zip"
    ExportFormatV1.TAR_GZ -> "application/gzip"
}
