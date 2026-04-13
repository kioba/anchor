import SwiftUI
import shared

struct HomeView: View {
  @StateObject var viewModel = ViewModel(factory: MainAnchorKt.mainAnchor)
  var body: some View {
    HomeUi(state: $viewModel.state, anchor: $viewModel.anchor)
      .environmentAnchor(viewModel)
  }
}

struct HomeUi: View {
  @Binding var state: MainViewState
  @Binding var anchor: AnchorAction<shared.Anchor<MainEffect, MainViewState>>

  @State var counter: String? = nil

  var body: some View {
    NavigationView {

      VStack(spacing: 16) {
        Text(state.details)
        if(counter != nil) {
          Text(counter!)
            .contentTransition(.numericText())
        }
        Button("refresh") { anchor { a in try await a.refresh() } }
        .buttonStyle(.borderedProminent)
        .cornerRadius(10)
        Button("cancel") { anchor { a in try await a.clear() } }
        .buttonStyle(.borderedProminent)
        .cornerRadius(10)
      }.onChange(of: state.iterationCounter) { counter in
        withAnimation {
          self.counter = counter
        }
      }.toolbar {
        ToolbarItem(placement: .principal) {
          Text(state.title)
            .font(.headline)
        }
      }
    }
  }
}
