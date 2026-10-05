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

final class ViewModel<E, S, Err>: ObservableObject where E: Effect, S: ViewState, Err: AnyObject {
  private let container: AnchorContainer<E, S>
  let anchorInstance: shared.Anchor<E, S, Err>
  var anchor: AnchorAction<shared.Anchor<E, S, Err>>
  @Published var state: S
  @Published var signal: SwiftSignalProvider

  init(factory: @escaping (any RememberAnchorScope) -> shared.Anchor<E, S, Err>) {
    let container = AnchorContainerKt.createAnchor(
      scope: { scope in factory(scope) as! shared.Anchor<any Effect, any ViewState, AnyObject> },
      customKey: nil
    ) as! AnchorContainer<E, S>
    let localAnchor = container.anchor as! shared.Anchor<E, S, Err>

    self.container = container
    self.anchorInstance = localAnchor
    self.anchor = { action in Task { try await action(localAnchor) } }
    self.state = container.state
    self.signal = SwiftSignalProvider(signal: UnitSignal())

    // Both collectors stop in container.clear(); capture self weakly so deinit can run.
    _ = container.collectState { [weak self] value in
      self?.state = value
    }

    _ = container.collectSignals { [weak self] value in
      self?.signal = SwiftSignalProvider(signal: value.provide())
    }
  }

  deinit {
    container.clear()
  }
}

private struct EnvironmentBinding<E, S, Err>: ViewModifier where E: Effect, S: ViewState, Err: AnyObject {
  let viewModel: ViewModel<E, S, Err>

  func body(content: Content) -> some View {
    content
      .environmentObject(viewModel)
  }
}

extension View {
  func environmentAnchor<E: Effect, S: ViewState, Err: AnyObject>(
    _ viewModel: ViewModel<E, S, Err>
  ) -> some View {
    modifier(EnvironmentBinding<E, S, Err>(viewModel: viewModel))
  }
}
