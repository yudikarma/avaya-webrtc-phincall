import AVFoundation
import Foundation
import UIKit

/// Implements the 4-step Avaya WebRTC Connect 4.0.10 SDK lifecycle on iOS:
/// 1) Token retrieval -> 2) AOOceanaCustomerWebVoiceVideo init -> 3) AOWork -> 4) AOAudioInteraction / AOVideoInteraction.
@MainActor
final class AvayaInteractionManager: NSObject {

    let state = CallSessionState()
    private let tokenService = TokenService()

    private var activeSettings: AvayaClientSettings?
    private var timer: Timer?
    private var discardTriggered = false
    private var mutedVideoForBackground = false

    // Retained UIView containers bound to AOVideoDevice setLocalView / setRemoteView.
    let localVideoContainerView: UIView = {
        let view = UIView()
        view.backgroundColor = UIColor(red: 0.10, green: 0.13, blue: 0.18, alpha: 1.0)
        view.layer.cornerRadius = 12
        view.layer.borderWidth = 2
        view.layer.borderColor = UIColor.white.withAlphaComponent(0.35).cgColor
        view.clipsToBounds = true
        return view
    }()

    let remoteVideoContainerView: UIView = {
        let view = UIView()
        view.backgroundColor = UIColor(red: 0.06, green: 0.08, blue: 0.11, alpha: 1.0)
        view.clipsToBounds = true
        return view
    }()

    #if canImport(OceanaCustomerWebVoiceVideo)
    private var sdkClient: AOOceanaCustomerWebVoiceVideo?
    private var audioInteraction: AOAudioInteraction?
    private var videoInteraction: AOVideoInteraction?
    private var videoDevice: AOVideoDevice?
    #endif

    override init() {
        super.init()
        registerLifecycleObservers()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    func initiateLiveHelp(settings: AvayaClientSettings, media: CallMediaChoice) {
        guard !state.isPreCallLocked else { return }

        if settings.platform == .oceana && settings.amcServer.trimmingCharacters(in: .whitespaces).isEmpty {
            state.statusError = "AMC Server FQDN is required for Avaya Oceana."
            return
        }
        if settings.aawgServer.trimmingCharacters(in: .whitespaces).isEmpty {
            state.statusError = "AAWG Server FQDN is required."
            return
        }
        if settings.platform == .elite && settings.destinationNumber.trimmingCharacters(in: .whitespaces).isEmpty {
            state.statusError = "Destination Number is required for Avaya Aura Elite."
            return
        }

        // Disable call triggers immediately until the call ends and discardComplete() fires (Guide p. 33).
        state.isPreCallLocked = true
        state.isFetchingToken = true
        state.statusError = nil
        state.mediaChoice = media
        state.platformChoice = settings.platform
        state.selectedCamera = settings.initialCamera
        state.resolutionPreference = settings.resolutionPreference
        state.orientationPreference = settings.orientationPreference
        activeSettings = settings
        discardTriggered = false

        Task {
            do {
                try await requestMediaPermissions(includeCamera: media == .video)
                configureAudioSession(isVideo: media == .video)

                // Step 1: Fetch single-use csaGuest token via intermediary proxy.
                let token = try await tokenService.fetchAuthorizationToken(settings: settings)
                state.isFetchingToken = false
                state.isCallActive = true

                // Steps 2-4: Initialize SDK, construct Work, and start interaction.
                startSdkInteraction(token: token, settings: settings, media: media)
            } catch {
                tokenService.clearToken()
                state.isFetchingToken = false
                state.isPreCallLocked = false
                state.isCallActive = false
                state.statusError = error.localizedDescription
            }
        }
    }

    private func requestMediaPermissions(includeCamera: Bool) async throws {
        let micGranted = await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        guard micGranted else {
            throw NSError(
                domain: "PhinCall.Permissions",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Microphone permission is required for WebRTC calls."]
            )
        }

        if includeCamera {
            let camGranted = await withCheckedContinuation { continuation in
                AVCaptureDevice.requestAccess(for: .video) { granted in
                    continuation.resume(returning: granted)
                }
            }
            guard camGranted else {
                throw NSError(
                    domain: "PhinCall.Permissions",
                    code: 2,
                    userInfo: [NSLocalizedDescriptionKey: "Camera permission is required for video interactions."]
                )
            }
        }
    }

    private func configureAudioSession(isVideo: Bool) {
        let audioSession = AVAudioSession.sharedInstance()
        do {
            let mode: AVAudioSession.Mode = isVideo ? .videoChat : .voiceChat
            try audioSession.setCategory(.playAndRecord, mode: mode, options: [.allowBluetooth, .defaultToSpeaker])
            try audioSession.setActive(true)
        } catch {
            state.signallingBanner = "Audio session configuration warning: \(error.localizedDescription)"
        }
    }

    private func startSdkInteraction(token: String, settings: AvayaClientSettings, media: CallMediaChoice) {
        #if canImport(OceanaCustomerWebVoiceVideo)
        // Step 2: Build AOClientConfiguration and instantiate AOOceanaCustomerWebVoiceVideo.
        let clientConfig = AOClientConfiguration()

        let gatewayConfig = AOWebGatewayConfiguration()
        gatewayConfig.webGatewayAddress = settings.aawgServer
        gatewayConfig.port = NSNumber(value: settings.aawgPort)
        gatewayConfig.isSecure = settings.aawgSecure
        if !settings.aawgUrlPath.isEmpty {
            gatewayConfig.webGatewayUrlPath = settings.aawgUrlPath
        }
        clientConfig.webGatewayConfiguration = gatewayConfig

        // AOConfiguration applies to Avaya Oceana (Guide 4.0.10, p. 47).
        if settings.platform == .oceana {
            let amcConfig = AOConfiguration()
            amcConfig.restUrl = settings.amcServer
            amcConfig.port = NSNumber(value: settings.amcPort)
            amcConfig.isSecure = settings.amcSecure
            if !settings.amcUrlPath.isEmpty {
                amcConfig.urlPath = settings.amcUrlPath
            }
            clientConfig.configuration = amcConfig
        }

        let client = AOOceanaCustomerWebVoiceVideo(clientConfiguration: clientConfig)
        sdkClient = client
        state.sdkVersion = client.versionNumber ?? "4.0.10"

        // Step 3: Derive AOWork and attach Services/Attributes, Context, and Topic.
        let work = client.createWork()
        if !settings.contextId.isEmpty {
            work.context = settings.contextId
        }
        if !settings.topic.isEmpty {
            work.topic = settings.topic
        }
        if !settings.locale.isEmpty {
            work.locale = settings.locale
        }
        if !settings.routingStrategy.isEmpty {
            work.routingStrategy = settings.routingStrategy
        }

        if settings.platform == .oceana {
            let attrMap = settings.parsedAttributeMap()
            if !attrMap.isEmpty {
                let service = AOService()
                service.attributes = attrMap as NSDictionary
                service.priority = NSNumber(value: settings.servicePriority)
                work.services = [service]
            }
        }

        let platformType: AOPlatformType = (settings.platform == .oceana)
            ? AOPlatformType_OCEANA
            : AOPlatformType_ELITE

        // Step 4: Derive Audio/Video interaction, bind token/delegates/views, and call start().
        switch media {
        case .audio:
            let interaction = work.createAudioInteraction()
            interaction.authorizationToken = token
            if !settings.destinationNumber.isEmpty {
                interaction.destinationAddress = settings.destinationNumber
            }
            interaction.setPlatformType(platformType)
            interaction.delegate = self
            interaction.connectionListenerDelegate = self
            audioInteraction = interaction
            interaction.start()

        case .video:
            let interaction = work.createVideoInteraction()
            interaction.authorizationToken = token
            if !settings.destinationNumber.isEmpty {
                interaction.destinationAddress = settings.destinationNumber
            }
            interaction.setPlatformType(platformType)
            interaction.delegate = self
            interaction.connectionListenerDelegate = self
            videoInteraction = interaction

            if let device = interaction.videoDevice {
                videoDevice = device
                device.setLocalView(localVideoContainerView)
                device.setRemoteView(remoteVideoContainerView)
                device.selectCamera(mapCameraType(settings.initialCamera))
            }
            interaction.start()
        }
        #else
        state.interactionState = .failed
        state.statusError = "Avaya XCFrameworks are not linked in this build target. Place the 4 XCFrameworks in ios/Frameworks/ and regenerate with xcodegen."
        state.isCallActive = false
        state.isPreCallLocked = false
        #endif
    }

    // In-call controls

    func toggleAudioMute() {
        let next = !state.isAudioMuted
        #if canImport(OceanaCustomerWebVoiceVideo)
        audioInteraction?.muteAudio(next)
        videoInteraction?.muteAudio(next)
        #endif
        state.isAudioMuted = next
    }

    func toggleVideoMute() {
        let next = !state.isVideoMuted
        #if canImport(OceanaCustomerWebVoiceVideo)
        videoInteraction?.muteVideo(next)
        #endif
        state.isVideoMuted = next
    }

    func toggleVideoEnabled() {
        let next = !state.isVideoEnabled
        #if canImport(OceanaCustomerWebVoiceVideo)
        videoInteraction?.enableVideo(next)
        #endif
        state.isVideoEnabled = next
    }

    func selectCamera(_ camera: CameraSelectionChoice) {
        state.selectedCamera = camera
        #if canImport(OceanaCustomerWebVoiceVideo)
        videoDevice?.selectCamera(mapCameraType(camera))
        #endif
    }

    func switchCamera() {
        let next: CameraSelectionChoice = (state.selectedCamera == .front) ? .back : .front
        state.selectedCamera = next
        #if canImport(OceanaCustomerWebVoiceVideo)
        videoDevice?.switchCamera()
        #endif
    }

    func updateVideoCapture(resolution: VideoResolutionChoice, orientation: VideoOrientationChoice) {
        state.resolutionPreference = resolution
        state.orientationPreference = orientation
        #if canImport(OceanaCustomerWebVoiceVideo)
        videoDevice?.setVideoCaptureResolutionWithCaptureOrientation(
            mapResolution(resolution),
            orientationPreference: mapOrientation(orientation)
        )
        #endif
    }

    func toggleHold() {
        guard !state.isHeldRemotely else { return }
        #if canImport(OceanaCustomerWebVoiceVideo)
        let completion: (Error?) -> Void = { [weak self] error in
            if let error {
                Task { @MainActor in
                    self?.state.signallingBanner = "Hold state update failed: \(error.localizedDescription)"
                }
            }
        }
        if state.isHeldLocally {
            audioInteraction?.unhold(completionHandler: completion)
            videoInteraction?.unhold(completionHandler: completion)
        } else {
            audioInteraction?.hold(completionHandler: completion)
            videoInteraction?.hold(completionHandler: completion)
        }
        #endif
    }

    func sendDTMF(_ digit: String) {
        #if canImport(OceanaCustomerWebVoiceVideo)
        guard let tone = mapDTMFTone(digit) else { return }
        audioInteraction?.sendDTMF(tone)
        videoInteraction?.sendDTMF(tone)
        #endif
    }

    /// Terminates the interaction via end() followed by discard() (Appendix D).
    func hangUp() {
        guard !discardTriggered else { return }
        discardTriggered = true
        stopTimer()
        #if canImport(OceanaCustomerWebVoiceVideo)
        if let audio = audioInteraction {
            audio.end()
            audio.discard()
        } else if let video = videoInteraction {
            video.end()
            video.discard()
        } else {
            completeTeardown()
        }
        #else
        completeTeardown()
        #endif
    }

    private func triggerDiscardAfterRemoteTermination() {
        guard !discardTriggered else { return }
        discardTriggered = true
        stopTimer()
        #if canImport(OceanaCustomerWebVoiceVideo)
        audioInteraction?.discard()
        videoInteraction?.discard()
        #else
        completeTeardown()
        #endif
    }

    private func completeTeardown() {
        stopTimer()
        tokenService.clearToken()
        #if canImport(OceanaCustomerWebVoiceVideo)
        audioInteraction?.delegate = nil
        audioInteraction?.connectionListenerDelegate = nil
        videoInteraction?.delegate = nil
        videoInteraction?.connectionListenerDelegate = nil
        audioInteraction = nil
        videoInteraction = nil
        videoDevice = nil
        sdkClient = nil
        #endif
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        state.isCallActive = false
        state.isPreCallLocked = false
        state.elapsedSeconds = 0
        state.isAudioMuted = false
        state.isVideoMuted = false
        state.isVideoEnabled = true
        state.isHeldLocally = false
        state.isHeldRemotely = false
        state.callQuality = .unknown
        state.signallingBanner = nil
    }

    // Background mode handling per Guide 4.0.10 (p. 54):
    // iOS suspends video automatically on background and resumes on foreground unless explicitly muted.
    private func registerLifecycleObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    @objc private func handleDidEnterBackground() {
        guard state.isCallActive,
              state.mediaChoice == .video,
              activeSettings?.muteVideoInBackground == true,
              !state.isVideoMuted else { return }
        mutedVideoForBackground = true
        toggleVideoMute()
    }

    @objc private func handleWillEnterForeground() {
        // Explicit background mute requires explicit unmute on foreground return (Guide 4.0.10, p. 54).
        guard mutedVideoForBackground else { return }
        mutedVideoForBackground = false
        if state.isVideoMuted {
            toggleVideoMute()
        }
    }

    private func startTimer() {
        stopTimer()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.refreshElapsedFromSdk()
            }
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    private func refreshElapsedFromSdk() {
        #if canImport(OceanaCustomerWebVoiceVideo)
        let ms: Int
        if let video = videoInteraction {
            ms = Int(video.getInteractionTimeElapsed())
        } else if let audio = audioInteraction {
            ms = Int(audio.getInteractionTimeElapsed())
        } else {
            ms = -1
        }
        state.elapsedSeconds = ms >= 0 ? (ms / 1000) : (state.elapsedSeconds + 1)
        #else
        state.elapsedSeconds += 1
        #endif
    }
}

#if canImport(OceanaCustomerWebVoiceVideo)
extension AvayaInteractionManager: AOAudioInteractionDelegate, AOVideoInteractionDelegate, AOConnectionListenerDelegate, AOLoggerDelegate {

    nonisolated func interactionInitiating() {
        Task { @MainActor in
            state.interactionState = .initiating
        }
    }

    nonisolated func interactionRemoteAlerting() {
        Task { @MainActor in
            state.interactionState = .remoteAlerting
            startTimer()
            if state.mediaChoice == .video {
                updateVideoCapture(
                    resolution: state.resolutionPreference,
                    orientation: state.orientationPreference
                )
            }
        }
    }

    nonisolated func interactionActive() {
        Task { @MainActor in
            state.interactionState = .established
            state.signallingBanner = nil
        }
    }

    nonisolated func interactionEnded() {
        Task { @MainActor in
            state.interactionState = .ended
            triggerDiscardAfterRemoteTermination()
        }
    }

    nonisolated func interactionFailed(_ error: Error) {
        Task { @MainActor in
            state.interactionState = .failed
            state.statusError = "Interaction failed: \(error.localizedDescription)"
            triggerDiscardAfterRemoteTermination()
        }
    }

    nonisolated func interactionAudioMuteStatusChanged(_ isMuted: Bool) {
        Task { @MainActor in
            state.isAudioMuted = isMuted
        }
    }

    nonisolated func interactionVideoMuteStatusChanged(_ isMuted: Bool) {
        Task { @MainActor in
            state.isVideoMuted = isMuted
        }
    }

    nonisolated func interactionVideoEnabledStatusChanged(_ isEnabled: Bool) {
        Task { @MainActor in
            state.isVideoEnabled = isEnabled
        }
    }

    nonisolated func holdComplete() {
        Task { @MainActor in
            state.interactionState = .held
            state.isHeldLocally = true
        }
    }

    nonisolated func unholdComplete() {
        Task { @MainActor in
            state.isHeldLocally = false
            state.interactionState = .established
        }
    }

    nonisolated func remoteHoldComplete() {
        Task { @MainActor in
            state.isHeldRemotely = true
            state.interactionState = .heldRemotely
        }
    }

    nonisolated func remoteUnholdComplete() {
        Task { @MainActor in
            state.isHeldRemotely = false
            state.interactionState = .established
        }
    }

    nonisolated func onInteractionQualityChanged(_ quality: AOCallQuality) {
        Task { @MainActor in
            switch quality {
            case AOCallQuality_EXCELLENT: state.callQuality = .excellent
            case AOCallQuality_GOOD: state.callQuality = .good
            case AOCallQuality_FAIR: state.callQuality = .fair
            case AOCallQuality_POOR: state.callQuality = .poor
            case AOCallQuality_BAD: state.callQuality = .bad
            default: state.callQuality = .unknown
            }
        }
    }

    nonisolated func discardComplete() {
        Task { @MainActor in
            completeTeardown()
        }
    }

    nonisolated func interactionServiceConnected() {
        Task { @MainActor in
            state.signallingBanner = nil
        }
    }

    nonisolated func onInteractionServiceConnecting() {
        Task { @MainActor in
            state.signallingBanner = "Signalling interrupted. Reconnecting while media remains active."
        }
    }

    nonisolated func onInteractionServiceDisconnectedWithError(_ error: Error) {
        Task { @MainActor in
            state.signallingBanner = "Signalling disconnected: \(error.localizedDescription)"
        }
    }

    nonisolated func didPrintLog(_ level: CSLogLevel, withTag tag: String?, message: String) {
        #if DEBUG
        print("[AvayaSDK][\(tag ?? "General")] \(message)")
        #endif
    }

    private func mapCameraType(_ choice: CameraSelectionChoice) -> AOCameraType {
        choice == .front ? AOCameraTypeFront : AOCameraTypeBack
    }

    private func mapResolution(_ choice: VideoResolutionChoice) -> AOVideoCapturePreference {
        switch choice {
        case .min: return AOVideoCapturePreference_Min
        case .p270: return AOVideoCapturePreference_270p
        case .p360: return AOVideoCapturePreference_360p
        case .p540: return AOVideoCapturePreference_540p
        case .max: return AOVideoCapturePreference_Max
        }
    }

    private func mapOrientation(_ choice: VideoOrientationChoice) -> AOVideoCaptureOrientation {
        switch choice {
        case .landscapeOnly: return AOVideoCaptureOrientation_LandscapeOnly
        case .landscapeOrPortrait: return AOVideoCaptureOrientation_LandscapeOrPortrait
        }
    }

    private func mapDTMFTone(_ digit: String) -> AODTMFTone? {
        switch digit {
        case "0": return AODTMFToneZero
        case "1": return AODTMFToneOne
        case "2": return AODTMFToneTwo
        case "3": return AODTMFToneThree
        case "4": return AODTMFToneFour
        case "5": return AODTMFToneFive
        case "6": return AODTMFToneSix
        case "7": return AODTMFToneSeven
        case "8": return AODTMFToneEight
        case "9": return AODTMFToneNine
        case "*": return AODTMFToneStar
        case "#": return AODTMFTonePound
        default: return nil
        }
    }
}
#endif
