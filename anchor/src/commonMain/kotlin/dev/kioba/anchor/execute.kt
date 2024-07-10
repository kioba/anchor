package dev.kioba.anchor

import kotlinx.coroutines.launch

public inline fun <reified S, reified E> anchorScope(
    noinline initialState: () -> S,
    noinline effectScope: () -> E,
    noinline init: (suspend AnchorScope<S, E>.() -> Unit)? = null,
    noinline subscriptions: SubscriptionsScope<S, E>.() -> Unit = {},
): AnchorScope<S, E> =
    AnchorScope(
        initialState = initialState,
        effectScope = effectScope,
        init = init?.let { Anchor<AnchorScope<S, E>> { scope -> launch { it(scope) } } },
        subscriptions = subscriptions,
    )

public inline fun <reified S> anchorScope(
    noinline initialState: () -> S,
    noinline init: (suspend AnchorScope<S, Unit>.() -> Unit)? = null,
    noinline subscriptions: SubscriptionsScope<S, Unit>.() -> Unit = {},
): AnchorScope<S, Unit> =
    anchorScope(
        initialState = initialState,
        effectScope = {},
        init = init,
        subscriptions = subscriptions,
    )
