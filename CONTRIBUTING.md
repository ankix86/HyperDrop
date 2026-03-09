# Contributing to HyperDrop

Thanks for taking the time to contribute! 🎉

---

## 📋 Table of Contents

- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Development Setup](#development-setup)
- [Making Changes](#making-changes)
- [Code Style](#code-style)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)

---

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/your-username/HyperDrop.git
   cd HyperDrop
   ```
3. Create a **feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

---

## Project Structure

```
MyAPP/
├── android_app/   # Kotlin + Jetpack Compose (Android)
├── pc_app/        # Python + PySide6/QML (Windows/Linux)
├── Assest/        # Shared icons and sound effects
├── README.md
└── CONTRIBUTING.md
```

Each side of the app mirrors the same protocol — changes to the protocol or crypto layer must be reflected in **both** `android_app/` and `pc_app/`.

---

## Development Setup

### PC App

```bash
cd pc_app
pip install -r requirements.txt
python -m app.main
```

Run tests:
```bash
pytest
```

### Android App

- Open `android_app/` in **Android Studio** (Ladybug or newer)
- Sync Gradle, then run on a device or emulator (API 29+)

---

## Making Changes

### Protocol changes
The wire protocol is defined in:
- PC: `pc_app/app/network/protocol.py`
- Android: `android_app/app/src/main/java/com/lantransfer/app/network/SessionProtocol.kt`

Any change to message types or the handshake sequence **must** be updated in both files to keep interoperability.

### Crypto changes
- PC: `pc_app/app/crypto/`
- Android: `android_app/app/src/main/java/com/lantransfer/app/crypto/`

Do not change the encryption scheme (X25519 + AES-GCM) without a corresponding update on both sides and a clear explanation in the PR.

### UI changes
- PC UI is in QML: `pc_app/app/ui_qt/qml/`
- Android UI is Jetpack Compose: `android_app/.../ui/`

---

## Code Style

### Python (PC app)
- Follow [PEP 8](https://pep8.org/)
- Use type hints (`from __future__ import annotations`)
- Keep functions small and focused
- No single-letter variable names outside of short loops

### Kotlin (Android app)
- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use coroutines and `StateFlow` for async/reactive patterns
- Prefer `data class` for simple models

---

## Submitting a Pull Request

1. Make sure your branch is up to date with `main`:
   ```bash
   git fetch origin
   git rebase origin/main
   ```
2. Run all tests before submitting
3. Keep PRs **focused** — one feature or fix per PR
4. Write a clear PR description:
   - **What** the change does
   - **Why** it's needed
   - Any **Breaking changes**
5. Reference related issues with `Closes #issue-number`

---

## Reporting Issues

When filing a bug, please include:

- **OS and version** (e.g. Windows 11, Android 14)
- **App version / commit hash**
- **Steps to reproduce** the issue
- **Expected vs actual behaviour**
- Any relevant **logs** (check `~/.lan_transfer_mvp/app.log` on PC)

---

## Questions?

Open a [GitHub Discussion](../../discussions) for general questions or ideas. Use [Issues](../../issues) for confirmed bugs and feature requests.
