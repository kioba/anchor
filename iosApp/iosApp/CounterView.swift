import SwiftUI
import shared

typealias CounterAnchor = AsyncAnchor<CounterEffect, CounterState>

struct CounterView: View {
  @StateObject var viewModel = ViewModel(factory: CounterAnchorKt.counterAnchor)
  var body: some View {
      CounterUi(
        state: $viewModel.state,
        signals: $viewModel.singal,
        anchor: $viewModel.anchor,
      )
        .enviromentAnchor(viewModel)
  }
}

struct CounterUi: View {
  @Binding var state: CounterState
  @Binding var signals: SwiftSignalProvider
  @Binding var anchor: AnchorChannel<CounterAnchor>
  
  @State var animatePlus: Bool = false
  @State var animateMinus: Bool = false
  
  var body: some View {
    NavigationView {
      VStack {
        Text(String(describing: state.count))
        HStack(spacing: 20) {
          Button {
            anchor{ dsl in try await dsl.increment() }
          } label: {
            Image(systemName: "plus")
              .resizable()
              .scaledToFit()
              .frame(width: 16, height: 16)
              .padding(4)
              .cornerRadius(8)
          }.scaleEffect(animatePlus ? 1.5 : 1.0)
            .animation(.easeInOut, value: animatePlus)
            
          Button {
            anchor{ dsl in try await dsl.decrement() }
          } label: {
            Image(systemName: "minus")
              .resizable()
              .scaledToFit()
              .frame(width: 16, height: 16)
              .padding(4)
              .cornerRadius(8)
          }.scaleEffect(animateMinus ? 1.5 : 1.0)
            .animation(.easeInOut, value: animateMinus)
        }.buttonStyle(.borderedProminent)
          .padding(20)
      }
      .onChange(of: signals) { value in
        switch (value.provide()) {
        case is CounterSignalIncrement:
          animatePlus = true
          DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            animatePlus = false
          }
          break
        case is CounterSignalDecrement:
          animateMinus.toggle()
          DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            animateMinus.toggle()
          }
          break
        default:
          break
        }
      }
      .toolbar {
        ToolbarItem(placement: .principal) {
          Text("Counter")
            .font(.headline)
        }
      }
    }
  }
}

struct ContentView_Previews: PreviewProvider {

  static var previews: some View {
    @State var previewState: CounterState = CounterState.init(count: 12)
    @State var previewSignal: SwiftSignalProvider = SwiftSignalProvider(signal: UnitSignal())
    @State var previewAnchor: AnchorChannel<CounterAnchor> = {_ in}
    
    CounterUi(state: $previewState, signals: $previewSignal, anchor: $previewAnchor)
  }
}
