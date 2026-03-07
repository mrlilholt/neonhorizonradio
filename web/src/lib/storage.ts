import type { RadioStation } from '../types'

const FAVORITES_KEY = 'neon-horizon-web-favorites'
const WEATHER_ENABLED_KEY = 'neon-horizon-web-weather-enabled'
const VOLUME_KEY = 'neon-horizon-web-volume'

export function loadFavorites(): RadioStation[] {
  try {
    const raw = window.localStorage.getItem(FAVORITES_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as RadioStation[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveFavorites(favorites: RadioStation[]) {
  window.localStorage.setItem(FAVORITES_KEY, JSON.stringify(favorites))
}

export function loadWeatherEnabled(): boolean {
  const raw = window.localStorage.getItem(WEATHER_ENABLED_KEY)
  return raw === null ? true : raw === 'true'
}

export function saveWeatherEnabled(enabled: boolean) {
  window.localStorage.setItem(WEATHER_ENABLED_KEY, String(enabled))
}

export function loadVolume(): number {
  const raw = window.localStorage.getItem(VOLUME_KEY)
  const parsed = raw ? Number(raw) : 0.9
  return Number.isFinite(parsed) ? Math.min(1, Math.max(0, parsed)) : 0.9
}

export function saveVolume(volume: number) {
  window.localStorage.setItem(VOLUME_KEY, String(volume))
}
