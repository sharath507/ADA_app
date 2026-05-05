# ADA Android Jarvis Assistant (Android + Python Backend)

This repository contains a **working Android “Jarvis-style” assistant** that connects to a **Python FastAPI + Socket.IO backend** (forked/adapted from `ada_v2`).

The phone:

- Streams **microphone PCM audio** to the backend (`POST /voice`)
- Optionally streams **camera frames** to the backend (`POST /vision`)
- Receives **AI-generated audio** back over **Socket.IO** (`audio_data`) and plays it on-device
- Shows an interactive UI with **Jarvis face animation** and assistant state

The backend:

- Hosts FastAPI routes and Socket.IO under a single ASGI app (`asgi_app`)
- Runs the Gemini Live audio session and emits audio + transcription events
- Uses a **Jarvis persona** (“Jarvis”, calls you “Sir”, balanced style)

---

## Project Structure

```
ADAapp/
├── app/                       # Android app module
├── ada_v2/                    # Python backend (FastAPI + Socket.IO + Gemini Live)
│   ├── backend/
│   │   ├── server.py          # FastAPI endpoints + Socket.IO wiring
│   │   └── ada.py             # AudioLoop + Gemini Live config/persona
│   ├── requirements.txt
│   └── .venv/                 # Python venv (recommended)
└── README.md
```

---

## Requirements

### Android

- Android Studio (Giraffe+ recommended)
- A physical Android phone on the **same Wi‑Fi/LAN** as the backend machine

### Backend

- Python 3.10+
- A Gemini API key (`GEMINI_API_KEY`)

---

## Backend Setup (Python)

All backend commands below assume you run from:

```bash
cd /home/sharath/Desktop/ADAapp/ada_v2
```

### 1) Create / use a virtual environment

If you already have `ada_v2/.venv`, use it.

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

### 2) Set the Gemini API key

Create `ada_v2/.env`:

```bash
echo "GEMINI_API_KEY=YOUR_KEY_HERE" > .env
```

Do **not** commit this file.

### 3) Run the backend (IMPORTANT: use `asgi_app`)

```bash
source .venv/bin/activate
python -m uvicorn backend.server:asgi_app --host 0.0.0.0 --port 8000 --reload
```

The backend will be available at:

- `http://<YOUR_LAPTOP_LAN_IP>:8000`

---

## Android App Setup

### 1) Build / Install

Open the repo root (`ADAapp/`) in Android Studio and run the `app` configuration on your phone.

### 2) Configure backend URL in the app

On the phone:

- Open **Settings (Backend URL)**
- Set it to:

```
http://<YOUR_LAPTOP_LAN_IP>:8000
```

Example:

```
http://10.219.60.45:8000
```

### 3) Grant permissions

The app requires:

- Microphone
- Camera (for Vision)
- Overlay permission (optional overlay UI)

### 4) Start the assistant

- Tap **Start Assistant Service**
- You should see:
  - Socket.IO connect
  - Transcriptions appear
  - Jarvis face animates while speaking

---

## Vision Streaming (Camera)

To stream camera frames:

- Tap **Vision**
- Tap **Start Vision Streaming**

The app will upload JPEG frames periodically to:

- `POST /vision`

---

## Endpoints / Protocol

### HTTP

- `POST /start` – initializes the assistant session (Android mode)
- `POST /voice` – mic PCM bytes (16-bit mono)
- `POST /vision` – JPEG bytes

### Socket.IO

- Path: `/socket.io`
- Events:
  - `audio_data` – assistant PCM audio bytes (plays on phone)
  - `transcription` – `{ sender, text }`

---

## Audio Feedback Loop Fix (important)

If you notice the assistant continuously responding to itself, that’s **speaker → mic echo feedback**.

This project mitigates it on Android via:

- Half‑duplex: mic streaming pauses while assistant audio plays
- `AudioRecord` uses `VOICE_COMMUNICATION`
- `AcousticEchoCanceler` + `NoiseSuppressor` enabled when available

---

## Troubleshooting

### Socket.IO 404

Make sure you run:

```bash
python -m uvicorn backend.server:asgi_app --host 0.0.0.0 --port 8000
```

(not `backend.server:app`).

### Android can’t reach backend

- Ensure phone and laptop are on the same Wi‑Fi
- Use your laptop LAN IP (not `localhost`, not `127.0.0.1`)
- Ensure the app is allowed to use cleartext HTTP (already configured)

### Useful log commands

Android:

```bash
adb logcat | grep -i -E "AssistantService|PcmPlayer|MicStreamer|AndroidRuntime"
```

Backend:

Watch for:

- `/start` 200
- `/voice` 200
- `/socket.io` connect

---

## Notes

- The backend includes a **Jarvis persona** via Gemini Live `system_instruction`.
- This repo focuses on the Android client + Python backend integration (desktop Electron UI from upstream is not required for phone use).
