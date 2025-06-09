import SwiftUI
import shared
import Combine

class ViewModel<E, S>: ObservableObject where E: Effect, S: ViewState {
    @Published var anchor: shared.AnchorSink<E, S>
    @Published var viewState: S

  init(factory: @escaping (any RememberAnchorScope) -> shared.Anchor<E, S>) {
    
      let anchor = RememberAnchorKt.rememberAnchor(
        scope: { scope in factory(scope) as! shared.Anchor<any Effect, any ViewState> }
        , customKey: nil
      ) as! shared.AnchorSink<E, S>
      self.anchor = anchor
      self.viewState = anchor.state as! S
    }
}

private struct StateBinding<E, S>: ViewModifier where E: Effect, S: ViewState {
  @Binding var state: S
  @StateObject var viewModel: ViewModel<E, S>

  func body(content: Content) -> some View {
    content.task {
      for await value in viewModel.anchor.viewState {
        self.state = value
      }
    }
  }
}

extension View {
  func rememberAnchor<E: Effect, S: ViewState>(
    _ state: Binding<S>,
    _ factory: @escaping (any RememberAnchorScope) -> shared.Anchor<E, S>
  ) -> some View {
    modifier(StateBinding(state: state, viewModel: ViewModel(factory: factory)))
  }
}
