# Neon Horizon Radio

A cross-platform Vaporwave radio experience for Android (Mobile + Auto) and Web.

## Project Structure

This monorepo contains the following components:

- **`mobile/`**: Android Mobile application (Jetpack Compose).
- **`automotive/`**: Android Automotive OS application (MediaSession, Car App Library).
- **`web/`**: Web application (React, Vite, TypeScript).
- **`shared/`**: Shared Android logic and resources.

## Getting Started

### Prerequisites

- **Android Studio** (Koala or later recommended)
- **Node.js** (v18 or later) for the Web component
- **JDK 17**

### Building the Android App

Open the project in Android Studio and sync Gradle. You can run the `:mobile` or `:automotive` configurations.

### Running the Web App

Navigate to the `web/` directory and install dependencies:

```bash
cd web
npm install
npm run dev
```

The web app will be available at `http://localhost:5173`.

## Documentation

- [Automotive Agents & Operational Manual](AGENTS.md)
- [Automotive Technical Specification](automotive/techSpec.md)
- [Web Documentation](web/README.md)

## License

[License Type] - See LICENSE file for details.
