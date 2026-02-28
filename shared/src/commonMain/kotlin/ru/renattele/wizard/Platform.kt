package ru.renattele.wizard

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform