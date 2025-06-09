import SwiftUI
import shared

struct ContentView: View {
  
	var body: some View {
    TabView {
     HomeView()
        .tabItem {
          Label("Home", systemImage: "house")
        }
      
      CounterView()
        .tabItem {
          Label("Home", systemImage: "house")
        }

      HomeView()
        .tabItem {
          Label("Home", systemImage: "house")
        }
    }
  }

  
}

struct HomeView: View {
  @State var viewState: MainViewState = shared.mainViewState()
  
  var body: some View {
    
    Text("Hello, World!")
      .rememberAnchor($viewState, MainAnchorKt.mainAnchor)
  }
}

struct CounterView: View {
  @State var viewState: CounterViewState = shared.counterViewState()
  
  var body: some View {
    
    Text("Hello, World!")
      .rememberAnchor($viewState, CounterAnchorKt.counterAnchor)
  }
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
    ContentView()
	}
}
