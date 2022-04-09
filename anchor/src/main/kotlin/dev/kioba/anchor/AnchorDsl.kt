package dev.kioba.anchor

@DslMarker
internal annotation class AnchorDsl

public interface AnchorSyntax

public interface AnchorDslSyntax<S> : AnchorSyntax {
  public val environment: AnchorEnvironment<S>
}

public interface AnchorCommand
public object UnitCommand : AnchorCommand
