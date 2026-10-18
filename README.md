# OnePageClock ⏱️

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)](https://developer.android.com/jetpack/compose)

A sleek, unified Android time management suite built with **Jetpack Compose**. **OnePageClock** brings timers, world clocks, exact alarms, and a millisecond stopwatch into a single, cohesive, dark-themed dashboard—eliminating tab-switching friction while respecting battery and user privacy.

---

## ✨ Features

### ⏳ 1. Gestural Timer & Favorite Presets
- **Multi-Modal Input**:
  - Horizontal & vertical touch scrubbing on Hours (0–24) and Minutes/Seconds (0–60).
  - One-tap discrete step buttons (`+15m`, `+1m`, `-15m`, `-1m`, `±1h`).
  - Direct numeric keyboard input with decimal seconds support (`MIN.SEC`).
- **Quick-Clear Badges**: Integrated red cancel badges at the top-right of input fields to clear numbers and reset focus instantly.
- **Dynamic Favorite Presets**: Toggle the star icon to save or remove durations from an 8-slot quick-start grid.
- **Persistent Background Service**: Keeps ticking accurately in the background using a foreground service with rich notification controls (Pause, Resume, Stop).

### 🌍 2. World Clock & Interactive 3-Mode City Finder
- **Reorderable Cards**: Drag and reorder time zones horizontally with haptic feedback.
- **Default Setup**: Quick glance at major international financial centers (**Tokyo, Paris, London, New York**).
- **Interactive 3-Way City Finder**:
  1. **By Name**: Fast filterable search through all IANA time zones worldwide.
  2. **By Coordinates**: Enter latitude and longitude to automatically resolve the nearest metropolis and exact solar UTC offset.
  3. **Interactive World Map**: Visual canvas map featuring:
     - 24 standard 15° meridian time zone bands with dynamic crosshair tracking.
     - 80+ major world capitals and global metropolitan hubs.
     - Globally standard formatted output: `[City] • UTC±HH:mm • [Local Time]`.

### ⏰ 3. Exact Multi-Alarm
- Schedule multiple recurring or one-off alarms (`Once`, `Daily`, `Mon-Fri`).
- Reliable wakeup scheduling via Android `AlarmManager` exact alarms.
- Next-alarm countdown summary banner directly on the main dashboard.
- Custom alarm audio selection from device storage or system ringtones.

### ⏱️ 4. Precision Stopwatch
- Millisecond-accurate stopwatch ticker with persistent elapsed timing.
- One-tap lap recording with scrollable lap history.

### 🛡️ 5. Privacy & Offline First
- **Zero Location Tracking**: No GPS permissions required.
- **Neutral Defaults**: Initial coordinates and map crosshairs default neutrally to the Prime Meridian (Greenwich / London).
- **Local Persistence**: All alarms, presets, and world clocks are stored strictly on-device using local SharedPreferences.

---

## 📱 Screenshots & Visual Design

The UI is built with a custom cyberpunk-minimalist theme:
- **Background**: Deep void black (`#090A0F`)
- **Card Surfaces**: Dark elevated indigo-slate (`#14161F` / `#1E2230`)
- **Accents**: Neon Cyan (`#00E5FF`), Mint (`#64FFDA`), Gold (`#FFD54F`), Coral (`#FF5252`)
- **Adaptive Vector Launcher Icon**: Custom paper-and-clock vector with smooth rounded borders.

---

## 🛠️ Tech Stack & Requirements

- **Language**: Kotlin 2.x
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 16 (API 36)
- **Architecture**: Event-driven unidirectional state flow with coroutines and background services.

---

## 🚀 Building from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Neo-DannyDeTour/OnePageClock.git
   cd OnePageClock
   ```

2. **Build the debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on a connected Android device or emulator**:
   ```bash
   ./gradlew installDebug
   ```
   *Or install directly with ADB:*
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 📄 License

This project is open-source software licensed under the **[MIT License](LICENSE)**.

```
Copyright (c) 2026 UnrealDanny
```
