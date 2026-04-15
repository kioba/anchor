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
  @Binding var anchor: AnchorAction<shared.Anchor<MainEffect, MainViewState, KotlinNothing>>

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
        Button("local error") { anchor { a in try await a.triggerLocalError() } }
        .buttonStyle(.borderedProminent)
        .cornerRadius(10)
        Button("propagated error") { anchor { a in try await a.triggerPropagatedError() } }
        .buttonStyle(.borderedProminent)
        .cornerRadius(10)
      }
      .alert(
        "Error",
        isPresented: Binding(
          get: { state.errorDialog != nil },
          set: { if !$0 { anchor { a in a.dismissErrorDialog() } } }
        )
      ) {
        Button("OK") { anchor { a in a.dismissErrorDialog() } }
      } message: {
        Text(state.errorDialog ?? "")
      }
      .onChange(of: state.iterationCounter) { counter in
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
