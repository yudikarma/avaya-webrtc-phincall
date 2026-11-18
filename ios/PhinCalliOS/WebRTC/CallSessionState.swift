import Foundation

enum CallQualityLevel: String {
    case unknown = "Measuring"
    case excellent = "Excellent"
    case good = "Good"
    case fair = "Fair"
    case poor = "Poor"
    case bad = "Bad"
}

enum CallLifecycleState: String {
    case idle = "IDLE"
    case initiating = "INITIATING"
    case remoteAlerting = "REMOTE_ALERTING"
    case established = "ESTABLISHED"
    case held = "HELD"
    case heldRemotely = "HELD_REMOTELY"
    case ending = "ENDING"
    case ended = "ENDED"
    case failed = "FAILED"
}

/// Observable call state consumed by SwiftUI PreCallView and InCallView.
@MainActor
final class CallSessionState: ObservableObject {
    @Published var isCallActive: Bool = false
    @Published var isPreCallLocked: Bool = false
    @Published var isFetchingToken: Bool = false
    @Published var mediaChoice: CallMediaChoice = .video
    @Published var platformChoice: AvayaPlatformChoice = .oceana
    @Published var interactionState: CallLifecycleState = .idle
    @Published var callQuality: CallQualityLevel = .unknown
    @Published var elapsedSeconds: Int = 0
    @Published var isAudioMuted: Bool = false
    @Published var isVideoMuted: Bool = false
    @Published var isVideoEnabled: Bool = true
    @Published var isHeldLocally: Bool = false
    @Published var isHeldRemotely: Bool = false
    @Published var selectedCamera: CameraSelectionChoice = .front
    @Published var resolutionPreference: VideoResolutionChoice = .p540
    @Published var orientationPreference: VideoOrientationChoice = .landscapeOrPortrait
    @Published var signallingBanner: String? = nil
    @Published var statusError: String? = nil
    @Published var sdkVersion: String = "4.0.10"

    var formattedDuration: String {
        let mins = elapsedSeconds / 60
        let secs = elapsedSeconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }
}
