# 🎬 WatchTogether

> **Production-quality synchronized video playback & live text chat application for Android with a scalable Node.js/TypeScript Fastify WebSocket backend.**

Watch videos or legally authorized movies together in real-time with sub-millisecond clock synchronization and seamless drift correction — **with zero onboarding friction (no registration, no login, no passwords, no email verification)**.

---

## ⚡ Core Philosophy: Zero Friction

$$\text{Open App} \longrightarrow \text{Enter Display Name} \longrightarrow \text{Enter Room Code} \longrightarrow \text{Start Watching}$$

- **No Passwords or Accounts**: Device creates a persistent anonymous `guestId` (UUID) stored locally.
- **Instant Rooms**: Host creates a room with 1 tap (e.g. `WATCH-8K42`) and shares it natively.
- **Fast Join**: Friends enter the code and are immediately in sync with playback.

---

## 🏗️ Architecture & Features

### 1. Synchronized Playback Engine
- **Server-Authoritative Timing**: Server tracks media state, play/pause status, and anchor timestamps.
- **NTP-Style Clock Synchronization**: Computes client-server clock offset via 4-timestamp ping-pong:
  $$\text{Offset } \theta = \frac{(T_2 - T_1) + (T_3 - T_4)}{2}$$
  $$T_{\text{server}} = T_{\text{local}} + \theta$$
- **Lag-Free Smart Drift Correction**:
  - **In-Sync ($|\Delta| < 50\text{ms}$)**: Standard $1.0\times$ speed.
  - **Micro Drift ($50\text{ms} \le |\Delta| \le 750\text{ms}$)**: Smooth dynamic playback speed adjustment ($0.95\times - 1.05\times$) for zero-jitter catch-up without audio clicks.
  - **Macro Drift ($|\Delta| > 750\text{ms}$)**: Smooth seek to expected target position.

### 2. Host & Participant Controls
- **Host Control Mode**: Only the room host can play, pause, seek, switch media, or kick participants.
- **Shared Control Mode**: All participants can control playback in real-time.
- **Room Lock**: Host can lock the room to prevent new entries.
- **Curated & Custom Media**: Curated open movies (*Big Buck Bunny*, *Tears of Steel*, *Sintel*, etc.) or custom direct stream URLs (`.mp4`, `.m3u8` HLS, DASH).

### 3. Real-Time Text Chat & Presence
- Slide-in chat drawer / bottom sheet with instant messaging.
- Host badges (👑 `HOST`), timestamps, and system notifications.
- Participant list showing live connection & buffering status.
- Local block / mute participant capabilities.

---

## 📁 Repository Structure

```
├── android/                        # Android Application (Kotlin, Jetpack Compose, Media3)
│   ├── app/
│   │   ├── src/main/java/com/syncwatch/app/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── local/          # UserPreferences (Guest ID, Display Name, Recent Rooms)
│   │   │   │   ├── models/         # RoomState, Participant, ChatMessage, SyncPacket
│   │   │   │   └── network/        # SyncWebSocketClient, ClockSyncManager
│   │   │   ├── sync/               # LagFreeSyncEngine
│   │   │   └── ui/                 # HomeScreen, RoomScreen, ChatOverlay, ParticipantList
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── server/                         # Backend (Node.js, TypeScript, WebSockets, REST)
│   ├── index.js                    # Server-authoritative WebSocket engine & HTTP API
│   ├── test-sync.js                # Integration tests
│   ├── Dockerfile                  # Multi-stage production container
│   ├── docker-compose.yml          # Backend + Nginx reverse proxy
│   ├── nginx.conf                  # SSL / WSS reverse proxy configuration
│   └── deploy-oracle.sh            # 1-Click Oracle Cloud VM deployment script
└── .github/workflows/
    └── release.yml                 # Automated APK build & GitHub Release pipeline
```

---

## 🚀 Quick Start Guide

### 1. Running the Backend Locally
```bash
cd server
npm install
npm run start
# Server listens on ws://0.0.0.0:8080 and http://0.0.0.0:8080
```

Run integration test:
```bash
node test-sync.js
```

### 2. Deploying to Oracle Cloud VM (1-Click)
1. Launch an Oracle Cloud Compute Instance (Ubuntu 22.04 or Oracle Linux).
2. Open ports `80`, `443`, and `8080` in your VCN Ingress Rules.
3. SSH into your VM and run:
```bash
git clone https://github.com/<your-username>/WatchTogether.git
cd WatchTogether/server
chmod +x deploy-oracle.sh
./deploy-oracle.sh
```

### 3. Building the Android App
```bash
cd android
./gradlew assembleDebug
# Output APK: android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤖 GitHub Actions Automated Releases
Every `git push` to `main` triggers `.github/workflows/release.yml` which:
1. Runs automated unit tests and synchronization verification.
2. Compiles the signed/debug Android APK.
3. Automatically generates a GitHub Release with version tag and attaches `WatchTogether-v*.apk` for download.

---

## 📄 License
Open source and released under the MIT License. Curated open movies are licensed under Creative Commons.
