package dev.kioba.anchor

internal typealias AnchorChannel<A> = (suspend A.() -> Unit) -> Unit
