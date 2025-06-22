import SwiftUI
import shared

struct ContentView: View {
  
	var body: some View {
    TabView {
      HomeView()
        .tabItem {
          Image(systemName: "house")
        }

      CounterView()
        .tabItem {
          Image(systemName: "plus")
        }

      ConfigView()
        .tabItem {
          Image(systemName: "lightbulb")
        }
    }
  }
}
