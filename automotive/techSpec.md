# Technical Specification: Neon Horizon Radio (MVP)
**Project Title:** Neon Horizon Radio  
**Platform:** Android (Mobile + Auto)  
**Min SDK:** API 28 (Android 9.0)  
**Language:** Kotlin  
**Build System:** Kotlin DSL (build.gradle.kts)

## 1. Executive Summary
A specialized radio streaming application designed to deliver a "Vaporwave" aesthetic to car head units. It bridges high-fidelity streaming with a curated visual atmosphere.

## 2. Scope & Goals
- **In-Scope:** Radio streaming (API), Genre-based scanning, Favorites, Vaporwave Theme Engine (Mobile + Auto metadata), Voice Commands.
- **Out-of-Scope (V2):** User-submitted stations, Instrument-specific search, Sleep timer, Social sharing.

## 3. Technical Architecture
### Tech Stack
- **UI:** Jetpack Compose (Mobile), Media3 MediaSession (Auto).
- **Audio:** Media3 ExoPlayer.
- **Networking:** Retrofit 2 + Moshi (JSON parsing).
- **Persistence:** Room Database (Favorites).
- **Architecture:** MVVM + Clean Architecture (Use Cases for playback).

### Theme Engine Schema
Each theme is a `VaporTheme` object:
- `id: String`
- `name: String` (e.g., "Crystal Pepsi 1992")
- `backgroundRes: Int`
- `neonColorPrimary: Color`
- `neonColorSecondary: Color`
- `scanlineOpacity: Float`

## 4. Functional Requirements
- **FR1:** Stream audio from `radio-browser.info` API.
- **FR2:** Display station name, current song (metadata), and album art.
- **FR3:** Voice search via Android Auto: "Neon Radio play Lofi".
- **FR4:** Skip button scans to next station in the same genre category.
- **FR5:** Persistent "Favorites" library.

## 5. Directory Map (Machine-Optimized)
- `src/main/java/com/neonhorizon/features/player`: ExoPlayer & MediaSession logic.
- `src/main/java/com/neonhorizon/features/radio`: API repository & RadioBrowser integration.
- `src/main/java/com/neonhorizon/features/theme`: Vaporwave Theme Provider & Theme definitions.
- `src/main/java/com/neonhorizon/data/db`: Room Database & DAOs for Favorites.

## 6. Decision Log
- **2026-02-27:** Architecture locked to Media3 to ensure Android Auto 1st-party support.
- **2026-02-27:** Theme Engine prioritized as a core "Transformation" feature.
- **2026-02-27:** Project modules standardized on Kotlin DSL (`build.gradle.kts`) and Min SDK 28.
- **2026-02-27:** Media session baseline implemented with `MediaSessionService` + `ExoPlayer` preload for Lofi stations.
- **2026-02-27:** First Vaporwave theme `"Sunset Horizon"` shipped with pink/cyan palette and session extras propagation.
- **2026-02-27:** Upgraded playback service to `MediaLibraryService` with library root, Lofi browse tree, and search callbacks for Android Auto voice flows.
- **2026-02-27:** Mobile radio feature screen now loads Lofi stations, lists results, and controls playback via `MediaController` (play/pause/next/station select).
- **2026-02-27:** Fixed Radio Browser converter setup (Moshi Kotlin adapter + lenient parsing) and expanded player UI to a detailed retro console with diagnostics and full transport controls.
- **2026-02-27:** Added branded Neon Horizon assets (logo + dual-size car background) and applied them across mobile UI, launcher icons, and Media3 metadata artwork.
- **2026-02-27:** Set new default background pack with qualifiers: `ultraTall` portrait for mobile, `standard widescreen` for landscape Auto, and `ultraWide` for large Auto displays.
- **2026-02-27:** Fixed startup playback crash by including Media3 HLS module (`media3-exoplayer-hls`) required for Radio Browser stream formats.
- **2026-02-27:** Refactored mobile UX to player-first home screen with bottom mode switcher (`Player / Stations / Favorites`) and moved diagnostics into transient loading modal behavior.
- **2026-02-27:** Completed Room-backed favorites flow in mobile ViewModel/UI (`save/remove`, dedicated Favorites folder tab, and favorite quick action from player/station rows).
- **2026-02-27:** Updated playback controls so `Next`/`Previous` force autoplay on station transition and diagnostics auto-dismiss once stream is ready/playing.
- **2026-02-27:** Replaced theme background resource pack with latest `*DefaultBackground.png` assets from `Downloads` (mobile ultraTall + auto widescreen/ultrawide defaults).
- **2026-02-27:** Upgraded mobile UI to an icon-driven retro desktop player aesthetic (Win95-inspired window chrome), converted transport/favorite/station controls to icons, and moved theme controls into a dedicated bottom `Theme` tab.
- **2026-02-27:** Completed pixel-tuning pass: bottom-anchored player layout, transport-row status badge placement, widened primary play control, working maximize/restore window control, thin Win95-style taskbar height, and scrolling station marquee with station ID.
- **2026-02-27:** Added station cover rendering using Radio Browser favicon metadata (`coil-compose` in mobile, `artworkUri` on `MediaItem` metadata) with logo fallback.
- **2026-02-27:** Added metadata-driven oldschool station search UX with top search bar (name/tag/country/language), plus fast genre switching in both Player and Stations tabs.
- **2026-02-27:** Refined player details: removed duplicate oversized title block, restored favorite action in transport row, moved controls below expanded cover in maximized mode, simplified marquee content, and switched taskbar clock to live 24-hour format (`HH:mm`).
- **2026-02-28:** Hardened Android Auto browse reliability by returning genre children immediately (async refresh), reducing metadata payload size (`artworkUri` over raw artwork bytes), and building selected-station queues so car `Next/Previous` can traverse the current genre list.
- **2026-02-28:** Added service-level Firebase favorites bootstrap so Android Auto loads Firestore favorites on service startup/auth state changes, independent of whether the mobile UI has been opened.
- **2026-03-07:** Spec updated by user approval to add mobile-only station sharing via custom deep links (`neonhorizon://station/{uuid}`), with player share action and Drive-folder install fallback text while Play Store distribution is pending.

## 7. Implementation Plan
- **P1 (Done):** Initialize modular feature map in `shared/src/main/java/com/neonhorizon/{features,data}`.
- **P2 (Done):** Integrate Radio Browser API (`Retrofit + Moshi`) and add `GetLofiStationListUseCase`.
- **P3 (Done):** Replace legacy compat service with Media3 `NeonMediaSessionService` for Auto/mobile baseline.
- **P4 (Done):** Implement Vaporwave Theme Engine (`VaporThemeProvider`, `ThemeEngine`, `Sunset Horizon`).
- **P5 (Done):** Add browse tree + search callbacks for Android Auto voice-driven navigation.
- **P6 (Done):** Connect Room favorites flow into player queue and mobile UI controls.
- **P7 (Done):** Build mobile Lofi station list + playback controls bound to Media3 session.
- **P8 (Done):** Add robust playback control surface (prev/play-pause/next/stop), playback logs, and station diagnostics.
- **P9 (Done):** Integrate brand assets across app shells and automotive-oriented background variants.
- **P10 (Done):** Ship player-first mobile navigation model with bottom toggle and dedicated Favorites folder access.
- **P11 (Done):** Replace text-heavy controls with icon controls and restyle player/taskbar to retro desktop-inspired UI, including dedicated `Theme` bottom tab.
- **P12 (Done):** Pixel-tune retro player UI and wire maximize/artwork/marquee behaviors for closer screenshot fidelity.
- **P13 (Done):** Implement metadata search + genre filter workflow and finalize expanded player composition/clock format refinements.
- **P14 (Done):** Stabilize Android Auto browse/playback behavior for in-car hosts (reliability + queue continuity) without changing mobile UI.
- **P15 (Done):** Ensure favorites persistence is available to Android Auto at boot by syncing Firestore favorites into local Room from `NeonMediaSessionService`.
- **P16 (Done):** Add mobile-only exact-station sharing with inbound deep-link resolution, player share action, and fallback install link in shared text payloads.

@AI_AGENT: This document is iterative. You are required to update the Implementation Plan and Decision Log as you execute tasks.
