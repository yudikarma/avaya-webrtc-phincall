import SwiftUI

struct PreCallView: View {
    let manager: AvayaInteractionManager
    @ObservedObject var state: CallSessionState

    @State private var settings = AvayaClientSettings()

    private let canvasLight = Color(red: 0.965, green: 0.969, blue: 0.976)
    private let slateNavy = Color(red: 0.106, green: 0.165, blue: 0.255)
    private let crimsonAccent = Color(red: 0.784, green: 0.063, blue: 0.180)
    private let primaryInk = Color(red: 0.067, green: 0.094, blue: 0.133)
    private let secondaryInk = Color(red: 0.278, green: 0.333, blue: 0.412)
    private let errorInk = Color(red: 0.706, green: 0.137, blue: 0.094)

    var body: some View {
        VStack(spacing: 0) {
            headerBar

            Form {
                Section("Contact Center Platform") {
                    Picker("Platform", selection: $settings.platform) {
                        ForEach(AvayaPlatformChoice.allCases) { platform in
                            Text(platform.rawValue).tag(platform)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Server and Gateway") {
                    if settings.platform == .oceana {
                        TextField("AMC Server FQDN", text: $settings.amcServer)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        TextField("AMC Port", value: $settings.amcPort, format: .number)
                            .keyboardType(.numberPad)
                        TextField("AMC URL Path (optional)", text: $settings.amcUrlPath)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }

                    TextField("AAWG Server FQDN", text: $settings.aawgServer)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("AAWG Port", value: $settings.aawgPort, format: .number)
                        .keyboardType(.numberPad)
                    TextField("AAWG URL Path (optional)", text: $settings.aawgUrlPath)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Toggle("Use HTTPS / TLS", isOn: $settings.aawgSecure)
                        .onChange(of: settings.aawgSecure) { newVal in
                            settings.amcSecure = newVal
                        }
                }

                Section("Intermediary Token Proxy") {
                    TextField("Token Proxy Host", text: $settings.tokenProxyHost)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Token Proxy Port", value: $settings.tokenProxyPort, format: .number)
                        .keyboardType(.numberPad)
                    TextField("Token Proxy Path", text: $settings.tokenProxyPath)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Toggle("Proxy HTTPS", isOn: $settings.tokenProxySecure)
                }

                Section("Caller and Destination") {
                    TextField("Destination Number / SIP Address", text: $settings.destinationNumber)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Calling Number (Unique Customer ID)", text: $settings.callingNumber)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Caller Display Name", text: $settings.displayName)
                }

                Section("Routing, Screen Pop Context, and Topic") {
                    if settings.platform == .oceana {
                        TextField("Attributes (Language: English; Service: Sales)", text: $settings.attributesRaw)
                            .autocorrectionDisabled()
                    }
                    TextField("Context ID (Order or Product ID)", text: $settings.contextId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Topic (Customer Journey)", text: $settings.topic)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                Section("Video Capture Settings") {
                    Picker("Initial Camera", selection: $settings.initialCamera) {
                        ForEach(CameraSelectionChoice.allCases) { cam in
                            Text(cam.rawValue).tag(cam)
                        }
                    }
                    Picker("Capture Resolution", selection: $settings.resolutionPreference) {
                        ForEach(VideoResolutionChoice.allCases) { res in
                            Text(res.rawValue).tag(res)
                        }
                    }
                    Picker("Capture Orientation", selection: $settings.orientationPreference) {
                        ForEach(VideoOrientationChoice.allCases) { orientation in
                            Text(orientation.rawValue).tag(orientation)
                        }
                    }
                    Toggle("Mute video automatically in background", isOn: $settings.muteVideoInBackground)
                }

                if state.isFetchingToken {
                    Section {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text("Requesting csaGuest token from proxy...")
                                .foregroundColor(primaryInk)
                                .font(.subheadline)
                        }
                    }
                } else if let error = state.statusError {
                    Section {
                        Text(error)
                            .foregroundColor(errorInk)
                            .font(.subheadline)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(canvasLight)

            bottomActionBar
        }
        .background(canvasLight)
    }

    private var headerBar: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.white.opacity(0.14))
                    .frame(width: 34, height: 34)
                Image(systemName: "waveform")
                    .foregroundColor(.white)
                    .font(.system(size: 16, weight: .semibold))
            }
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                Text("PhinCall")
                    .font(.headline)
                    .foregroundColor(.white)
                Text("Avaya WebRTC Connect \(state.sdkVersion)")
                    .font(.caption)
                    .foregroundColor(Color.white.opacity(0.8))
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(slateNavy)
    }

    private var bottomActionBar: some View {
        HStack(spacing: 12) {
            Button {
                manager.initiateLiveHelp(settings: settings, media: .audio)
            } label: {
                Text("Live Help: Voice")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .foregroundColor(slateNavy)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(slateNavy, lineWidth: 1.5)
                    )
            }
            .disabled(state.isPreCallLocked)
            .opacity(state.isPreCallLocked ? 0.5 : 1.0)

            Button {
                manager.initiateLiveHelp(settings: settings, media: .video)
            } label: {
                Text("Live Help: Video")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .foregroundColor(.white)
                    .background(crimsonAccent)
                    .cornerRadius(8)
            }
            .disabled(state.isPreCallLocked)
            .opacity(state.isPreCallLocked ? 0.5 : 1.0)
        }
        .padding(16)
        .background(Color.white)
        .overlay(Divider(), alignment: .top)
    }
}
