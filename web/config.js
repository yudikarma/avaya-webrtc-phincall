/**
 * Default configuration constants for the Avaya WebRTC Connect 4.0.10 Web client.
 * Override these values in the Pre-Call form before initiating an interaction.
 */
window.PHINCALL_CONFIG = Object.freeze({
  sdkVersionTarget: "4.0.10",
  tokenProxy: {
    endpoint: "/api/tokens",
    expirationMs: "120000"
  },
  oceanaDefaults: {
    amcServer: "amc.contactcenter.example.com",
    amcPort: 443,
    amcUrlPath: "",
    aawgServer: "aawg.contactcenter.example.com",
    aawgPort: 443,
    secure: true,
    destinationNumber: "1000",
    attributes: "Language: English; Service: Sales",
    servicePriority: 5,
    context: "ORD-2026-00419",
    topic: "billing",
    resolution: "RESOLUTION_1280x720"
  },
  eliteDefaults: {
    aawgServer: "aawg.contactcenter.example.com",
    aawgPort: 443,
    secure: true,
    destinationNumber: "2000",
    context: "ORD-2026-00419",
    topic: "technical support",
    resolution: "RESOLUTION_1280x720"
  }
});
