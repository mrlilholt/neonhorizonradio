# AGENTS.md: Operational Manual for Neon Horizon Radio

## 1. TL;DR Core Transformation
Transform the utility of radio into an immersive Vaporware experience. The app must look like a 1980s retro-futuristic dream while providing stable streaming on the go.

## 2. Coding Standards
- **Language:** Strict Kotlin only.
- **UI:** Jetpack Compose for everything except the mandatory Media3 service components.
- **Naming:** Follow `action_subject_type` (e.g., `get_station_list.kt`, `save_favorite_usecase.kt`).
- **Dependency Injection:** Use Hilt for dependency management.
- **Error Handling:** Use `Result` wrappers for API calls to prevent UI crashes during hiking (low signal).

## 3. Theme Engine Protocol
- All UI components must pull colors from the `VaporThemeProvider`.
- Backgrounds are stored in `res/drawable/bg_themes_...`.
- The `ThemeEngine` must broadcast changes to the `MediaSession` so the Car UI updates its metadata/background extras.

## 4. V2 Roadmap (Do Not Build Now)
- Deep search (Instrument, Country).
- Custom "Glitch" transitions between stations.
- Integration with local MP3 files for "Vapor-Mixes."

## 5. The "Stop" Command
If a requested feature deviates from the `techSpec.md` scope (e.g., adding a social login), **STOP** and ask the user for a Spec Update.

## 6. UI Freeze Rule
- The **mini-player layout is locked/frozen** and must not be modified unless the user explicitly asks to reopen that scope.
- The **maximized player layout is locked/frozen** and must not be modified unless the user explicitly asks to reopen that scope.
- The **Player tab UI/flow is locked/frozen** and must not be modified without explicit user approval.
- The **Stations tab UI/flow is locked/frozen** and must not be modified without explicit user approval.
- The **Favorites tab UI/flow is locked/frozen** and must not be modified without explicit user approval.
- The **genre list and ordering are locked/frozen** and must not be modified without explicit user approval.
