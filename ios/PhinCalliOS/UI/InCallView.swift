import AVKit
import SwiftUI

struct UIKitVideoHostView: UIViewRepresentable {
    let containerView: UIView

    func makeUIView(context: Context) -> UIView { containerView }
    func updateUIView(_ uiView: UIView, context: Context) {}
}

/// Native iOS audio route selector recommended by Avaya WebRTC Connect Guide 4.0.10 (p. 70).
struct AudioRoutePickerRepresentable: UIViewRepresentable {
    func makeUIView(context: Context) -> AVRoutePickerView {
        let picker = AVRoutePickerView()
        picker.activeTintColor = UIColor(red: 0.784, green: 0.063, blue: 0.180, alpha: 1.0)
        picker.tintColor = .white
        return picker
    }
    func updateUIView(_ uiView: AVRoutePickerView, context: Context) {}
}

struct InCallView: View {
    let manager: AvayaInteractionManager
    @ObservedObject var state: CallSessionState

    @State private var showDtmfSheet = false
    @State private var showResolutionSheet = false

    private let stageDark = Color(red: 0.059, green: 0.078, blue: 0.106)
    private let dockDark = Color(red: 0.102, green: 0.133, blue: 0.176)
    private let buttonIdle = Color(red: 0.153, green: 0.200, blue: 0.267)
    private let crimson = Color(red: 0.784, green: 0.063, blue: 0.180)
    private let goodTint = Color(red: 0.290, green: 0.871, blue: 0.502)
    private let fairTint = Color(red: 0.984, green: 0.749, blue: 0.141)
    private let poorTint = Color(red: 0.973, green: 0.443, blue: 0.443)

    var body: some View {
        VStack(spacing: 0) {
            statusHeader
            mediaStage
            controlDock
        }
        .background(stageDark.ignoresSafeArea())
        .sheet(isPresented: $showDtmfSheet) {
            DtmfKeypadView { digit in
                manager.sendDTMF(digit)
            }
        }
        .confirmationDialog("Video Capture Resolution", isPresented: $showResolutionSheet) {
            ForEach(VideoResolutionChoice.allCases) { res in
                Button(res.rawValue) {
                    manager.updateVideoCapture(resolution: res, orientation: state.orientationPreference)
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var statusHeader: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("\(state.platformChoice.rawValue) / \(state.mediaChoice.rawValue)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.white)
                Spacer()
                Text(state.formattedDuration)
                    .font(.subheadline.monospacedDigit().weight(.bold))
                    .foregroundColor(.white)
            }

            HStack {
                Text(state.interactionState.rawValue)
                    .font(.caption)
                    .foregroundColor(Color.white.opacity(0.75))
                Spacer()
                HStack(spacing: 5) {
                    Image(systemName: "chart.bar.fill")
                        .font(.caption)
                        .accessibilityHidden(true)
                    Text("Quality: \(state.callQuality.rawValue)")
                        .font(.caption.weight(.semibold))
                }
                .foregroundColor(qualityColor(state.callQuality))
            }

            if let banner = state.signallingBanner {
                Text(banner)
                    .font(.caption)
                    .foregroundColor(fairTint)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(dockDark)
    }

    private var mediaStage: some View {
        ZStack(alignment: .bottomTrailing) {
            if state.mediaChoice == .video && state.isVideoEnabled {
                UIKitVideoHostView(containerView: manager.remoteVideoContainerView)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                UIKitVideoHostView(containerView: manager.localVideoContainerView)
                    .frame(width: 120, height: 164)
                    .padding(16)
            } else {
                VStack(spacing: 12) {
                    Image(systemName: "waveform")
                        .font(.system(size: 36, weight: .semibold))
                        .foregroundColor(.white)
                        .accessibilityHidden(true)
                    Text(state.mediaChoice == .video ? "Video disabled (audio active)" : "Active Voice Interaction")
                        .font(.headline)
                        .foregroundColor(.white)
                    Text("State: \(state.interactionState.rawValue)")
                        .font(.subheadline)
                        .foregroundColor(Color.white.opacity(0.75))
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(stageDark)
    }

    private var controlDock: some View {
        VStack(spacing: 12) {
            if state.mediaChoice == .video {
                HStack(spacing: 14) {
                    controlButton(
                        icon: state.isVideoMuted ? "video.slash.fill" : "video.fill",
                        label: state.isVideoMuted ? "Unmute video" : "Mute video",
                        tint: state.isVideoMuted ? crimson : buttonIdle,
                        disabled: !state.isVideoEnabled
                    ) {
                        manager.toggleVideoMute()
                    }

                    controlButton(
                        icon: state.isVideoEnabled ? "rectangle.inset.filled" : "rectangle.slash",
                        label: state.isVideoEnabled ? "Disable video" : "Enable video",
                        tint: state.isVideoEnabled ? buttonIdle : fairTint.opacity(0.35)
                    ) {
                        manager.toggleVideoEnabled()
                    }

                    controlButton(
                        icon: "arrow.triangle.2.circlepath.camera.fill",
                        label: "Switch camera",
                        tint: buttonIdle,
                        disabled: !state.isVideoEnabled
                    ) {
                        manager.switchCamera()
                    }

                    controlButton(
                        icon: "slider.horizontal.3",
                        label: "Capture resolution",
                        tint: buttonIdle,
                        disabled: !state.isVideoEnabled
                    ) {
                        showResolutionSheet = true
                    }
                }
            }

            HStack(spacing: 12) {
                controlButton(
                    icon: state.isAudioMuted ? "mic.slash.fill" : "mic.fill",
                    label: state.isAudioMuted ? "Unmute audio" : "Mute audio",
                    tint: state.isAudioMuted ? crimson : buttonIdle
                ) {
                    manager.toggleAudioMute()
                }

                AudioRoutePickerRepresentable()
                    .frame(width: 50, height: 50)
                    .background(buttonIdle)
                    .cornerRadius(8)
                    .accessibilityLabel("Select audio output route")

                controlButton(
                    icon: "pause.fill",
                    label: state.isHeldLocally ? "Resume call" : "Hold call",
                    tint: state.isHeldLocally ? fairTint.opacity(0.35) : buttonIdle,
                    disabled: state.isHeldRemotely
                ) {
                    manager.toggleHold()
                }

                controlButton(
                    icon: "circle.grid.3x3.fill",
                    label: "Open DTMF keypad",
                    tint: buttonIdle
                ) {
                    showDtmfSheet = true
                }

                Button {
                    manager.hangUp()
                } label: {
                    Image(systemName: "phone.down.fill")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(width: 56, height: 56)
                        .background(crimson)
                        .clipShape(Circle())
                }
                .accessibilityLabel("End call")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(dockDark)
    }

    private func controlButton(
        icon: String,
        label: String,
        tint: Color,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 50, height: 50)
                .background(tint)
                .cornerRadius(8)
        }
        .disabled(disabled)
        .opacity(disabled ? 0.45 : 1.0)
        .accessibilityLabel(label)
    }

    private func qualityColor(_ quality: CallQualityLevel) -> Color {
        switch quality {
        case .excellent, .good: return goodTint
        case .fair: return fairTint
        case .poor, .bad: return poorTint
        case .unknown: return Color.white.opacity(0.75)
        }
    }
}
