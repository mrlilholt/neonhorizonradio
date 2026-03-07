import { getApp, getApps, initializeApp, type FirebaseApp } from 'firebase/app'
import {
  GoogleAuthProvider,
  browserLocalPersistence,
  getAuth,
  setPersistence,
  signInWithPopup,
  signInWithRedirect,
  signOut
} from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? '',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? '',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? '',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? '',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
  appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '',
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID ?? ''
}

const requiredConfigValues = [
  firebaseConfig.apiKey,
  firebaseConfig.authDomain,
  firebaseConfig.projectId,
  firebaseConfig.appId
]

export const isFirebaseConfigured = requiredConfigValues.every((value) => value.trim().length > 0)

export const firebaseApp: FirebaseApp | null = isFirebaseConfigured
  ? getApps().length > 0
    ? getApp()
    : initializeApp(firebaseConfig)
  : null

export const firebaseAuth = firebaseApp ? getAuth(firebaseApp) : null
export const firestore = firebaseApp ? getFirestore(firebaseApp) : null

const googleProvider = new GoogleAuthProvider()
googleProvider.setCustomParameters({ prompt: 'select_account' })

let persistenceInitialized = false

async function ensureAuthPersistence() {
  if (!firebaseAuth || persistenceInitialized) return
  await setPersistence(firebaseAuth, browserLocalPersistence)
  persistenceInitialized = true
}

function shouldFallbackToRedirect(error: unknown) {
  if (!error || typeof error !== 'object' || !('code' in error)) {
    return false
  }

  const code = String(error.code)
  return (
    code === 'auth/popup-blocked' ||
    code === 'auth/web-storage-unsupported' ||
    code === 'auth/operation-not-supported-in-this-environment'
  )
}

export async function signInWithGoogle() {
  if (!firebaseAuth) {
    throw new Error('Firebase web auth is not configured.')
  }

  await ensureAuthPersistence()

  try {
    await signInWithPopup(firebaseAuth, googleProvider)
  } catch (error) {
    if (shouldFallbackToRedirect(error)) {
      await signInWithRedirect(firebaseAuth, googleProvider)
      return
    }

    throw error
  }
}

export async function signOutFromGoogle() {
  if (!firebaseAuth) {
    throw new Error('Firebase web auth is not configured.')
  }

  await signOut(firebaseAuth)
}
