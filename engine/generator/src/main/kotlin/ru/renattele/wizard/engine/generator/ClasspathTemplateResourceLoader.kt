package ru.renattele.wizard.engine.generator

import java.io.File
import java.net.JarURLConnection
import java.net.URI
import java.util.jar.JarFile

class ClasspathTemplateResourceLoader(
    private val classLoader: ClassLoader = ClasspathTemplateResourceLoader::class.java.classLoader,
) : TemplateResourceLoader {
    private val binaryExtensions = setOf("jar")

    override fun readText(path: String): String {
        val stream = classLoader.getResourceAsStream(path)
            ?: error("Template resource '$path' not found")
        return stream.use { it.readBytes().decodeToString() }
    }

    override fun readDirectory(path: String): Map<String, String> {
        val resource = classLoader.getResource(path)
            ?: error("Template resource directory '$path' not found")

        return when (resource.protocol) {
            "file" -> readFileDirectory(path, resource.toURI())
            "jar" -> readJarDirectory(path, resource.openConnection() as JarURLConnection)
            else -> error("Unsupported template resource protocol '${resource.protocol}' for '$path'")
        }
    }

    override fun readBinaryDirectory(path: String): Map<String, ByteArray> {
        val resource = classLoader.getResource(path)
            ?: error("Template resource directory '$path' not found")

        return when (resource.protocol) {
            "file" -> readBinaryFileDirectory(path, resource.toURI())
            "jar" -> readBinaryJarDirectory(path, resource.openConnection() as JarURLConnection)
            else -> error("Unsupported template resource protocol '${resource.protocol}' for '$path'")
        }
    }

    private fun readFileDirectory(
        rootPath: String,
        uri: URI,
    ): Map<String, String> {
        val root = File(uri)
        require(root.isDirectory) { "Template resource directory '$rootPath' is not a directory" }
        return root.walkTopDown()
            .filter(File::isFile)
            .filterNot { it.extension.lowercase() in binaryExtensions }
            .associate { file ->
                root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/') to file.readText()
            }
    }

    private fun readBinaryFileDirectory(
        rootPath: String,
        uri: URI,
    ): Map<String, ByteArray> {
        val root = File(uri)
        require(root.isDirectory) { "Template resource directory '$rootPath' is not a directory" }
        return root.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension.lowercase() in binaryExtensions }
            .associate { file ->
                root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/') to file.readBytes()
            }
    }

    private fun readJarDirectory(
        rootPath: String,
        connection: JarURLConnection,
    ): Map<String, String> {
        val jar = connection.jarFile
        return jar.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { entry -> entry.name.startsWith("$rootPath/") }
            .filterNot { entry -> entry.name.substringAfterLast('.', "").lowercase() in binaryExtensions }
            .associate { entry ->
                entry.name.removePrefix("$rootPath/") to jar.readText(entry.name)
            }
    }

    private fun readBinaryJarDirectory(
        rootPath: String,
        connection: JarURLConnection,
    ): Map<String, ByteArray> {
        val jar = connection.jarFile
        return jar.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { entry -> entry.name.startsWith("$rootPath/") }
            .filter { entry -> entry.name.substringAfterLast('.', "").lowercase() in binaryExtensions }
            .associate { entry ->
                entry.name.removePrefix("$rootPath/") to jar.readBytes(entry.name)
            }
    }

    private fun JarFile.readText(entryName: String): String =
        getInputStream(getJarEntry(entryName)).use { it.readBytes().decodeToString() }

    private fun JarFile.readBytes(entryName: String): ByteArray =
        getInputStream(getJarEntry(entryName)).use { it.readBytes() }
}
