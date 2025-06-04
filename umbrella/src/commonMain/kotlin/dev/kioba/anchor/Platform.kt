package dev.kioba.anchor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform