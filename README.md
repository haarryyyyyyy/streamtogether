# 🎬 WatchTogether

> **High-precision synchronized video streaming & real-time chat application for Android powered by a server-authoritative WebSocket architecture.**

Watch videos, local files, and live streams together with friends in perfect millisecond synchronization with zero onboarding friction — no accounts, passwords, or emails required.

---

## ⚡ Core Concept: Zero-Friction Group Watching

$$\text{Launch App} \longrightarrow \text{Enter Display Name} \longrightarrow \text{Create or Join 6-Digit Room} \longrightarrow \text{Enjoy Synchronized Playback}$$

- **No Sign-Up or Accounts**: Each device generates an anonymous, persistent identifier stored locally.
- **Instant Numeric Room Codes**: Rooms use clean 6-digit numeric codes (e.g. `849201`) for quick keypad entry.
- **Automatic Master Alignment**: Guests automatically sync to the host's playback position the moment they join.

---

## 🚀 Key Features & Capabilities

### 1. ⏱️ Server-Authoritative Lag-Free Sync Engine
- **NTP Clock Calibration**: Measures round-trip time (RTT) and calculates precise clock offset between clients and the server:
  $$\text{Offset } \theta = \frac{(T_2 - T_1) + (T_3 - T_4)}{2}, \quad T_{\text{server}} = T_{\text{local}} + \theta$$
- **Dynamic Micro-Speed Adjustment**: 
  - Sub-perceptible speed ramping ($0.96\times - 1.04\times$) corrects small timing drifts without causing audio clicks, stutter, or buffer reloads.
  - Coordinated macro-seek for larger position differences.
- **Continuous Host Sync Heartbeat**: Host broadcasts regular position heartbeats to eliminate cumulative drift during long movies.
- **Coordinated Seek Pre-Buffering Gate**: When seeking forward or backward, all devices pause and pre-buffer stream chunks before simultaneously resuming in lockstep.
- **One-Tap Room Synchronization**: A dedicated sync button allows instant resynchronization of all participants to the same playback position.

---

### 2. 📱 Host Device Local Video Sharing & Cloud Streaming
- **Direct Video Streaming**: Supports direct `.mp4`, `.mkv`, `.webm`, `.m3u8` (HLS), and `.mpd` (DASH) streams.
- **Host Device Video Sharing**: Host can select any local video from their phone's gallery; the built-in HTTP 206 byte-range media server relays and streams the video to all guests in the room in real time.
- **Extensive Codec Support**: Hardware and software decoding fallback for AC3, EAC3, DTS, Opus, AAC, and multi-channel audio tracks.

---

### 3. 💬 Subtitles & Audio Track Customization
- **Embedded Subtitle Selection**: Dedicated subtitle menu supporting embedded and external caption tracks with high-contrast outlines.
- **Default OFF Subtitles**: Subtitles remain off by default and can be toggled on demand.
- **Multi-Track Audio Switching**: Easily switch between alternate language tracks and surround sound streams.

---

### 4. 🎛️ Modern Video Player Experience
- **Orientation & Fullscreen Controls**: Seamless switching between portrait view (with live chat list) and landscape fullscreen theater mode.
- **Swipe Gestures**:
  - Vertical swipe on the left half of the screen adjusts screen brightness with a HUD indicator.
  - Vertical swipe on the right half of the screen adjusts media volume.
- **Lock Controls**: One-tap lock mode hides all controls and gesture inputs to prevent accidental touches during movies.
- **Floating Overlay Chat**: Stacked, transparent live chat overlay in landscape fullscreen.
- **Picture-in-Picture (PiP)**: Keep watching in floating mini-player mode with background audio support.

---

### 5. 👑 Host Room Management & Security
- **Host Control Mode**: Only the host can play, pause, seek, switch media, or manage participants.
- **Shared Control Mode**: All participants can interact with playback.
- **Room Lock**: Host can lock the room to prevent new entries.
- **Participant Management**: View live buffering statuses and kick abusive participants.

---

## 🏗️ Architecture Overview

```
   ┌─────────────────────────────────────────────────────────┐
   │                    WatchTogether Server                 │
   │  • WebSocket Authoritative Room State Machine           │
   │  • NTP Time Synchronization Endpoint                    │
   │  • HTTP 206 Byte-Range Video Streaming Relay            │
   └───────────────▲─────────────────────────▲───────────────┘
                   │                         │
          WebSocket Messages        WebSocket Messages
          (Play/Pause/Seek/Chat)    (Play/Pause/Seek/Chat)
                   │                         │
   ┌───────────────▼───────────────┐ ┌───────▼───────────────┐
   │       Host Android App        │ │      Guest Android    │
   │  • ExoPlayer / Media3 Engine  │ │  • LagFreeSyncEngine  │
   │  • Live Playback Heartbeat    │ │  • Speed Adjustment   │
   │  • Jetpack Compose UI         │ │  • Live Chat Overlay  │
   └───────────────────────────────┘ └───────────────────────┘
```

---

## 📄 License
Released under the MIT License. Curated open movies are licensed under Creative Commons.
