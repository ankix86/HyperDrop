# 🚀 HyperDrop

**Fast, encrypted, zero-internet file transfer between your Android phone and Windows/Linux PC — over your local Wi-Fi.**

No cloud. No cables. No account. Just drop files instantly across your LAN.

---

## ✨ Features

- 📁 **Send files & folders** — transfer any file type, including entire folder trees
- 🔒 **End-to-end encrypted** — X25519 key exchange + AES-GCM encryption on every chunk
- 📡 **Auto-discovery** — finds devices on your network automatically via UDP broadcast, no IP typing needed
- 🤝 **One-time pairing** — pair devices with a 6-character code; trusted devices connect instantly after that
- ⚡ **Share sheet integration** — share images directly from your Android gallery to your PC in one tap
- 🔔 **Background receiving** — Android receives files via a foreground service even when the app is minimised
- 🔄 **Transfer queue** — queue multiple transfers; they run one after another automatically

---

## 📐 How It Works

```
Android App  ──────────────────────────────────  PC App (Python)
     │                                                 │
     │  1. UDP broadcast (port 54546) — discovery      │
     │◄────────────────────────────────────────────────│
     │                                                 │
     │  2. TCP connection (port 54545)                 │
     │────────────────────────────────────────────────►│
     │                                                 │
     │  3. Pairing handshake (6-char code)             │
     │◄───────────────────────────────────────────────►│
     │                                                 │
     │  4. X25519 key exchange → AES-GCM session key  │
     │◄───────────────────────────────────────────────►│
     │                                                 │
     │  5. Encrypted file chunks transferred           │
     │────────────────────────────────────────────────►│
```

Both devices must be on the **same Wi-Fi network**.

---

## 📦 Project Structure

```
HyperDrop/
├── android_app/        # Kotlin + Jetpack Compose Android app
│   └── app/src/main/
│       ├── network/    # UDP discovery, TCP protocol, framed transport
│       ├── transfer/   # TransferEngine, manifest builder, queue
│       ├── crypto/     # X25519 key exchange, AES-GCM encryption
│       ├── ui/         # Compose screens & ViewModel
│       └── service/    # Foreground service for background receiving
│
└── pc_app/             # Python + PySide6 (QML UI) PC app
    └── app/
        ├── core/       # Config, constants, data models
        ├── network/    # UDP discovery, TCP server & client
        ├── transfer/   # Sender, receiver, manifest, queue
        ├── crypto/     # Key exchange, AES-GCM encryptor
        ├── ui_qt/      # QML UI + Python backend bridge
        └── utils/      # Hashing, path validation, helpers
```

---

## 🖥️ PC App — Setup

### Requirements
- Python 3.11+
- Windows or Linux

### Install & Run

```bash
# 1. Navigate to the PC app folder
cd pc_app

# 2. Install dependencies
pip install -r requirements.txt

# 3. Run the app
python -m app.main
```

### Or use the pre-built executable
Double-click **`LanTransferPC.exe`** inside `pc_app/` — no Python installation needed.

---

## 📱 Android App — Setup

### Requirements
- Android 10 (API 29) or higher

### Install
1. Open `android_app/` in **Android Studio**
2. Connect your phone or start an emulator
3. Click **Run ▶**

> Make sure your phone and PC are on the **same Wi-Fi network**.

---

## 🔄 Usage Workflow

### Step 1 — Start the PC app
Launch the PC app. It starts listening automatically and begins broadcasting its presence on the network.

### Step 2 — Open the Android app
The app scans for nearby devices. Your PC should appear in the device list within a few seconds.

### Step 3 — Pair (first time only)
1. On PC: note the **pairing code** shown in the app (or generate a new one)
2. On Android: tap your PC in the device list, enter the same pairing code
3. Done — the devices are now trusted and will connect automatically in the future

### Step 4 — Transfer files

**Android → PC (send from phone):**
- Open the app → tap **Send Files** → pick files or a folder
- *Or* share images directly from your gallery using the Android share sheet

**PC → Android (send from computer):**
- Select the Android device in the PC app
- Click **Send Files** or **Send Folder** and pick what to transfer

### Step 5 — Receive files
- **On PC:** received files land in the configured receive folder (default: `~/.lan_transfer_mvp/received/`)
- **On Android:** received files go to the folder you selected in settings

---

## ⚙️ Configuration

### PC App
| Setting | Default | Description |
|---|---|---|
| Port | `54545` | TCP port for file transfers |
| Receive folder | `~/.lan_transfer_mvp/received/` | Where incoming files are saved |
| Pairing code | `123456` | Change this before pairing |
| Auto-start server | `on` | Server starts when app opens |

### Android App
Settings are accessible from the main screen:
- **Target PC** — selected by tapping a discovered device
- **Pairing code** — must match the code shown on the PC
- **Receive folder** — choose any folder via the Android folder picker

---

## 🔐 Security

| Layer | Mechanism |
|---|---|
| Key exchange | X25519 ECDH (ephemeral per-session) |
| Encryption | AES-256-GCM per chunk |
| Authentication | 6-character pairing code + SHA-256 hash stored |
| Trust | First-pair-then-trust; known devices skip re-pairing |

All traffic stays on your **local network** — nothing is sent to the internet.

---

## 🛠️ Default Ports

| Port | Protocol | Purpose |
|---|---|---|
| `54546` | UDP | Device discovery (broadcast) |
| `54545` | TCP | File transfer |

Make sure these ports are **allowed through your firewall** if devices can't find each other.
