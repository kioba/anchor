import SwiftUI
import shared

typealias AsyncAnchor = shared.Skie.Anchor.Anchor.__Suspend
typealias AnchorChannel<AnchorType> = (@escaping (AnchorType) async throws -> Void) -> Void

extension SwiftSignalProvider: Equatable {
  static func == (lhs: SwiftSignalProvider, rhs: SwiftSignalProvider) -> Bool {
    return lhs === rhs
  }
}

class SwiftSignalProvider: SignalProvider {
  let signal: Signal
  
  init(signal: Signal) {
    self.signal = signal
  }
  
  func provide() -> any Signal {
    signal
  }
}

final class ViewModel<E, S>: ObservableObject where E: Effect, S: ViewState {
  private var sink: shared.AnchorSink<E, S>
  var anchor: AnchorChannel<AsyncAnchor<E, S>>
  @Published var state: S
  @Published var singal: SwiftSignalProvider

  
  typealias E = E
  typealias S = S
  
  init(factory: @escaping (any RememberAnchorScope) -> shared.Anchor<E, S>) {

    let localAnchor = RememberAnchorKt.rememberAnchor(
      scope: { scope in factory(scope) as! shared.Anchor<any Effect, any ViewState> },
      customKey: nil,
    )  as! shared.Anchor<E, S>
    
    self.sink = localAnchor  as! shared.AnchorSink<E, S>
    self.anchor = { fun in Task { try await fun(skie(localAnchor)) } }
    self.state = localAnchor.state as! S
    self.singal = SwiftSignalProvider(signal: UnitSignal())
  }
  
  @MainActor
  func collectState() async {
    for await value in sink.viewState {
      state = value
    }
  }
  
  @MainActor
  func collectSignals() async {
    for await value in sink.signals {
      self.singal = SwiftSignalProvider(signal: value.provide())
    }
  }
}


private struct EnviromentBinding<E, S>: ViewModifier where E: Effect, S: ViewState {
  let viewModel: ViewModel<E, S>

  func body(content: Content) -> some View {
    content
      .task { await viewModel.collectState() }
      .task { await viewModel.collectSignals() }
      .environmentObject(viewModel)
  }
}

extension View {
  func enviromentAnchor<E: Effect, S: ViewState>(
    _ viewModel: ViewModel<E, S>
  ) -> some View {
    modifier(EnviromentBinding<E, S>(viewModel: viewModel))
  }
}
