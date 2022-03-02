package dev.kioba.anchor

@DslMarker
internal annotation class MviDsl

public interface MviScopeSyntax

public interface DslSyntax<S> : MviScopeSyntax {
  public val bridge: MviBridge<S>
}

public interface MviEffect
public object EmptyEffect : MviEffect
