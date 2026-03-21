<p align="center">
  <img src="Assest/Icon/hyperdrop-wordmark.png" alt="HyperDrop" width="320" />
</p>

# HyperDrop

HyperDrop is a local-network file transfer app for Android and desktop.

It moves files directly between devices on the same LAN without a cloud relay, an account system, or an internet upload path.

[GitHub Repository](https://github.com/ankix86/HyperDrop) • [Collaboration Guide](CONTRIBUTING.md)

- [About](#about)
- [Screenshots](#screenshots)
- [Current Product Behavior](#current-product-behavior)
- [Platform Overview](#platform-overview)
- [Networking And Transport Design](#networking-and-transport-design)
- [Security Model](#security-model)
- [File Transfer Model](#file-transfer-model)
- [Actual User-Facing Features](#actual-user-facing-features-in-this-repository)
- [Repository Structure](#repository-structure)
- [Build And Run](#build-and-run)
- [External Dependencies](#external-dependencies)
- [Troubleshooting](#troubleshooting)
- [Status](#status-of-this-repository)

## About

The current project contains two production-facing clients:

- Android app written in Kotlin with Jetpack Compose
- Desktop app written in Python with PySide6 and QML

The shared transport model is:

- UDP-based LAN discovery on port `54546`
- TCP session transport on port `54545`
- framed JSON protocol messages
- per-session X25519 key agreement
- AES-GCM encrypted file chunks


## Screenshots
<p align="center">
  <img src="https://raw.githubusercontent.com/ankix86/HyperDrop/main/Assest/Screenshot/Phone.png" width="30%" />
  <img src="https://raw.githubusercontent.com/ankix86/HyperDrop/main/Assest/Screenshot/Desktop.png" width="65%" />
</p>

## Current product behavior

HyperDrop currently works with a selection-first flow.

### Send flow

1. Build a local selection queue first.
2. Add files or folders before choosing a target.
3. Nearby online devices appear automatically.
4. Tap or click a discovered device to send a transfer request.
5. The receiver reviews the request and either accepts or declines it.
6. If accepted, transfer starts immediately.

### Receive flow

1. Turn the receiver online.
2. The device becomes discoverable on the local network.
3. Incoming requests appear inside the Receive screen, not as disconnected system-style popups.
4. The receiver can review the incoming file list, rename targets, change the destination folder, then accept or decline.
5. During transfer, a dedicated receive status screen shows session progress and file-level state.
6. When complete, the finished receive view allows opening received files.

### Selection behavior

The send side no longer behaves like a raw file explorer.

- Main selection card: compact summary only
- Preview tiles: limited preview of selected items
- Edit view: full selection management, remove one, clear all
- Android share sheet input: shared files are added into selection automatically

## Platform overview

### Android app

Path: `android_app/`

Core stack:

- Kotlin
- Jetpack Compose
- Material 3
- Android Foreground Service
- Kotlin Coroutines
- StateFlow
- Kotlinx Serialization JSON
- Android DataStore Preferences
- Android DocumentFile
- Android FileProvider
- Android WorkManager dependency is present in the build, though the current transfer path is centered on the foreground service

Primary Android modules:

- `app/src/main/java/com/lantransfer/app/ui/`
  - Compose screens, tabs, view models, shared UI state
- `app/src/main/java/com/lantransfer/app/network/`
  - `LanDiscovery.kt`
  - `FramedTransport.kt`
  - `SessionProtocol.kt`
- `app/src/main/java/com/lantransfer/app/transfer/`
  - `TransferEngine.kt`
  - `OutgoingTransferQueue.kt`
  - manifest build and streaming logic
- `app/src/main/java/com/lantransfer/app/service/`
  - foreground receiver service
  - incoming transfer approval bridge
- `app/src/main/java/com/lantransfer/app/crypto/`
  - X25519 and AES-GCM helpers
- `app/src/main/java/com/lantransfer/app/storage/`
  - storage and SAF-backed destination handling
- `app/src/main/java/com/lantransfer/app/settings/`
  - persisted app settings

Android platform features used:

- `INTERNET`
- `ACCESS_WIFI_STATE`
- `CHANGE_WIFI_MULTICAST_STATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `POST_NOTIFICATIONS`
- `READ_MEDIA_IMAGES`
- `READ_MEDIA_VIDEO`
- `READ_MEDIA_VISUAL_USER_SELECTED`
- `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent handling
- Wi-Fi multicast lock for more reliable LAN discovery on Android OEM builds

### Desktop app

Path: `pc_app/`

Core stack:

- Python 3
- PySide6
- Qt Quick / QML
- `cryptography`
- `asyncio`
- Python sockets and datagram transport
- PyInstaller packaging via `HyperDrop.spec`

Primary desktop modules:

- `app/ui_qt/`
  - `backend.py`
  - `qml/Main.qml`
  - QML-facing state bridge
- `app/network/`
  - `discovery.py`
  - `client.py`
  - `server.py`
  - `protocol.py`
  - `transport.py`
- `app/transfer/`
  - `manifest.py`
  - `sender.py`
  - `receiver.py`
  - `queue.py`
  - `storage.py`
  - `resume.py`
- `app/crypto/`
  - key exchange
  - session key derivation
  - chunk encryption
- `app/core/`
  - config, constants, models, logging
- `app/utils/`
  - hashing, path safety, validators

## Networking and transport design

HyperDrop is a LAN-only product.

### Discovery

Discovery is UDP-based and designed to be resilient on typical home and office networks.

Port:

- `54546/udp`

Mechanics:

- broadcast `discover_request`
- direct `discover_response`
- periodic `announce` packets while a receiver is online
- `bye` packets when a device goes offline cleanly
- broadcast to `255.255.255.255`
- broadcast to interface-specific IPv4 broadcast addresses
- stale peer pruning on both platforms

Why this matters:

- discovery does not depend on a single packet making it through
- Android and desktop both actively announce presence while online
- Android uses a multicast lock to avoid common OEM Wi-Fi discovery failures

### Session transport

Transfer sessions run over TCP.

Port:

- `54545/tcp`

Transport properties:

- framed protocol messages
- JSON message bodies
- explicit message types for offer, accept, decline, manifest, chunks, completion, cancel, and resume messages
- request/decision flow before file data is written

### Protocol messages in use

Defined across Android and desktop implementations:

- `hello`
- `pair_request`
- `pair_confirm`
- `auth`
- `key_exchange`
- `transfer_offer`
- `transfer_accept`
- `transfer_decline`
- `manifest`
- `file_chunk`
- `chunk_ack`
- `transfer_complete`
- `transfer_error`
- `cancel`
- `ping`
- `pong`
- `resume_request`
- `resume_response`

The presence of `pair_request` and `pair_confirm` in the protocol list does not mean the product still uses a user-facing pairing-code step. Those message types remain only for transport compatibility in the current codebase.

Note on connection approval:

HyperDrop no longer uses a 6-character pairing-code workflow. The current product flow is direct-send to discovered online devices, followed by receiver-side accept or decline before transfer starts.

## Security model

Security is session-based and local-network scoped.

- Key exchange: X25519
- Session key derivation: shared secret plus transcript-derived session key
- Chunk encryption: AES-GCM
- Transfer data: encrypted per chunk
- Transport scope: same LAN only, no cloud relay

Important clarification:

HyperDrop is private to your local network path, but it is not a substitute for a fully authenticated internet-facing file delivery system. Discovery and transport are designed for trusted local-network environments.

## File transfer model

### Sender side

- Build a selection queue first
- Open a connection to the selected nearby device
- Send `transfer_offer`
- Wait for `transfer_accept` or `transfer_decline`
- Send final manifest
- Stream encrypted file chunks
- Finish with `transfer_complete`

### Receiver side

- Show incoming request inside the Receive screen
- Allow rename and destination review
- Accept or decline before transfer begins
- Create receive session UI only after approval
- Track per-file completion and expose open actions after completion

### Cancellation and decline behavior

The project now treats these as distinct cases.

- Sender closes before approval: receiver shows that the sender closed the session
- Receiver declines: sender gets a rejected state and does not retry automatically
- Explicit cancellation: active session stops and UI updates accordingly

## Actual user-facing features in this repository

- Android share sheet integration into the selection queue
- Multi-file and folder selection
- Selection summary card with edit manager
- Dedicated incoming approval screen
- Dedicated receive progress and finished screens
- Editable showcase device name
- Online toggle on receive screen
- Hidden scrollbars with custom themed layout styling
- Custom desktop and Android visual identity

## Repository structure

```text
MyAPP/
|- android_app/
|  |- app/src/main/java/com/lantransfer/app/
|  |  |- core/
|  |  |- crypto/
|  |  |- data/
|  |  |- network/
|  |  |- service/
|  |  |- settings/
|  |  |- storage/
|  |  |- transfer/
|  |  |- ui/
|  |- app/src/main/res/
|  |- app/src/main/assets/
|
|- pc_app/
|  |- app/
|  |  |- core/
|  |  |- crypto/
|  |  |- network/
|  |  |- transfer/
|  |  |- ui/
|  |  |- ui_qt/
|  |  |- utils/
|  |- assets/
|  |- HyperDrop.spec
|
|- Assest/
|- README.md
|- CONTRIBUTING.md
|- COLLABORATION.md
```

## Build and run

### Android

Requirements:

- Android Studio
- Android SDK for API 29+
- Java 17

Build debug APK:

```bash
cd android_app
./gradlew.bat assembleDebug
```

Compile Kotlin only:

```bash
cd android_app
./gradlew.bat :app:compileDebugKotlin
```

Install on a connected device with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Desktop

Requirements:

- Python 3.11+
- Windows desktop is the primary tested path in this repository

Install dependencies:

```bash
cd pc_app
pip install -r requirements.txt
```

Run the app:

```bash
cd pc_app/app
python .\main.py
```

## External dependencies

### Android dependencies

Declared in `android_app/app/build.gradle.kts`:

- `androidx.core:core-ktx`
- `androidx.lifecycle:lifecycle-runtime-ktx`
- `androidx.activity:activity-compose`
- `androidx.compose.ui:ui`
- `androidx.compose.ui:ui-tooling-preview`
- `androidx.compose.material3:material3`
- `com.google.android.material:material`
- `androidx.datastore:datastore-preferences`
- `androidx.documentfile:documentfile`
- `androidx.work:work-runtime-ktx`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`
- `org.jetbrains.kotlinx:kotlinx-serialization-json`

### Desktop dependencies

Declared in `pc_app/requirements.txt`:

- `cryptography`
- `PySide6`
- `pytest`

### Standard library and platform modules used heavily

Desktop:

- `asyncio`
- `socket`
- `json`
- `subprocess`
- `ipaddress`
- `dataclasses`
- `pathlib`
- `logging`

Android / Kotlin runtime:

- coroutines
- flows / state flows
- Java networking classes such as `Socket`, `ServerSocket`, `DatagramSocket`, `NetworkInterface`
- Android Storage Access Framework APIs

## Troubleshooting

### Devices do not appear in Nearby Devices

Check:

- both devices are on the same LAN
- receiver is online
- UDP `54546` is not blocked by firewall or router isolation
- TCP `54545` is not blocked
- Android app is allowed to run its foreground service
- Android Wi-Fi is not under aggressive battery restrictions

### Desktop can send but Android does not show the request

Check:

- Android receiver is online
- the app foreground service is running
- the current build on both sides matches the same protocol revision

### Android share-to-HyperDrop does not populate selection

Check:

- HyperDrop is installed as the active share target
- you are testing the currently installed package `com.lantransfer.app`
- the source app is sending a standard `ACTION_SEND` or `ACTION_SEND_MULTIPLE` payload

## Status of this repository

This repository is an actively modified product codebase. UI and transfer flow details have changed substantially over time. If you are making changes, read `CONTRIBUTING.md` before changing protocol, discovery, transfer flow, or UI state handling.
