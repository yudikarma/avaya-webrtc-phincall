/**
 * UI Controller for PhinCall Web Reference Client (Avaya WebRTC Connect 4.0.10).
 * Manages Pre-Call validation, In-Call controls, DTMF dialog focus trap, and lifecycle telemetry.
 */
(function () {
  "use strict";

  const cfg = window.PHINCALL_CONFIG;
  const el = id => document.getElementById(id);

  let selectedPlatform = "OCEANA";
  let timerIntervalId = null;
  let dtmfReturnFocusEl = null;

  const ui = {
    sdkBanner: el("sdk-runtime-banner"),
    simulatorToggle: el("toggle-simulator-mode"),
    btnOceana: el("btn-platform-oceana"),
    btnElite: el("btn-platform-elite"),
    oceanaFields: el("oceana-amc-group"),
    attributesFieldGroup: el("oceana-attributes-group"),

    amcServer: el("input-amc-server"),
    amcPort: el("input-amc-port"),
    amcPath: el("input-amc-path"),
    aawgServer: el("input-aawg-server"),
    aawgPort: el("input-aawg-port"),
    secureTls: el("input-secure-tls"),
    tokenEndpoint: el("input-token-endpoint"),
    tokenApiKey: el("input-token-api-key"),
    destination: el("input-destination"),
    callingNumber: el("input-calling-number"),
    displayName: el("input-display-name"),
    attributes: el("input-attributes"),
    context: el("input-context"),
    topic: el("input-topic"),
    resolution: el("input-resolution"),

    btnVoiceCall: el("btn-live-help-voice"),
    btnVideoCall: el("btn-live-help-video"),
    preCallStatus: el("precall-status-panel"),
    preCallStatusText: el("precall-status-text"),

    preCallSection: el("screen-pre-call"),
    inCallSection: el("screen-in-call"),
    inCallTitle: el("incall-mode-label"),
    inCallState: el("incall-state-badge"),
    inCallTimer: el("incall-timer"),
    qualityBanner: el("incall-quality-banner"),
    qualityLabel: el("incall-quality-label"),
    signallingAlert: el("incall-signalling-alert"),

    videoStage: el("video-media-stage"),
    audioStage: el("audio-media-stage"),
    audioStageTitle: el("audio-stage-title"),
    audioStageHint: el("audio-stage-hint"),

    btnMuteAudio: el("btn-mute-audio"),
    btnMuteVideo: el("btn-mute-video"),
    btnEnableVideo: el("btn-enable-video"),
    btnHold: el("btn-hold-call"),
    btnDtmf: el("btn-open-dtmf"),
    btnDiagnostics: el("btn-read-diagnostics"),
    btnHangUp: el("btn-hang-up"),
    diagnosticsOutput: el("diagnostics-output"),

    dtmfDialog: el("dtmf-dialog"),
    dtmfEcho: el("dtmf-echo-display"),
    dtmfClose: el("btn-close-dtmf"),
    dtmfKeys: document.querySelectorAll("[data-dtmf-digit]"),

    eventLogList: el("event-log-list"),
    eventLogEmpty: el("event-log-empty"),
    btnClearLog: el("btn-clear-log")
  };

  function appendLog(category, message) {
    ui.eventLogEmpty.hidden = true;
    const item = document.createElement("li");
    item.className = "log-entry";
    const now = new Date();
    const stamp = now.toTimeString().slice(0, 8);
    item.innerHTML = `<span class="log-time">${stamp}</span><span class="log-tag">${escapeHtml(category)}</span><span class="log-msg">${escapeHtml(message)}</span>`;
    ui.eventLogList.prepend(item);
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;");
  }

  function setLifecycleStep(stepNum, status, detail) {
    const stepEl = el(`lifecycle-step-${stepNum}`);
    const detailEl = el(`lifecycle-detail-${stepNum}`);
    if (!stepEl || !detailEl) return;
    stepEl.dataset.state = status;
    detailEl.textContent = detail;
  }

  function resetLifecycleSteps() {
    setLifecycleStep(1, "idle", "Awaiting Live Help trigger (POST /api/tokens).");
    setLifecycleStep(2, "idle", "SDK initializes only when a call is requested.");
    setLifecycleStep(3, "idle", "Attaches Services, Screen Pop context, and Journey topic.");
    setLifecycleStep(4, "idle", "Starts AudioInteraction or VideoInteraction.");
  }

  function setPreCallStatus(kind, message) {
    ui.preCallStatus.dataset.status = kind;
    ui.preCallStatusText.textContent = message;
  }

  function setCallButtonsDisabled(disabled) {
    ui.btnVoiceCall.disabled = disabled;
    ui.btnVideoCall.disabled = disabled;
  }

  function formatDuration(ms) {
    const totalSec = Math.max(0, Math.floor(ms / 1000));
    const mins = String(Math.floor(totalSec / 60)).padStart(2, "0");
    const secs = String(totalSec % 60).padStart(2, "0");
    return `${mins}:${secs}`;
  }

  function updateQualityBanner(quality) {
    const normalized = String(quality || "UNKNOWN").toUpperCase();
    let tier = "neutral";
    if (normalized === "EXCELLENT" || normalized === "GOOD") tier = "good";
    else if (normalized === "FAIR") tier = "fair";
    else if (normalized === "POOR" || normalized === "BAD") tier = "poor";

    ui.qualityBanner.dataset.quality = tier;
    ui.qualityLabel.textContent = `Call Quality: ${normalized}`;
    appendLog("QUALITY", `CallQuality updated to ${normalized}`);
  }

  const controller = new window.AvayaWebRtcController({
    onLifecycleStep({ stepNumber, status, detail }) {
      setLifecycleStep(stepNumber, status, detail);
      appendLog(`STEP ${stepNumber}`, detail);
    },
    onSignalling(state) {
      appendLog("SIGNAL", `Signalling path: ${state}`);
      if (state === "CONNECTING") {
        ui.signallingAlert.hidden = false;
        ui.signallingAlert.textContent = "Signalling interrupted. Reconnecting while media remains active.";
      } else if (state === "DISCONNECTED") {
        ui.signallingAlert.hidden = false;
        ui.signallingAlert.textContent = "Signalling disconnected and unrecoverable.";
      } else {
        ui.signallingAlert.hidden = true;
      }
    },
    onStateChange(state) {
      ui.inCallState.textContent = state;
      appendLog("STATE", `Interaction state -> ${state}`);
      if (state === "REMOTE_ALERTING" && !timerIntervalId) {
        timerIntervalId = setInterval(() => {
          ui.inCallTimer.textContent = formatDuration(controller.getElapsedMs());
        }, 1000);
      }
    },
    onAudioMute(muted) {
      ui.btnMuteAudio.setAttribute("aria-pressed", String(muted));
      ui.btnMuteAudio.dataset.active = String(muted);
      ui.btnMuteAudio.querySelector(".control-label").textContent = muted ? "Unmute Mic" : "Mute Mic";
      appendLog("AUDIO", muted ? "Microphone muted" : "Microphone unmuted");
    },
    onVideoMute(muted) {
      ui.btnMuteVideo.setAttribute("aria-pressed", String(muted));
      ui.btnMuteVideo.dataset.active = String(muted);
      ui.btnMuteVideo.querySelector(".control-label").textContent = muted ? "Unmute Cam" : "Mute Cam";
      appendLog("VIDEO", muted ? "Camera muted" : "Camera unmuted");
    },
    onVideoEnabled(enabled) {
      ui.btnEnableVideo.setAttribute("aria-pressed", String(!enabled));
      ui.btnEnableVideo.dataset.active = String(!enabled);
      ui.btnEnableVideo.querySelector(".control-label").textContent = enabled ? "Disable Video" : "Enable Video";
      ui.btnMuteVideo.disabled = !enabled;
      ui.videoStage.hidden = !enabled;
      ui.audioStage.hidden = enabled;
      if (!enabled) {
        ui.audioStageTitle.textContent = "Video stream disabled";
        ui.audioStageHint.textContent = "Voice audio remains active. Use Enable Video to resume camera media.";
      }
      appendLog("VIDEO", enabled ? "Video stream enabled" : "Video stream disabled");
    },
    onHoldChange(held) {
      ui.btnHold.setAttribute("aria-pressed", String(held));
      ui.btnHold.dataset.active = String(held);
      ui.btnHold.querySelector(".control-label").textContent = held ? "Resume Call" : "Hold Call";
      ui.inCallState.textContent = held ? "HELD" : controller.getState();
      appendLog("HOLD", held ? "Interaction placed on hold" : "Interaction resumed");
    },
    onCallQuality(quality) {
      updateQualityBanner(quality);
    },
    onEnded({ finalState, errorText }) {
      if (timerIntervalId) {
        clearInterval(timerIntervalId);
        timerIntervalId = null;
      }
      closeDtmfDialog();
      ui.inCallSection.hidden = true;
      ui.preCallSection.hidden = false;
      setCallButtonsDisabled(false);

      if (errorText) {
        setPreCallStatus("error", `${finalState}: ${errorText}`);
        appendLog("ERROR", `${finalState}: ${errorText}`);
      } else {
        setPreCallStatus("idle", "Interaction terminated cleanly. A fresh token will be requested for the next call.");
        appendLog("STATE", `Interaction ended (${finalState}). Pre-call controls unlocked.`);
      }
    }
  });

  function applyPlatformDefaults(platform) {
    selectedPlatform = platform;
    const isOceana = platform === "OCEANA";
    const defaults = isOceana ? cfg.oceanaDefaults : cfg.eliteDefaults;

    ui.btnOceana.setAttribute("aria-pressed", String(isOceana));
    ui.btnElite.setAttribute("aria-pressed", String(!isOceana));
    ui.oceanaFields.hidden = !isOceana;
    ui.attributesFieldGroup.hidden = !isOceana;

    if (isOceana) {
      ui.amcServer.value = defaults.amcServer;
      ui.amcPort.value = String(defaults.amcPort);
      ui.amcPath.value = defaults.amcUrlPath;
      ui.attributes.value = defaults.attributes;
    }
    ui.aawgServer.value = defaults.aawgServer;
    ui.aawgPort.value = String(defaults.aawgPort);
    ui.secureTls.checked = defaults.secure;
    ui.destination.value = defaults.destinationNumber;
    ui.context.value = defaults.context;
    ui.topic.value = defaults.topic;
    ui.resolution.value = defaults.resolution;
  }

  function validateForm() {
    if (selectedPlatform === "OCEANA" && !ui.amcServer.value.trim()) {
      return "AMC Server FQDN is required when Avaya Oceana is selected.";
    }
    if (!ui.aawgServer.value.trim()) {
      return "AAWG Server FQDN is required.";
    }
    if (!ui.tokenEndpoint.value.trim()) {
      return "Intermediary Token Proxy endpoint is required.";
    }
    if (selectedPlatform === "ELITE" && !ui.destination.value.trim()) {
      return "Destination Number is required for Avaya Aura Elite interactions.";
    }
    return null;
  }

  async function handleStartInteraction(mediaMode) {
    const validationError = validateForm();
    if (validationError) {
      setPreCallStatus("error", validationError);
      appendLog("VALIDATE", validationError);
      return;
    }

    // Lock call buttons immediately until the interaction terminates (Guide 4.0.10, p. 33).
    setCallButtonsDisabled(true);
    resetLifecycleSteps();
    setPreCallStatus("loading", `Requesting csaGuest token and starting ${mediaMode} interaction...`);

    const isVideo = mediaMode === "video";
    ui.inCallTitle.textContent = `${selectedPlatform} / ${mediaMode.toUpperCase()}${controller.isVendorSdkPresent() ? "" : " (SIMULATOR)"}`;
    ui.inCallState.textContent = "INITIATING";
    ui.inCallTimer.textContent = "00:00";
    ui.qualityBanner.dataset.quality = "neutral";
    ui.qualityLabel.textContent = "Call Quality: Measuring";
    ui.signallingAlert.hidden = true;
    ui.diagnosticsOutput.hidden = true;
    ui.diagnosticsOutput.textContent = "";

    ui.videoStage.hidden = !isVideo;
    ui.audioStage.hidden = isVideo;
    ui.btnMuteVideo.hidden = !isVideo;
    ui.btnEnableVideo.hidden = !isVideo;
    ui.btnMuteVideo.disabled = false;
    if (!isVideo) {
      ui.audioStageTitle.textContent = "Active Voice Interaction";
      ui.audioStageHint.textContent = "Audio media session is running through WebRTC.";
    }

    // Reset toggle labels cleanly
    ui.btnMuteAudio.setAttribute("aria-pressed", "false");
    ui.btnMuteAudio.dataset.active = "false";
    ui.btnMuteAudio.querySelector(".control-label").textContent = "Mute Mic";
    ui.btnMuteVideo.setAttribute("aria-pressed", "false");
    ui.btnMuteVideo.dataset.active = "false";
    ui.btnMuteVideo.querySelector(".control-label").textContent = "Mute Cam";
    ui.btnEnableVideo.setAttribute("aria-pressed", "false");
    ui.btnEnableVideo.dataset.active = "false";
    ui.btnEnableVideo.querySelector(".control-label").textContent = "Disable Video";
    ui.btnHold.setAttribute("aria-pressed", "false");
    ui.btnHold.dataset.active = "false";
    ui.btnHold.querySelector(".control-label").textContent = "Hold Call";

    ui.btnDtmf.disabled = !controller.isDtmfSupported();

    try {
      await controller.startCall({
        platform: selectedPlatform,
        mediaMode,
        allowSimulator: ui.simulatorToggle.checked,
        amcServer: ui.amcServer.value.trim(),
        amcPort: ui.amcPort.value.trim() || "443",
        amcUrlPath: ui.amcPath.value.trim(),
        aawgServer: ui.aawgServer.value.trim(),
        aawgPort: ui.aawgPort.value.trim() || "443",
        secure: ui.secureTls.checked,
        tokenProxyEndpoint: ui.tokenEndpoint.value.trim(),
        tokenApiKey: ui.tokenApiKey.value.trim(),
        tokenExpirationMs: cfg.tokenProxy.expirationMs,
        destinationNumber: ui.destination.value.trim(),
        callingNumber: ui.callingNumber.value.trim(),
        displayName: ui.displayName.value.trim(),
        attributes: ui.attributes.value.trim(),
        servicePriority: cfg.oceanaDefaults.servicePriority,
        context: ui.context.value.trim(),
        topic: ui.topic.value.trim(),
        resolution: ui.resolution.value,
        localVideoId: "local-video-view",
        remoteVideoId: "remote-video-view"
      });

      ui.preCallSection.hidden = true;
      ui.inCallSection.hidden = false;
      ui.btnHangUp.focus();
    } catch (err) {
      setCallButtonsDisabled(false);
      setPreCallStatus("error", err.message || "Unable to initiate interaction.");
      appendLog("ERROR", err.message || "Unable to initiate interaction.");
    }
  }

  function openDtmfDialog() {
    dtmfReturnFocusEl = document.activeElement;
    ui.dtmfEcho.textContent = "";
    if (typeof ui.dtmfDialog.showModal === "function") {
      ui.dtmfDialog.showModal();
    } else {
      ui.dtmfDialog.setAttribute("open", "true");
    }
  }

  function closeDtmfDialog() {
    if (ui.dtmfDialog.open) {
      ui.dtmfDialog.close();
    }
    if (dtmfReturnFocusEl && typeof dtmfReturnFocusEl.focus === "function") {
      dtmfReturnFocusEl.focus();
      dtmfReturnFocusEl = null;
    }
  }

  function initSdkRuntimeIndicator() {
    if (controller.isVendorSdkPresent()) {
      ui.sdkBanner.dataset.mode = "live";
      ui.sdkBanner.textContent = "Avaya OceanaCustomerWebVoiceVideo.js detected. Live AAWG / Oceana mode active.";
      ui.simulatorToggle.checked = false;
      ui.simulatorToggle.disabled = true;
    } else {
      ui.sdkBanner.dataset.mode = "simulator";
      ui.sdkBanner.textContent =
        "Vendor file web/vendor/OceanaCustomerWebVoiceVideo.js is not loaded. Enable Local SDK Simulator below to test the token proxy and call controls locally.";
      ui.simulatorToggle.checked = true;
    }
  }

  function bindEvents() {
    ui.btnOceana.addEventListener("click", () => applyPlatformDefaults("OCEANA"));
    ui.btnElite.addEventListener("click", () => applyPlatformDefaults("ELITE"));

    ui.btnVoiceCall.addEventListener("click", () => handleStartInteraction("audio"));
    ui.btnVideoCall.addEventListener("click", () => handleStartInteraction("video"));

    ui.btnMuteAudio.addEventListener("click", () => controller.toggleAudioMute());
    ui.btnMuteVideo.addEventListener("click", () => controller.toggleVideoMute());
    ui.btnEnableVideo.addEventListener("click", () => controller.toggleVideoEnabled());
    ui.btnHold.addEventListener("click", () => controller.toggleHold());
    ui.btnHangUp.addEventListener("click", () => controller.hangUp());

    ui.btnDiagnostics.addEventListener("click", () => {
      controller.readDiagnostics(report => {
        ui.diagnosticsOutput.hidden = false;
        ui.diagnosticsOutput.textContent = JSON.stringify(report, null, 2);
        appendLog("STATS", "Read audio/video media diagnostics snapshot.");
      });
    });

    ui.btnDtmf.addEventListener("click", openDtmfDialog);
    ui.dtmfClose.addEventListener("click", closeDtmfDialog);
    ui.dtmfDialog.addEventListener("cancel", event => {
      event.preventDefault();
      closeDtmfDialog();
    });

    ui.dtmfKeys.forEach(btn => {
      btn.addEventListener("click", () => {
        const digit = btn.getAttribute("data-dtmf-digit");
        if (controller.sendDTMF(digit)) {
          ui.dtmfEcho.textContent += digit;
          appendLog("DTMF", `Sent tone "${digit}"`);
        }
      });
    });

    ui.btnClearLog.addEventListener("click", () => {
      ui.eventLogList.innerHTML = "";
      ui.eventLogEmpty.hidden = false;
    });
  }

  ui.tokenEndpoint.value = cfg.tokenProxy.endpoint;
  applyPlatformDefaults("OCEANA");
  resetLifecycleSteps();
  initSdkRuntimeIndicator();
  bindEvents();
})();
