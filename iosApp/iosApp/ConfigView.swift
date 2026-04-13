import SwiftUI
import shared

struct ConfigView: View {
  @StateObject var viewModel = ViewModel(factory: ConfigAnchorKt.configAnchor)
  var body: some View {
    ConfigUi(state: $viewModel.state, anchor: $viewModel.anchor)
      .environmentAnchor(viewModel)
  }
}

struct ConfigUi: View {
  @Binding var state: ConfigState
  @Binding var anchor: AnchorAction<shared.Anchor<ConfigEffect, ConfigState>>

  @State var text: String = ""
  var body: some View {
    NavigationView {

      VStack(spacing: 16) {
        TextField("", text: $text)
          .padding()
          .font(.title)
          .foregroundColor(.gray)
          .border(.secondary, width: 1.0)
          .padding()
          .onChange(of: text) { update in
            anchor { a in try await a.updateText(text: update) }
          }
        if(state.text != nil) {
          Text(state.text!)
        }
      }.toolbar {
        ToolbarItem(placement: .principal) {
          Text("config")
            .font(.headline)
        }
      }
    }
  }
}
