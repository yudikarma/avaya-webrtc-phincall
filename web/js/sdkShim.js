/**
 * Local SDK Simulator matching the Avaya OceanaCustomerWebVoiceVideo.js 4.0.10 public API.
 * Used ONLY when the official vendor SDK file is absent and the user enables Simulator Mode.
 */
(function (global) {
  "use strict";

  const PlatformType = Object.freeze({ OCEANA: "OCEANA", ELITE: "ELITE" });
  const InteractionState = Object.freeze({
    IDLE: "IDLE",
    INITIATING: "INITIATING",
    REMOTE_ALERTING: "REMOTE_ALERTING",
    ESTABLISHED: "ESTABLISHED",
    HELD: "HELD",
    ENDING: "ENDING",
    ENDED: "ENDED",
    FAILED: "FAILED"
  });
  const CallQuality = Object.freeze({
    EXCELLENT: "EXCELLENT",
    GOOD: "GOOD",
    FAIR: "FAIR",
    POOR: "POOR",
    BAD: "BAD"
  });
  const CameraResolution = Object.freeze({
    RESOLUTION_416x240: "RESOLUTION_416x240",
    RESOLUTION_640x360: "RESOLUTION_640x360",
    RESOLUTION_848x480: "RESOLUTION_848x480",
    RESOLUTION_1280x720: "RESOLUTION_1280x720",
    RESOLUTION_1920x1080: "RESOLUTION_1920x1080"
  });

  function createSimulatedCanvasStream(labelText, bgColor) {
    if (typeof document === "undefined") return null;
    const canvas = document.createElement("canvas");
    canvas.width = 640;
    canvas.height = 360;
    const ctx = canvas.getContext("2d");
    if (!ctx || typeof canvas.captureStream !== "function") return null;

    let tick = 0;
    const draw = () => {
      ctx.fillStyle = bgColor;
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      ctx.strokeStyle = "rgba(255, 255, 255, 0.16)";
      ctx.lineWidth = 2;
      ctx.strokeRect(16, 16, canvas.width - 32, canvas.height - 32);

      ctx.fillStyle = "#F8FAFC";
      ctx.font = "600 22px system-ui, sans-serif";
      ctx.fillText(labelText, 36, 168);

      ctx.fillStyle = "#B6C2D2";
      ctx.font = "400 14px monospace";
      ctx.fillText(`LOCAL SIMULATOR STREAM | FRAME ${tick++}`, 36, 200);
    };

    draw();
    const intervalId = setInterval(draw, 1000);
    const stream = canvas.captureStream(1);
    stream._stopSim = () => {
      clearInterval(intervalId);
      stream.getTracks().forEach(t => t.stop());
    };
    return stream;
  }

  class SimService {
    constructor() {
      this._attributes = {};
      this._priority = 5;
    }
    setAttributes(attrs) { this._attributes = attrs; }
    getAttributes() { return this._attributes; }
    setPriority(p) { this._priority = p; }
    getPriority() { return this._priority; }
  }

  class SimResource {
    constructor() {
      this._nativeResourceId = "";
      this._sourceName = "";
    }
    setNativeResourceId(id) { this._nativeResourceId = id; }
    setSourceName(src) { this._sourceName = src; }
  }

  class SimVideoDevice {
    constructor() {
      this.localViewId = null;
      this.remoteViewId = null;
      this.resolution = CameraResolution.RESOLUTION_1280x720;
      this._streams = [];
    }
    setLocalView(id) { this.localViewId = id; }
    setRemoteView(id) { this.remoteViewId = id; }
    setCameraCaptureResolution(res) { this.resolution = res; }
    getCameraCaptureResolution() { return this.resolution; }
    _attachSimulatedFeeds() {
      const remoteEl = this.remoteViewId ? document.getElementById(this.remoteViewId) : null;
      const localEl = this.localViewId ? document.getElementById(this.localViewId) : null;
      if (remoteEl) {
        const s = createSimulatedCanvasStream("Simulated Remote Agent Video", "#131C28");
        if (s) {
          remoteEl.srcObject = s;
          this._streams.push(s);
        }
      }
      if (localEl) {
        const s = createSimulatedCanvasStream("Simulated Local Camera", "#1B2A41");
        if (s) {
          localEl.srcObject = s;
          this._streams.push(s);
        }
      }
    }
    _detachFeeds() {
      this._streams.forEach(s => s._stopSim && s._stopSim());
      this._streams = [];
      [this.remoteViewId, this.localViewId].forEach(id => {
        const el = id ? document.getElementById(id) : null;
        if (el) el.srcObject = null;
      });
    }
  }

  class SimInteraction {
    constructor(kind, work, platformType) {
      this._kind = kind; // "audio" or "video"
      this._work = work;
      this._platformType = platformType || PlatformType.OCEANA;
      this._token = null;
      this._destination = "";
      this._state = InteractionState.IDLE;
      this._audioMuted = false;
      this._videoMuted = false;
      this._videoEnabled = kind === "video";
      this._held = false;
      this._startedAt = null;
      this._timers = [];
      this._qualityTimer = null;
      this._videoDevice = kind === "video" ? new SimVideoDevice() : null;
    }

    setPlatformType(pt) { this._platformType = pt; }
    setAuthorizationToken(token) { this._token = token; }
    setDestinationAddress(dest) { this._destination = dest; }
    getVideoDevice() { return this._videoDevice; }
    getInteractionState() { return this._state; }
    getInteractionTimeElapsed() { return this._startedAt ? Date.now() - this._startedAt : 0; }
    isAudioMuted() { return this._audioMuted; }
    isVideoMuted() { return this._videoMuted; }
    isVideoEnabled() { return this._videoEnabled; }
    isCallHeld() { return this._held; }

    _emit(suffix, ...args) {
      const prefix = this._kind === "video" ? "onVideoInteraction" : "onAudioInteraction";
      const fn = this[`${prefix}${suffix}`];
      if (typeof fn === "function") fn(...args);
    }

    start() {
      if (!this._token) {
        this._state = InteractionState.FAILED;
        this._emit("Failed", "NOT_AUTHORIZED");
        return;
      }

      this._state = InteractionState.INITIATING;
      this._emit("ServiceConnected");
      this._emit("Initiating");

      this._timers.push(setTimeout(() => {
        if (this._state === InteractionState.ENDED) return;
        this._state = InteractionState.REMOTE_ALERTING;
        this._startedAt = Date.now();
        this._emit("RemoteAlerting");
      }, 450));

      this._timers.push(setTimeout(() => {
        if (this._state === InteractionState.ENDED) return;
        this._state = InteractionState.ESTABLISHED;
        if (this._videoDevice && this._videoEnabled) {
          this._videoDevice._attachSimulatedFeeds();
        }
        this._emit("Established");
        this._emit("CallQuality", CallQuality.EXCELLENT);

        const levels = [CallQuality.EXCELLENT, CallQuality.GOOD, CallQuality.FAIR, CallQuality.GOOD];
        let idx = 0;
        this._qualityTimer = setInterval(() => {
          if (this._state === InteractionState.ESTABLISHED || this._state === InteractionState.HELD) {
            idx = (idx + 1) % levels.length;
            this._emit("CallQuality", levels[idx]);
          }
        }, 6000);
      }, 1100));
    }

    muteAudio(muted) {
      this._audioMuted = Boolean(muted);
      this._emit("AudioMuteStatusChanged", this._audioMuted);
    }

    muteVideo(muted) {
      if (this._kind !== "video") return;
      this._videoMuted = Boolean(muted);
      this._emit("VideoMuteStatusChanged", this._videoMuted);
    }

    enableVideo(enabled) {
      if (this._kind !== "video") return;
      this._videoEnabled = Boolean(enabled);
      if (this._videoDevice) {
        if (this._videoEnabled) {
          this._videoDevice._attachSimulatedFeeds();
        } else {
          this._videoDevice._detachFeeds();
        }
      }
      this._emit("VideoEnabledStatusChanged", this._videoEnabled);
    }

    holdCall(isHeld) {
      this._held = Boolean(isHeld);
      this._state = this._held ? InteractionState.HELD : InteractionState.ESTABLISHED;
      this._emit("HoldStatusChanged", this._held);
    }

    sendDTMF(_tone) {
      // Simulator accepts valid DTMF digits 0-9, *, #.
    }

    readAudioDetails(callback) {
      if (typeof callback === "function") {
        callback({
          simulated: true,
          codec: "Opus / 48kHz (simulated)",
          packetsSent: Math.max(1, Math.floor(this.getInteractionTimeElapsed() / 20)),
          packetsReceived: Math.max(1, Math.floor(this.getInteractionTimeElapsed() / 20)),
          fractionLost: 0
        });
      }
    }

    readVideoDetails(callback) {
      if (typeof callback === "function" && this._kind === "video") {
        callback({
          simulated: true,
          resolution: this._videoDevice ? this._videoDevice.resolution : "N/A",
          videoEnabled: this._videoEnabled,
          videoMuted: this._videoMuted
        });
      }
    }

    end() {
      this._timers.forEach(clearTimeout);
      this._timers = [];
      if (this._qualityTimer) {
        clearInterval(this._qualityTimer);
        this._qualityTimer = null;
      }
      if (this._videoDevice) {
        this._videoDevice._detachFeeds();
      }
      this._state = InteractionState.ENDING;
      setTimeout(() => {
        this._state = InteractionState.ENDED;
        this._emit("Ended");
      }, 150);
    }
  }

  class SimWork {
    constructor() {
      this._context = "";
      this._topic = "";
      this._services = [];
      this._resources = [];
    }
    setContext(c) { this._context = c; }
    getContext() { return this._context; }
    setTopic(t) { this._topic = t; }
    getTopic() { return this._topic; }
    setServices(s) { this._services = s; }
    getServices() { return this._services; }
    setResources(r) { this._resources = r; }
    createAudioInteraction(platformType) { return new SimInteraction("audio", this, platformType); }
    createVideoInteraction(platformType) { return new SimInteraction("video", this, platformType); }
  }

  class SimulatedOceanaCustomerWebVoiceVideo {
    constructor(clientConfiguration) {
      this._config = clientConfiguration;
    }
    getVersionNumber() { return "4.0.10-simulator"; }
    createWork() { return new SimWork(); }
  }

  SimulatedOceanaCustomerWebVoiceVideo.Services = {
    Work: {
      PlatformType,
      InteractionState,
      Schema: {
        Service: SimService,
        Resource: SimResource
      }
    },
    Device: {
      Video: {
        CallQuality,
        CameraResolution
      }
    }
  };

  global.PhinCallSdkSimulator = SimulatedOceanaCustomerWebVoiceVideo;
})(window);
