import SwiftUI

@main
struct PhinCallApp: App {
    @StateObject private var sessionState: CallSessionState
    private let manager: AvayaInteractionManager

    init() {
        let interactionManager = AvayaInteractionManager()
        self.manager = interactionManager
        _sessionState = StateObject(wrappedValue: interactionManager.state)
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if sessionState.isCallActive {
                    InCallView(manager: manager, state: sessionState)
                } else {
                    PreCallView(manager: manager, state: sessionState)
                }
            }
        }
    }
}
