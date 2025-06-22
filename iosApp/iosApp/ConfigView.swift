import SwiftUI
import shared

typealias ConfigAnchor = AsyncAnchor<ConfigEffect, ConfigState>

struct ConfigView: View {
  @StateObject var viewModel = ViewModel(factory: ConfigAnchorKt.configAnchor)
  var body: some View {
    ConfigUi(state: $viewModel.state, anchor: $viewModel.anchor)
      .enviromentAnchor(viewModel)
  }
}

struct ConfigUi: View {
  @Binding var state: ConfigState
  @Binding var anchor: AnchorChannel<ConfigAnchor>

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
            anchor { dsl in try await dsl.updateText(text: update) }
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
