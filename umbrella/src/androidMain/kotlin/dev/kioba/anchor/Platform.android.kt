package dev.kioba.anchor

public class AndroidPlatform : Platform {
  override val name: String = "Android"
}

public actual fun getPlatform(): Platform = AndroidPlatform()
