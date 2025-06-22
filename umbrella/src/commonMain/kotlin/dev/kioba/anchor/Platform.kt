package dev.kioba.anchor

public interface Platform {
    public val name: String
}

public expect fun getPlatform(): Platform
