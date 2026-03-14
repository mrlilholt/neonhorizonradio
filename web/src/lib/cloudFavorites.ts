import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  onSnapshot,
  setDoc,
  type Unsubscribe
} from 'firebase/firestore'
import { firestore } from './firebase'
import type { RadioStation } from '../types'

const FIRESTORE_USERS_COLLECTION = 'users'
const FIRESTORE_FAVORITES_COLLECTION = 'favorites'
const FIRESTORE_USER_FAVORITES_COLLECTION = 'user_favorites'

function buildMirrorDocId(userId: string, stationId: string) {
  return `${userId}_${stationId.replace(/\//g, '_')}`
}

function mapFavoriteDocument(input: Record<string, unknown>, fallbackId: string): RadioStation | null {
  const stationId =
    (typeof input.stationId === 'string' && input.stationId.trim()) ||
    fallbackId.trim()

  const name = typeof input.name === 'string' ? input.name.trim() : ''
  const streamUrl = typeof input.streamUrl === 'string' ? input.streamUrl.trim() : ''

  if (!stationId || !name || !streamUrl) {
    return null
  }

  return {
    id: stationId,
    name,
    streamUrl,
    homepage: '',
    favicon: typeof input.favicon === 'string' ? input.favicon : '',
    tags: typeof input.tags === 'string' ? input.tags : '',
    country: typeof input.country === 'string' ? input.country : ''
  }
}

function buildFavoritePayload(userId: string, station: RadioStation) {
  const now = Date.now()

  return {
    stationId: station.id,
    name: station.name,
    streamUrl: station.streamUrl,
    favicon: station.favicon,
    tags: station.tags,
    country: station.country,
    savedAtEpochMillis: now,
    updatedAtEpochMillis: now,
    userId
  }
}

export function mergeFavorites(localFavorites: RadioStation[], cloudFavorites: RadioStation[]) {
  const merged = new Map<string, RadioStation>()

  ;[...localFavorites, ...cloudFavorites].forEach((station) => {
    merged.set(station.id, station)
  })

  return Array.from(merged.values())
}

export async function loadCloudFavorites(userId: string) {
  if (!firestore) {
    throw new Error('Firebase web auth is not configured.')
  }

  const snapshot = await getDocs(
    collection(
      firestore,
      FIRESTORE_USERS_COLLECTION,
      userId,
      FIRESTORE_FAVORITES_COLLECTION
    )
  )

  return snapshot.docs
    .map((documentSnapshot) =>
      mapFavoriteDocument(
        documentSnapshot.data() as Record<string, unknown>,
        documentSnapshot.id
      )
    )
    .filter((station): station is RadioStation => station !== null)
}

export function subscribeToCloudFavorites(
  userId: string,
  onFavoritesChanged: (favorites: RadioStation[]) => void
): Unsubscribe {
  if (!firestore) {
    throw new Error('Firebase web auth is not configured.')
  }

  return onSnapshot(
    collection(
      firestore,
      FIRESTORE_USERS_COLLECTION,
      userId,
      FIRESTORE_FAVORITES_COLLECTION
    ),
    (snapshot) => {
      const favorites = snapshot.docs
        .map((documentSnapshot) =>
          mapFavoriteDocument(
            documentSnapshot.data() as Record<string, unknown>,
            documentSnapshot.id
          )
        )
        .filter((station): station is RadioStation => station !== null)

      onFavoritesChanged(favorites)
    }
  )
}

export async function upsertCloudFavorite(userId: string, station: RadioStation) {
  if (!firestore) {
    throw new Error('Firebase web auth is not configured.')
  }

  const payload = buildFavoritePayload(userId, station)
  const mirrorId = buildMirrorDocId(userId, station.id)

  await Promise.all([
    setDoc(
      doc(
        firestore,
        FIRESTORE_USERS_COLLECTION,
        userId,
        FIRESTORE_FAVORITES_COLLECTION,
        station.id
      ),
      payload,
      { merge: true }
    ),
    setDoc(doc(firestore, FIRESTORE_USER_FAVORITES_COLLECTION, mirrorId), payload, {
      merge: true
    })
  ])
}

export async function removeCloudFavorite(userId: string, stationId: string) {
  if (!firestore) {
    throw new Error('Firebase web auth is not configured.')
  }

  const mirrorId = buildMirrorDocId(userId, stationId)

  await Promise.allSettled([
    deleteDoc(
      doc(
        firestore,
        FIRESTORE_USERS_COLLECTION,
        userId,
        FIRESTORE_FAVORITES_COLLECTION,
        stationId
      )
    ),
    deleteDoc(doc(firestore, FIRESTORE_USER_FAVORITES_COLLECTION, mirrorId))
  ])
}

export async function syncMergedFavoritesToCloud(userId: string, favorites: RadioStation[]) {
  await Promise.allSettled(
    favorites.map((station) => upsertCloudFavorite(userId, station))
  )
}
