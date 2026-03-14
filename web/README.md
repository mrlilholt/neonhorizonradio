# Neon Horizon Radio - Web

The web-based counterpart to the Neon Horizon Radio Android app. Built with React, Vite, and TypeScript.

## Features

- **Radio Streaming**: Access to curated Vaporwave stations.
- **Visuals**: Retro-futuristic UI with animated backgrounds.
- **Favorites**: Sync favorites across devices using Firebase Auth.
- **Sharing**: Direct station links (e.g., `/station/{uuid}`).
- **PWA Capable**: Installable on supported devices.

## Development

### Prerequisites

- Node.js (v18+)
- npm

### Setup

1.  Install dependencies:

    ```bash
    npm install
    ```

2.  Start the development server:

    ```bash
    npm run dev
    ```

3.  Build for production:

    ```bash
    npm run build
    ```

4.  Preview the production build:

    ```bash
    npm run preview
    ```

## Environment Variables

Create a `.env` file in the `web/` directory (copy from `.env.example` if available) with your Firebase configuration:

```env
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=...
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
```

## Deployment

The project is configured for deployment on Netlify. See `netlify.toml` in the root directory.

## Project Structure

- `src/components`: React components (Player, Visuals, etc.)
- `src/lib`: Utilities (API, Firebase, Audio logic)
- `src/types.ts`: TypeScript definitions
