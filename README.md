# ⚓️ Anchor

[![GitHub](https://img.shields.io/github/license/kioba/anchor?style=flat-square)](LICENSE)
[![Website](https://img.shields.io/badge/website-up-green?style=flat-square&link=http%3A%2F%2Fkioba.github.io%2Fanchor%2F)](https://kioba.github.io/anchor/)
[![Follow @k10b4](https://img.shields.io/twitter/follow/k10b4?style=flat-square&link=https%3A%2F%2Ftwitter.com%2Fintent%2Ffollow%3Fscreen_name%3Dk10b4)](https://x.com/k10b4)

**Anchor** is a lightweight, type-safe state management architecture for Kotlin Multiplatform projects, specifically designed for Jetpack Compose. It leverages Kotlin's modern features like Context Receivers and SAM conversions to provide a clean, expressive, and powerful DSL.

Visit [kioba.github.io/anchor/](https://kioba.github.io/anchor/) for the full documentation!

---

## 🚀 Key Features

- **Built for Compose**: Native integration with Jetpack Compose on Android and Desktop.
- **Kotlin Multiplatform**: Shared logic across Android and iOS.
- **Type-Safe**: Compile-time safety for State, Effects, Signals, and Events.
- **Lifecycle-Aware**: Automatic state retention across configuration changes via ViewModel integration.
- **Granular Recomposition**: Efficiently observe only the state properties you need.
- **Cancellable Jobs**: Easily manage and debounce long-running asynchronous operations.

---

## 📦 Installation

Add the repository to your `build.gradle.kts`:

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

Add the dependency:

```kotlin
dependencies {
    implementation("dev.kioba:anchor:0.0.8")
}
```

---

## 🛠 Quick Start

Defining a simple Counter component with Anchor:

### 1. Define State and Anchor

```kotlin
data class CounterState(val count: Int = 0) : ViewState

typealias CounterAnchor = Anchor<EmptyEffect, CounterState>

fun RememberAnchorScope.counterAnchor(): CounterAnchor =
    create(initialState = ::CounterState, effectScope = { EmptyEffect })
```

### 2. Define Actions

```kotlin
fun CounterAnchor.increment() {
    reduce { copy(count = count + 1) }
}

fun CounterAnchor.decrement() {
    reduce { copy(count = count - 1) }
}
```

### 3. Integrate with Compose

```kotlin
@Composable
fun CounterUi() {
    RememberAnchor(scope = { counterAnchor() }) {
        val count = collectState { it.count }

        Column {
            Text("Count: $count")
            Row {
                Button(onClick = anchor(CounterAnchor::decrement)) { Text("-") }
                Button(onClick = anchor(CounterAnchor::increment)) { Text("+") }
            }
        }
    }
}
```

---

## 📖 Learn More

For comprehensive details, check out our documentation:

- [**Core Concepts**](https://kioba.github.io/anchor/concepts/): Learn about State, Effects, Signals, and the engine.
- [**API Reference**](https://kioba.github.io/anchor/api/): Detailed documentation for all library functions.
- [**Examples**](https://kioba.github.io/anchor/examples/): Real-world usage scenarios and advanced patterns.

---

## 🤝 Contributing

Contributions are welcome! If you'd like to help improve Anchor, feel free to:

- **Submit a Pull Request**: Found a bug or have a feature idea? We'd love to see your code!
- **Open an Issue**: Report bugs or suggest enhancements via the [GitHub Issues](https://github.com/kioba/anchor/issues) page.
- **Join the Discussion**: Share your thoughts and feedback to help shape the future of the library.

When contributing, please ensure your code follows the existing style and includes appropriate tests.

---

## 📄 License

    Copyright 2026 Karoly Somodi

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
