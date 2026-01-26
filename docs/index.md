# Welcome to ⚓️ anchor

[![GitHub](https://img.shields.io/github/license/kioba/anchor?style=flat-square)](https://github.com/kioba/anchor/blob/master/LICENSE)
[![Follow @k10b4](https://img.shields.io/twitter/follow/k10b4?style=flat-square&link=https%3A%2F%2Ftwitter.com%2Fintent%2Ffollow%3Fscreen_name%3Dk10b4)](https://x.com/k10b4)

Anchor is a simple, lightweight, and extensible state management architecture built on Kotlin's Context receivers with Jetpack Compose integration for Kotlin Multiplatform projects.

!!! tip "Goal"
    **Focus on writing an amazing app and let Anchor handle the state and side effects!**

## Key Features

- **Built for Compose**: Native integration with Jetpack Compose.
- **Kotlin Multiplatform**: Supports Android and iOS.
- **Type-Safe**: Leverages Kotlin's type system for state, effects, signals, and events.
- **Lifecycle-Aware**: Automatically manages Anchor instances within ViewModels, retaining state across configuration changes.
- **Context Receivers**: Uses modern Kotlin features for a clean and expressive DSL.
- **Granular Recomposition**: Provides utilities to observe only the necessary parts of the state.

## Installation

Add the following to your `build.gradle.kts` file:

### 1. Add the GitHub Packages Repository

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/kioba/anchor")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
        }
    }
}
```

### 2. Add the Dependency

```kotlin
dependencies {
    implementation("dev.kioba:anchor:0.0.8")
}
```

## Quick Start (Counter Example)

Here's a simple counter example to get you started:

```kotlin
// 1. Define your State
data class CounterState(val count: Int = 0) : ViewState

// 2. Define your Anchor
typealias CounterAnchor = Anchor<EmptyEffect, CounterState>

fun RememberAnchorScope.counterAnchor(): CounterAnchor =
    create(initialState = ::CounterState, effectScope = { EmptyEffect })

// 3. Define Actions
fun CounterAnchor.increment() {
    reduce { copy(count = count + 1) }
}

// 4. Use in UI
@Composable
fun CounterScreen() {
    RememberAnchor(scope = { counterAnchor() }) {
        val count = collectState { it.count }
        Button(onClick = anchor(CounterAnchor::increment)) {
            Text("Count: $count")
        }
    }
}
```

## Next Steps

- Explore the [Core Concepts](concepts.md) to understand how Anchor works.
- Check the [API Reference](api.md) for detailed documentation.
- See more [Examples](examples.md) for real-world usage.
