# ⚓️ anchor

[![GitHub](https://img.shields.io/github/license/kioba/anchor?style=flat-square)](LICENSE)
[![Website](https://img.shields.io/badge/website-up-green?style=flat-square&link=http%3A%2F%2Fkioba.github.io%2Fanchor%2F)](https://kioba.github.io/anchor/)
[![Follow @k10b4](https://img.shields.io/twitter/follow/k10b4?style=flat-square&link=https%3A%2F%2Ftwitter.com%2Fintent%2Ffollow%3Fscreen_name%3Dk10b4)](https://x.com/k10b4)

Anchor is a simple and extensible state management architecture built on Kotlin's Context receivers
with Jetpack Compose integration.

Visit [kioba.github.io/anchor/](https://kioba.github.io/anchor/) for more!

Counter
=======

A counter example to showcase the usage of Anchor architecture. The screen displays a count, and the
ability to increment and decrement the count.

![counter example](https://github.com/kioba/anchor/blob/master/docs/images/counter_example.png)

```kotlin
// type alias to easily reference our Scope without repeating the type arguments
typealias CounterScope = AnchorScope<CounterState, Unit>

// function to generate the Scope with the initial state
fun counterScope(): CounterScope =
  anchorScope(initialState = ::CounterState)

// Provide the AnchorScope abilities with a receiver 
context(CounterScope)
fun increment() {
  // modify the view state by incrementing the value
  reduce { copy(count = count.inc()) }
}

context(CounterScope)
fun decrement() {
  reduce { copy(count = count.dec()) }
}
```

```kotlin
@Composable
fun CounterUi() {
  // Scope computations are remembered and retained across configuration changes
  RememberAnchor(scope = ::counterScope) { state ->
    Scaffold { paddingValues ->
      Box(
        modifier = Modifier
          .padding(paddingValues)
          .fillMaxSize()
      ) {
        Column(modifier = Modifier.align(Center)) {
          Text(
            modifier = Modifier.Companion.align(CenterHorizontally),
            text = state.count.toString(),
            style = MaterialTheme.typography.headlineMedium,
          )
          Spacer(modifier = Modifier.size(32.dp))
          Row {
            Button(
              // within a RememberAnchor actions can be executed
              // without the requirement to pass around the scope
              onClick = anchor(::decrement)
            ) { DecrementIcon() }
            Spacer(modifier = Modifier.size(16.dp))
            Button(
              onClick = anchor(::increment),
            ) { IncrementIcon() }
          }
        }
      }
    }
  }
}
```

License
--------

    Copyright 2023 Karoly Somodi

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
