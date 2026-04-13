import SwiftUI
import shared

typealias AnchorAction<AnchorType> = (@escaping (AnchorType) async throws -> Void) -> Void

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
  let anchorInstance: shared.Anchor<E, S>
  var anchor: AnchorAction<shared.Anchor<E, S>>
  @Published var state: S
  @Published var signal: SwiftSignalProvider

  private var stateCollector: NativeCancellable?
  private var signalCollector: NativeCancellable?

  init(factory: @escaping (any RememberAnchorScope) -> shared.Anchor<E, S>) {
    let localAnchor = RememberAnchorKt.rememberAnchor(
      scope: { scope in factory(scope) as! shared.Anchor<any Effect, any ViewState> },
      customKey: nil
    ) as! shared.Anchor<E, S>

    self.anchorInstance = localAnchor
    self.anchor = { action in Task { try await action(localAnchor) } }
    self.state = localAnchor.state as! S
    self.signal = SwiftSignalProvider(signal: UnitSignal())

    let sink = localAnchor as! shared.AnchorSink<E, S>

    self.stateCollector = sink.nativeViewState().collect { [weak self] value in
      self?.state = value
    }

    self.signalCollector = sink.nativeSignals().collect { [weak self] value in
      self?.signal = SwiftSignalProvider(signal: value.provide())
    }
  }

  deinit {
    stateCollector?.cancel()
    signalCollector?.cancel()
  }
}

private struct EnvironmentBinding<E, S>: ViewModifier where E: Effect, S: ViewState {
  let viewModel: ViewModel<E, S>

  func body(content: Content) -> some View {
    content
      .environmentObject(viewModel)
  }
}

extension View {
  func environmentAnchor<E: Effect, S: ViewState>(
    _ viewModel: ViewModel<E, S>
  ) -> some View {
    modifier(EnvironmentBinding<E, S>(viewModel: viewModel))
  }
}
