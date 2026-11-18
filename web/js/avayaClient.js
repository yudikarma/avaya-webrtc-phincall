/**
 * Avaya WebRTC Connect 4.0.10 JavaScript SDK adapter.
 * Implements the 4-step sequence: Token -> SDK Client -> Work -> Audio/VideoInteraction.
 */
(function (global) {
  "use strict";

  function parseOceanaAttributes(rawText) {
    const map = {};
    if (!rawText || !rawText.trim()) return map;
    rawText.split(";").forEach(segment => {
      const idx = segment.indexOf(":");
      if (idx === -1) return;
      const key = segment.slice(0, idx).trim();
      const values = segment
        .slice(idx + 1)
        .split(",")
        .map(v => v.trim())
        .filter(Boolean);
      if (key && values.length > 0) {
        map[key] = values;
      }
    });
    return map;
  }

  // Legacy EdgeHTML does not support JS SDK sendDTMF (Guide 4.0.10, p. 60).
  function isLegacyEdgeBrowser() {
    return typeof navigator !== "undefined" && /\bEdge\/\d+/.test(navigator.userAgent);
  }

  class AvayaWebRtcController {
    constructor(callbacks) {
      this.callbacks = callbacks || {};
      this.client = null;
      this.work = null;
      this.interaction = null;
      this.videoDevice = null;
      this.mediaMode = "audio";
      this.active = false;
      this.usingSimulator = false;
    }

    isVendorSdkPresent() {
      return typeof global.OceanaCustomerWebVoiceVideo === "function";
    }

    isDtmfSupported() {
      return !isLegacyEdgeBrowser();
    }

    async startCall(options) {
      if (this.active) {
        throw new Error("An interaction is already in progress.");
      }

      const SdkCtor = this.isVendorSdkPresent()
        ? global.OceanaCustomerWebVoiceVideo
        : options.allowSimulator
          ? global.PhinCallSdkSimulator
          : null;

      if (!SdkCtor) {
        throw new Error(
          "OceanaCustomerWebVoiceVideo.js is not loaded. Place the Avaya 4.0.10 SDK in web/vendor/ or enable Local SDK Simulator Mode."
        );
      }

      this.usingSimulator = !this.isVendorSdkPresent();
      this.mediaMode = options.mediaMode;
      this.active = true;

      try {
        // Step 1: Request single-use csaGuest token from intermediary proxy and store in memory.
        this._emitStep(1, "active", "Requesting csaGuest token from intermediary proxy...");
        const tokenResp = await global.PhinCallTokenClient.fetchToken({
          endpoint: options.tokenProxyEndpoint,
          calledNumber: options.destinationNumber,
          callingNumber: options.callingNumber,
          displayName: options.displayName,
          expiration: options.tokenExpirationMs,
          apiKey: options.tokenApiKey
        });
        const token = global.PhinCallTokenClient.consumeToken();
        this._emitStep(1, "done", tokenResp.isSimulatorToken
          ? "Token stored in memory (simulator proxy mode)."
          : "Encrypted csaGuest token stored in memory.");

        // Step 2: Initialize OceanaCustomerWebVoiceVideo with Configuration and WebGatewayConfiguration.
        // Note: JS WebGatewayConfiguration omits webGatewayUrlPath (Guide 4.0.10, p. 60).
        this._emitStep(2, "active", "Initializing OceanaCustomerWebVoiceVideo SDK instance...");
        const clientConfiguration = {
          webGatewayConfiguration: {
            webGatewayAddress: options.aawgServer,
            port: Number(options.aawgPort),
            secure: Boolean(options.secure)
          }
        };
        if (options.platform === "OCEANA") {
          clientConfiguration.configuration = {
            restUrl: options.amcServer,
            port: Number(options.amcPort),
            secure: Boolean(options.secure),
            urlPath: options.amcUrlPath || ""
          };
        }

        this.client = new SdkCtor(clientConfiguration);
        const version = typeof this.client.getVersionNumber === "function"
          ? this.client.getVersionNumber()
          : "4.0.10";
        this._emitStep(2, "done", `SDK initialized (${version}).`);

        // Step 3: Derive Work object and attach Services/Attributes, Context (Screen Pop), and Topic.
        this._emitStep(3, "active", "Configuring Work routing, Screen Pop context, and topic...");
        this.work = this.client.createWork();

        if (options.context && options.context.trim()) {
          this.work.setContext(options.context.trim());
        }
        if (options.topic && options.topic.trim()) {
          this.work.setTopic(options.topic.trim());
        }

        if (options.platform === "OCEANA") {
          const parsedAttributes = parseOceanaAttributes(options.attributes);
          if (Object.keys(parsedAttributes).length > 0 && SdkCtor.Services?.Work?.Schema?.Service) {
            const service = new SdkCtor.Services.Work.Schema.Service();
            service.setAttributes(parsedAttributes);
            service.setPriority(Number(options.servicePriority || 5));
            this.work.setServices([service]);
          }
        }
        this._emitStep(3, "done", "Work object ready.");

        // Step 4: Derive AudioInteraction or VideoInteraction, attach token, register callbacks, start().
        this._emitStep(4, "active", `Creating ${options.mediaMode} interaction (${options.platform})...`);
        const platformEnum = SdkCtor.Services?.Work?.PlatformType?.[options.platform] || options.platform;

        if (options.mediaMode === "video") {
          this.interaction = this.work.createVideoInteraction(platformEnum);
          this._wireVideoCallbacks(this.interaction);
          this.videoDevice = this.interaction.getVideoDevice();
          if (this.videoDevice) {
            this.videoDevice.setLocalView(options.localVideoId);
            this.videoDevice.setRemoteView(options.remoteVideoId);
            const resEnum = SdkCtor.Services?.Device?.Video?.CameraResolution?.[options.resolution] || options.resolution;
            if (typeof this.videoDevice.setCameraCaptureResolution === "function" && resEnum) {
              this.videoDevice.setCameraCaptureResolution(resEnum);
            }
          }
        } else {
          this.interaction = this.work.createAudioInteraction(platformEnum);
          this._wireAudioCallbacks(this.interaction);
        }

        if (options.destinationNumber && options.destinationNumber.trim()) {
          this.interaction.setDestinationAddress(options.destinationNumber.trim());
        }
        this.interaction.setAuthorizationToken(token);
        this.interaction.start();
        this._emitStep(4, "done", "Interaction start() invoked.");
      } catch (err) {
        global.PhinCallTokenClient.clearToken();
        this.active = false;
        this.interaction = null;
        this.videoDevice = null;
        throw err;
      }
    }

    _wireAudioCallbacks(interaction) {
      interaction.onAudioInteractionServiceConnected = () => this._notify("onSignalling", "CONNECTED");
      interaction.onAudioInteractionServiceConnecting = () => this._notify("onSignalling", "CONNECTING");
      interaction.onAudioInteractionServiceDisconnected = () => this._notify("onSignalling", "DISCONNECTED");
      interaction.onAudioInteractionInitiating = () => this._notify("onStateChange", "INITIATING");
      interaction.onAudioInteractionRemoteAlerting = () => this._notify("onStateChange", "REMOTE_ALERTING");
      interaction.onAudioInteractionEstablished = () => this._notify("onStateChange", "ESTABLISHED");
      interaction.onAudioInteractionAudioMuteStatusChanged = muted => this._notify("onAudioMute", muted);
      interaction.onAudioInteractionHoldStatusChanged = held => this._notify("onHoldChange", held);
      interaction.onAudioInteractionCallQuality = quality => this._notify("onCallQuality", quality);
      interaction.onAudioInteractionEnded = () => this._finish("ENDED", null);
      interaction.onAudioInteractionFailed = err => this._finish("FAILED", String(err || "Interaction failed"));
    }

    _wireVideoCallbacks(interaction) {
      interaction.onVideoInteractionServiceConnected = () => this._notify("onSignalling", "CONNECTED");
      interaction.onVideoInteractionServiceConnecting = () => this._notify("onSignalling", "CONNECTING");
      interaction.onVideoInteractionServiceDisconnected = () => this._notify("onSignalling", "DISCONNECTED");
      interaction.onVideoInteractionInitiating = () => this._notify("onStateChange", "INITIATING");
      interaction.onVideoInteractionRemoteAlerting = () => this._notify("onStateChange", "REMOTE_ALERTING");
      interaction.onVideoInteractionEstablished = () => this._notify("onStateChange", "ESTABLISHED");
      interaction.onVideoInteractionAudioMuteStatusChanged = muted => this._notify("onAudioMute", muted);
      interaction.onVideoInteractionVideoMuteStatusChanged = muted => this._notify("onVideoMute", muted);
      interaction.onVideoInteractionVideoEnabledStatusChanged = enabled => this._notify("onVideoEnabled", enabled);
      interaction.onVideoInteractionHoldStatusChanged = held => this._notify("onHoldChange", held);
      interaction.onVideoInteractionCallQuality = quality => this._notify("onCallQuality", quality);
      interaction.onVideoInteractionEnded = () => this._finish("ENDED", null);
      interaction.onVideoInteractionFailed = err => this._finish("FAILED", String(err || "Video interaction failed"));
    }

    _finish(finalState, errorText) {
      this.active = false;
      this.interaction = null;
      this.videoDevice = null;
      this.work = null;
      this.client = null;
      this._notify("onEnded", { finalState, errorText });
    }

    toggleAudioMute() {
      if (!this.interaction) return;
      const next = !this.interaction.isAudioMuted();
      this.interaction.muteAudio(next);
    }

    toggleVideoMute() {
      if (!this.interaction || this.mediaMode !== "video") return;
      const next = !this.interaction.isVideoMuted();
      this.interaction.muteVideo(next);
    }

    toggleVideoEnabled() {
      if (!this.interaction || this.mediaMode !== "video") return;
      const next = !this.interaction.isVideoEnabled();
      this.interaction.enableVideo(next);
    }

    toggleHold() {
      if (!this.interaction) return;
      const next = !this.interaction.isCallHeld();
      this.interaction.holdCall(next);
    }

    sendDTMF(digit) {
      if (!this.interaction || !this.isDtmfSupported()) return false;
      this.interaction.sendDTMF(digit);
      return true;
    }

    getState() {
      return this.interaction ? this.interaction.getInteractionState() : "IDLE";
    }

    getElapsedMs() {
      return this.interaction ? this.interaction.getInteractionTimeElapsed() : 0;
    }

    readDiagnostics(callback) {
      if (!this.interaction) return;
      const report = {};
      if (typeof this.interaction.readAudioDetails === "function") {
        this.interaction.readAudioDetails(audio => {
          report.audio = audio;
          if (this.mediaMode === "video" && typeof this.interaction.readVideoDetails === "function") {
            this.interaction.readVideoDetails(video => {
              report.video = video;
              callback(report);
            });
          } else {
            callback(report);
          }
        });
      }
    }

    hangUp() {
      if (!this.interaction) {
        this._finish("ENDED", null);
        return;
      }
      this.interaction.end();
    }

    _emitStep(stepNumber, status, detail) {
      this._notify("onLifecycleStep", { stepNumber, status, detail });
    }

    _notify(name, payload) {
      if (typeof this.callbacks[name] === "function") {
        this.callbacks[name](payload);
      }
    }
  }

  global.AvayaWebRtcController = AvayaWebRtcController;
})(window);
