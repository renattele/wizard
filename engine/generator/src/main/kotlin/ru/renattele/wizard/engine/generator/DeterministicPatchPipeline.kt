package ru.renattele.wizard.engine.generator

import ru.renattele.wizard.contracts.v1.PatchReportV1
import ru.renattele.wizard.contracts.v1.SkippedPatchV1
import ru.renattele.wizard.manifest.ConflictStrategy
import ru.renattele.wizard.manifest.PatchOperationType

class DeterministicPatchPipeline : PatchPipeline {
    override fun generate(request: GenerationRequest): GenerationResult {
        if (request.strictMode && request.lockfile == null) {
            throw IllegalArgumentException("Lockfile is required in strict mode")
        }

        val optionMap = request.catalog.options.associateBy { it.id }
        val mutableFiles = request.files.toMutableMap()
        val appliedFiles = linkedSetOf<String>()
        val skipped = mutableListOf<SkippedPatchV1>()

        request.orderedOptionIds.forEach { optionId ->
            val option = optionMap[optionId] ?: return@forEach
            option.patches.forEach { patch ->
                when (patch.type) {
                    PatchOperationType.ADD_FILE -> {
                        if (mutableFiles.containsKey(patch.targetPath)) {
                            when (patch.conflictStrategy) {
                                ConflictStrategy.SKIP -> skipped += SkippedPatchV1(optionId, patch.targetPath, "file already exists")
                                ConflictStrategy.MERGE_WITH_RULE -> {
                                    val existing = mutableFiles.getValue(patch.targetPath)
                                    val content = patch.content.orEmpty()
                                    if (!existing.contains(content)) {
                                        mutableFiles[patch.targetPath] = existing + content
                                        appliedFiles += patch.targetPath
                                    }
                                }
                                ConflictStrategy.FAIL -> throw IllegalStateException("Patch conflict on '${patch.targetPath}'")
                            }
                        } else {
                            mutableFiles[patch.targetPath] = patch.content.orEmpty()
                            appliedFiles += patch.targetPath
                        }
                    }

                    PatchOperationType.REPLACE_IN_FILE -> {
                        val existing = mutableFiles[patch.targetPath]
                        if (existing == null) {
                            skipped += SkippedPatchV1(optionId, patch.targetPath, "target file is missing")
                            return@forEach
                        }
                        val find = patch.find.orEmpty()
                        val replace = patch.replace.orEmpty()
                        if (!existing.contains(find)) {
                            skipped += SkippedPatchV1(optionId, patch.targetPath, "pattern not found")
                            return@forEach
                        }
                        mutableFiles[patch.targetPath] = existing.replace(find, replace)
                        appliedFiles += patch.targetPath
                    }

                    PatchOperationType.APPEND_FILE -> {
                        val existing = mutableFiles[patch.targetPath].orEmpty()
                        val content = patch.content.orEmpty()
                        if (!existing.contains(content)) {
                            mutableFiles[patch.targetPath] = existing + content
                            appliedFiles += patch.targetPath
                        }
                    }

                    PatchOperationType.REMOVE_FILE -> {
                        if (mutableFiles.remove(patch.targetPath) != null) {
                            appliedFiles += patch.targetPath
                        } else {
                            skipped += SkippedPatchV1(optionId, patch.targetPath, "file not found")
                        }
                    }
                }
            }
        }

        return GenerationResult(
            files = mutableFiles,
            patchReport = PatchReportV1(
                appliedOptionIds = request.orderedOptionIds,
                appliedFiles = appliedFiles.toList().sorted(),
                skippedPatches = skipped,
            ),
        )
    }
}
