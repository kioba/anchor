# ⚓️ anchor

[![GitHub](https://img.shields.io/github/license/kioba/anchor?style=flat-square)](LICENSE)
[![Website](https://img.shields.io/badge/website-up-green?style=flat-square&link=http%3A%2F%2Fkioba.github.io%2Fanchor%2F)](https://kioba.github.io/anchor/)
[![Follow @k10b4](https://img.shields.io/twitter/follow/k10b4?style=flat-square&link=https%3A%2F%2Ftwitter.com%2Fintent%2Ffollow%3Fscreen_name%3Dk10b4)](https://x.com/k10b4)

Anchor is a simple and extensible state management architecture built on Kotlin's Context receivers
with Jetpack Compose integration.

Visit [kioba.github.io/anchor/](https://kioba.github.io/anchor/) for more!

Counter Example
=======

A counter example to showcase the usage of Anchor architecture. The screen displays a count, and the
ability to increment and decrement the count.

![counter example](https://github.com/kioba/anchor/blob/master/docs/images/counter_example.png)

### 1. Define your State and Anchor

```kotlin
// Define your state
data class CounterState(val count: Int = 0) : ViewState

// Type alias for easy reference
typealias CounterAnchor = Anchor<EmptyEffect, CounterState>

// Factory function to create the anchor
fun RememberAnchorScope.counterAnchor(): CounterAnchor =
  create(
    initialState = ::CounterState,
    effectScope = { EmptyEffect }
  )
```

### 2. Define your Actions

```kotlin
// Use context receivers or simple extension functions
fun CounterAnchor.increment() {
  reduce { copy(count = count + 1) }
}

fun CounterAnchor.decrement() {
  reduce { copy(count = count - 1) }
}
```

### 3. Build your UI

```kotlin
@Composable
fun CounterUi() {
  // RememberAnchor handles ViewModel-scoped state retention
  RememberAnchor(RememberAnchorScope::counterAnchor) {
    // Access state with automatic recomposition optimization
    val count by collectState { it.count }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.headlineMedium,
      )
      Row {
        Button(onClick = anchor(CounterAnchor::decrement)) {
          Text("-")
        }
        Button(onClick = anchor(CounterAnchor::increment)) {
          Text("+")
        }
      }
    }
  }
}
```

Performance Best Practices
--------------------------

Anchor is designed for performance. To prevent unnecessary full-tree recompositions:

1.  **Use `collectState { ... }`**: Instead of using the `state` parameter directly, use `collectState` to observe only the specific fields your composable needs.
2.  **Granular UI**: Break down your UI into smaller composables and pass only the required data or use `collectState` within them.
3.  **One-shot Signals**: Use `HandleSignal<T> { ... }` for events like navigation or showing snackbars. Signals are delivered exactly once and do not trigger recomposition.

License
--------

    Copyright 2025 Karoly Somodi

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
