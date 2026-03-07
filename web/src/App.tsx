import { useEffect, useMemo, useRef, useState } from 'react'
import { Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import { onAuthStateChanged, type User } from 'firebase/auth'
import { DEFAULT_GENRES, SHARE_FALLBACK_URL } from './lib/constants'
import { getStationById, searchStations } from './lib/api'
import {
  loadCloudFavorites,
  mergeFavorites,
  removeCloudFavorite,
  syncMergedFavoritesToCloud,
  upsertCloudFavorite
} from './lib/cloudFavorites'
import { firebaseAuth, isFirebaseConfigured, signInWithGoogle, signOutFromGoogle } from './lib/firebase'
import { getLocalForecast } from './lib/weather'
import { loadFavorites, loadVolume, loadWeatherEnabled, saveFavorites, saveVolume, saveWeatherEnabled } from './lib/storage'
import type { LocalWeatherForecast, MainTab, RadioStation } from './types'

function App() {
  return (
    <Routes>
      <Route path="/" element={<RadioRoute />} />
      <Route path="/station/:stationId" element={<RadioRoute />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function RadioRoute() {
  const { stationId } = useParams()
  const navigate = useNavigate()
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const favoritesRef = useRef<RadioStation[]>(loadFavorites())
  const playerWindowRef = useRef<HTMLElement | null>(null)
  const stationsWindowRef = useRef<HTMLElement | null>(null)
  const favoritesWindowRef = useRef<HTMLElement | null>(null)

  const [tab, setTab] = useState<MainTab>('player')
  const [stations, setStations] = useState<RadioStation[]>([])
  const [favorites, setFavorites] = useState<RadioStation[]>(() => loadFavorites())
  const [selectedGenre, setSelectedGenre] = useState<string>('Lofi')
  const [searchQuery, setSearchQuery] = useState('')
  const [queueLabel, setQueueLabel] = useState('Queue: Genre Lofi')
  const [activeStation, setActiveStation] = useState<RadioStation | null>(null)
  const [currentQueue, setCurrentQueue] = useState<RadioStation[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isPlaying, setIsPlaying] = useState(false)
  const [playbackStatus, setPlaybackStatus] = useState('Ready')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [volume, setVolume] = useState(() => loadVolume())
  const [isWeatherEnabled, setIsWeatherEnabled] = useState(() => loadWeatherEnabled())
  const [weather, setWeather] = useState<LocalWeatherForecast | null>(null)
  const [weatherMessage, setWeatherMessage] = useState<string>('Waiting for local forecast.')
  const [showSettings, setShowSettings] = useState(false)
  const [currentUser, setCurrentUser] = useState<User | null>(null)
  const [authStatusMessage, setAuthStatusMessage] = useState(
    isFirebaseConfigured
      ? 'Signed out. Using local favorites.'
      : 'Firebase web auth is not configured yet.'
  )
  const [isAuthBusy, setIsAuthBusy] = useState(false)

  const favoriteIds = useMemo(() => new Set(favorites.map((station) => station.id)), [favorites])

  useEffect(() => {
    const audio = new Audio()
    audio.preload = 'none'
    audio.volume = volume
    audioRef.current = audio

    const syncPlaying = () => {
      setIsPlaying(!audio.paused)
      setPlaybackStatus(audio.paused ? 'Ready' : 'Playing')
    }
    const onWaiting = () => setPlaybackStatus('Buffering')
    const onEnded = () => setPlaybackStatus('Ended')
    const onError = () => {
      setPlaybackStatus('Error')
      playRelative(1)
    }

    audio.addEventListener('playing', syncPlaying)
    audio.addEventListener('pause', syncPlaying)
    audio.addEventListener('waiting', onWaiting)
    audio.addEventListener('ended', onEnded)
    audio.addEventListener('error', onError)

    return () => {
      audio.pause()
      audio.src = ''
      audio.removeEventListener('playing', syncPlaying)
      audio.removeEventListener('pause', syncPlaying)
      audio.removeEventListener('waiting', onWaiting)
      audio.removeEventListener('ended', onEnded)
      audio.removeEventListener('error', onError)
    }
  }, [])

  useEffect(() => {
    favoritesRef.current = favorites
    saveFavorites(favorites)
  }, [favorites])

  useEffect(() => {
    saveVolume(volume)
    if (audioRef.current) {
      audioRef.current.volume = volume
    }
  }, [volume])

  useEffect(() => {
    saveWeatherEnabled(isWeatherEnabled)
  }, [isWeatherEnabled])

  useEffect(() => {
    if (!firebaseAuth) {
      return
    }

    const unsubscribe = onAuthStateChanged(firebaseAuth, (user) => {
      setCurrentUser(user)
      setIsAuthBusy(false)

      if (!user) {
        setAuthStatusMessage('Signed out. Using local favorites.')
        return
      }

      void syncFavoritesFromCloud(user)
    })

    return unsubscribe
  }, [])

  useEffect(() => {
    void loadStations({ query: '', genre: 'Lofi' })
  }, [])

  useEffect(() => {
    if (!isWeatherEnabled || !navigator.geolocation) {
      if (!isWeatherEnabled) {
        setWeatherMessage('Weather hidden.')
      }
      return
    }

    navigator.geolocation.getCurrentPosition(
      async (position) => {
        try {
          const forecast = await getLocalForecast(position.coords.latitude, position.coords.longitude)
          setWeather(forecast)
          setWeatherMessage('')
        } catch (error) {
          setWeatherMessage(error instanceof Error ? error.message : 'Weather unavailable.')
        }
      },
      () => {
        setWeatherMessage('Location declined. Forecast unavailable.')
      },
      { enableHighAccuracy: false, timeout: 8000 }
    )
  }, [isWeatherEnabled])

  useEffect(() => {
    if (!stationId) return

    let cancelled = false
    ;(async () => {
      setLoadError(null)
      try {
        const station = await getStationById(stationId)
        if (!station || cancelled) return
        playStation(station, [station], 'Queue: Shared Station')
      } catch (error) {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : 'Unable to open shared station.')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [stationId])

  async function loadStations(input: { query: string; genre: string }) {
    setIsLoading(true)
    setLoadError(null)

    try {
      const result = await searchStations({
        query: input.query,
        genre: input.genre
      })
      setStations(result)
      setSelectedGenre(input.genre || 'All')
      setSearchQuery(input.query)
      setQueueLabel(
        input.query.trim()
          ? `Queue: Search "${input.query.trim()}"`
          : input.genre && input.genre !== 'All'
            ? `Queue: Genre ${input.genre}`
            : 'Queue: All Genres'
      )
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : 'Unable to load stations.')
    } finally {
      setIsLoading(false)
    }
  }

  async function syncFavoritesFromCloud(user: User) {
    setAuthStatusMessage('Syncing favorites from cloud...')

    try {
      const cloudFavorites = await loadCloudFavorites(user.uid)
      const localFavorites = favoritesRef.current
      const mergedFavorites = mergeFavorites(localFavorites, cloudFavorites)

      setFavorites(mergedFavorites)
      saveFavorites(mergedFavorites)

      if (mergedFavorites.length > 0) {
        await syncMergedFavoritesToCloud(user.uid, mergedFavorites)
      }

      setAuthStatusMessage(`Cloud favorites synced (${mergedFavorites.length}).`)
    } catch (error) {
      setAuthStatusMessage(
        error instanceof Error ? error.message : 'Unable to sync cloud favorites.'
      )
    }
  }

  async function handleGoogleSignIn() {
    if (!isFirebaseConfigured) {
      setAuthStatusMessage('Firebase web auth is not configured yet.')
      return
    }

    setIsAuthBusy(true)
    setAuthStatusMessage('Starting Google sign-in...')

    try {
      await signInWithGoogle()
      setAuthStatusMessage('Completing sign-in...')
    } catch (error) {
      setIsAuthBusy(false)
      setAuthStatusMessage(
        error instanceof Error ? error.message : 'Google sign-in failed.'
      )
    }
  }

  async function handleGoogleSignOut() {
    if (!firebaseAuth) {
      setAuthStatusMessage('Firebase web auth is not configured yet.')
      return
    }

    setIsAuthBusy(true)

    try {
      await signOutFromGoogle()
      setAuthStatusMessage('Signed out. Using local favorites.')
    } catch (error) {
      setIsAuthBusy(false)
      setAuthStatusMessage(
        error instanceof Error ? error.message : 'Sign-out failed.'
      )
    }
  }

  function focusTab(nextTab: MainTab) {
    setTab(nextTab)

    const target =
      nextTab === 'player'
        ? playerWindowRef.current
        : nextTab === 'stations'
          ? stationsWindowRef.current
          : favoritesWindowRef.current

    target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function playStation(station: RadioStation, queue = stations, nextQueueLabel = queueLabel) {
    const audio = audioRef.current
    if (!audio) return

    const effectiveQueue = queue.some((candidate) => candidate.id === station.id)
      ? queue
      : [station, ...queue]

    audio.src = station.streamUrl
    audio.play().catch(() => {
      setPlaybackStatus('Error')
    })

    setActiveStation(station)
    setCurrentQueue(effectiveQueue)
    setQueueLabel(nextQueueLabel)
    focusTab('player')
    setPlaybackStatus('Buffering')
    navigate(`/station/${station.id}`, { replace: true })
  }

  function playRelative(offset: number) {
    if (!activeStation || currentQueue.length === 0) return
    const currentIndex = currentQueue.findIndex((station) => station.id === activeStation.id)
    if (currentIndex < 0) return
    const nextIndex = (currentIndex + offset + currentQueue.length) % currentQueue.length
    playStation(currentQueue[nextIndex], currentQueue, queueLabel)
  }

  function togglePlayback() {
    const audio = audioRef.current
    if (!audio) return

    if (!activeStation) {
      const seed = stations[0] ?? favorites[0]
      if (seed) playStation(seed)
      return
    }

    if (audio.paused) {
      void audio.play()
    } else {
      audio.pause()
    }
  }

  async function toggleFavorite(station: RadioStation) {
    const isRemoving = favoriteIds.has(station.id)

    setFavorites((current) => {
      if (isRemoving) {
        return current.filter((item) => item.id !== station.id)
      }
      return [{ ...station }, ...current]
    })

    if (!currentUser) {
      return
    }

    try {
      if (isRemoving) {
        await removeCloudFavorite(currentUser.uid, station.id)
        setAuthStatusMessage('Favorite removed from cloud.')
      } else {
        await upsertCloudFavorite(currentUser.uid, station)
        setAuthStatusMessage('Favorite synced to cloud.')
      }
    } catch (error) {
      setAuthStatusMessage(
        error instanceof Error ? error.message : 'Unable to update cloud favorites.'
      )
    }
  }

  async function shareStation(station: RadioStation) {
    const url = `${window.location.origin}/station/${station.id}`
    const text = `Tune into ${station.name} on Neon Horizon Radio • ${station.country || 'Unknown location'}\n\nOpen station:\n${url}\n\nDownload app:\n${SHARE_FALLBACK_URL}`

    if (navigator.share) {
      try {
        await navigator.share({
          title: `Neon Horizon Radio: ${station.name}`,
          text,
          url
        })
        return
      } catch {
        // fall back to clipboard
      }
    }

    await navigator.clipboard.writeText(text)
    window.alert('Station link copied to clipboard.')
  }

  const marqueeText = activeStation
    ? `${activeStation.name}   •   ${activeStation.country || 'Unknown Country'}`
    : 'No station selected'

  return (
    <div className="app-shell">
      <div className="app-backdrop" />
      <main className="desktop">
        <div className="desktop-top">
          <div className="desktop-brand">
            <img src="/assets/logo-neon-horizon-small.png" alt="" />
            <div>
              <div className="desktop-eyebrow">Netlify Web Build</div>
              <h1>Neon Horizon Radio</h1>
            </div>
          </div>
          <button className="settings-launch" onClick={() => setShowSettings(true)}>
            Settings
          </button>
        </div>

        {isWeatherEnabled && (
          <Window title="Local Forecast" onMinimize={() => setIsWeatherEnabled(false)}>
            <div className="weather-grid">
              {weather ? (
                <>
                  <WeatherCard day={weather.today} />
                  <WeatherCard day={weather.tomorrow} />
                </>
              ) : (
                <div className="weather-empty">{weatherMessage}</div>
              )}
            </div>
          </Window>
        )}

        <div className="desktop-columns">
          <section className="desktop-main">
            <Window title="Neon Horizon Radio" sectionRef={playerWindowRef}>
              <div className="status-strip">
                <span>Stations: {stations.length}</span>
                <span>Favorites: {favorites.length}</span>
                <span>Status: {playbackStatus}</span>
              </div>

              <div className="player-layout">
                <div className="player-cover">
                  <img
                    src={activeStation?.favicon || '/assets/logo-neon-horizon.png'}
                    alt={activeStation?.name ?? 'Station cover'}
                    onError={(event) => {
                      event.currentTarget.src = '/assets/logo-neon-horizon.png'
                    }}
                  />
                </div>

                <div className="player-meta">
                  <div className={`marquee-box ${isPlaying ? 'is-playing' : ''}`}>
                    <div
                      className="marquee-track"
                      style={{ animationPlayState: isPlaying ? 'running' : 'paused' }}
                    >
                      <span>{marqueeText}</span>
                      <span>{marqueeText}</span>
                    </div>
                  </div>

                  <div className="queue-label">{queueLabel}</div>

                  <div className="controls-row">
                    <button onClick={() => activeStation && toggleFavorite(activeStation)} disabled={!activeStation}>
                      {activeStation && favoriteIds.has(activeStation.id) ? '♥' : '♡'}
                    </button>
                    <button onClick={() => activeStation && shareStation(activeStation)} disabled={!activeStation}>
                      ↗
                    </button>
                    <button onClick={() => playRelative(-1)} disabled={!activeStation}>⏮</button>
                    <button className="primary" onClick={togglePlayback}>{isPlaying ? '❚❚' : '▶'}</button>
                    <button onClick={() => playRelative(1)} disabled={!activeStation}>⏭</button>
                  </div>

                  <div className="volume-row">
                    <span>🔊</span>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.01"
                      value={volume}
                      onChange={(event) => setVolume(Number(event.target.value))}
                    />
                  </div>
                </div>
              </div>

              {loadError && <div className="error-strip">{loadError}</div>}
            </Window>
          </section>

          <aside className="desktop-side">
            <Window title="Stations" sectionRef={stationsWindowRef}>
              <div className="search-row">
                <input
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="Search by genre, country, instrument..."
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      void loadStations({ query: searchQuery, genre: '' })
                    }
                  }}
                />
                <button onClick={() => void loadStations({ query: searchQuery, genre: '' })}>Search</button>
              </div>

              <div className="genre-strip">
                {DEFAULT_GENRES.map((genre) => (
                  <button
                    key={genre}
                    className={selectedGenre === genre ? 'is-selected' : ''}
                    onClick={() => void loadStations({ query: '', genre })}
                  >
                    {genre}
                  </button>
                ))}
              </div>

              <div className="station-list">
                {isLoading ? (
                  <div className="empty-state">Loading stations…</div>
                ) : stations.length === 0 ? (
                  <div className="empty-state">No stations available.</div>
                ) : (
                  stations.map((station) => (
                    <StationRow
                      key={station.id}
                      station={station}
                      isActive={activeStation?.id === station.id}
                      isFavorite={favoriteIds.has(station.id)}
                      onPlay={() => playStation(station, stations, queueLabel)}
                      onFavorite={() => toggleFavorite(station)}
                    />
                  ))
                )}
              </div>
            </Window>

            <Window title="Favorites" sectionRef={favoritesWindowRef}>
              <div className="favorites-list">
                {favorites.length === 0 ? (
                  <div className="empty-state">No favorites yet. Save stations here.</div>
                ) : (
                  favorites.map((station) => (
                    <StationRow
                      key={station.id}
                      station={station}
                      isActive={activeStation?.id === station.id}
                      isFavorite
                      onPlay={() => playStation(station, favorites, 'Queue: Favorites')}
                      onFavorite={() => toggleFavorite(station)}
                    />
                  ))
                )}
              </div>
            </Window>
          </aside>
        </div>

        <footer className="taskbar">
          <button className={tab === 'player' ? 'is-selected' : ''} onClick={() => focusTab('player')}>▣ Player</button>
          <button className={tab === 'stations' ? 'is-selected' : ''} onClick={() => focusTab('stations')}>♫ Stations</button>
          <button className={tab === 'favorites' ? 'is-selected' : ''} onClick={() => focusTab('favorites')}>♥ Favorites</button>
          <button onClick={() => setShowSettings(true)}>⚙</button>
          <div className="taskbar-clock">{new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })}</div>
        </footer>

        {showSettings && (
          <div className="modal-scrim" onClick={() => setShowSettings(false)}>
            <div className="modal-panel" onClick={(event) => event.stopPropagation()}>
              <Window title="Neon Horizon Radio Settings" onMinimize={() => setShowSettings(false)}>
                <div className="settings-grid">
                  <section className="settings-section">
                    <div className="settings-section-header">
                      <strong>Google Account</strong>
                    </div>

                    {currentUser ? (
                      <div className="account-summary">
                        <img
                          className="account-avatar"
                          src={currentUser.photoURL || '/assets/logo-neon-horizon-small.png'}
                          alt={currentUser.displayName || 'Signed-in user'}
                        />
                        <div>
                          <div>{currentUser.displayName || 'Signed in'}</div>
                          <div className="account-detail">{currentUser.email || 'Google account'}</div>
                        </div>
                      </div>
                    ) : (
                      <div className="settings-note">
                        Sign in with Google to sync favorites with the same Firebase account used by Android.
                      </div>
                    )}

                    <div className="settings-actions">
                      {currentUser ? (
                        <button onClick={handleGoogleSignOut} disabled={isAuthBusy}>
                          {isAuthBusy ? 'Working...' : 'Sign out'}
                        </button>
                      ) : (
                        <button onClick={handleGoogleSignIn} disabled={!isFirebaseConfigured || isAuthBusy}>
                          {isAuthBusy ? 'Working...' : 'Sign in with Google'}
                        </button>
                      )}
                    </div>

                    <div className="settings-note">{authStatusMessage}</div>
                  </section>

                  <label className="setting-row">
                    <span>Weather</span>
                    <input
                      type="checkbox"
                      checked={isWeatherEnabled}
                      onChange={(event) => setIsWeatherEnabled(event.target.checked)}
                    />
                  </label>
                  <div className="settings-note">
                    Weather uses browser location plus Open-Meteo over HTTPS. On Netlify it should work once location access is granted.
                  </div>
                </div>
              </Window>
            </div>
          </div>
        )}
      </main>
    </div>
  )
}

function Window(props: {
  title: string
  children: React.ReactNode
  onMinimize?: () => void
  sectionRef?: React.Ref<HTMLElement>
}) {
  return (
    <section className="window-frame" ref={props.sectionRef}>
      <header className="window-titlebar">
        <div className="window-title">
          <img src="/assets/logo-neon-horizon-small.png" alt="" />
          <span>{props.title}</span>
        </div>
        <button className="titlebar-button" onClick={props.onMinimize}>—</button>
      </header>
      <div className="window-content">{props.children}</div>
    </section>
  )
}

function WeatherCard({ day }: { day: LocalWeatherForecast['today'] }) {
  return (
    <article className="weather-card">
      <h3>{day.dayLabel}</h3>
      <p>{day.summary}</p>
      <p>High {day.highTemperature}{day.temperatureUnitSymbol} Low {day.lowTemperature}{day.temperatureUnitSymbol}</p>
      <p>Rain {day.precipitationChance}%</p>
    </article>
  )
}

function StationRow(props: {
  station: RadioStation
  isActive: boolean
  isFavorite: boolean
  onPlay: () => void
  onFavorite: () => void
}) {
  return (
    <article className={`station-row ${props.isActive ? 'is-active' : ''}`}>
      <div className="station-copy" onClick={props.onPlay}>
        <h4>{props.station.name}</h4>
        <p>{props.station.country || 'Unknown Country'} • {props.station.tags || 'Open airwaves'}</p>
      </div>
      <div className="station-actions">
        <button onClick={props.onFavorite}>{props.isFavorite ? '♥' : '♡'}</button>
        <button onClick={props.onPlay}>▶</button>
      </div>
    </article>
  )
}

export default App
