import type { RadioStation } from '../types'

const API_BASES = [
  'https://de1.api.radio-browser.info',
  'https://all.api.radio-browser.info',
  'https://nl1.api.radio-browser.info',
  'https://at1.api.radio-browser.info',
  'https://fr1.api.radio-browser.info'
]

type RadioStationDto = {
  stationuuid?: string | null
  name?: string | null
  url_resolved?: string | null
  homepage?: string | null
  favicon?: string | null
  tags?: string | null
  country?: string | null
}

function mapStation(dto: RadioStationDto): RadioStation | null {
  const id = dto.stationuuid?.trim() ?? ''
  const name = dto.name?.trim() ?? ''
  const streamUrl = dto.url_resolved?.trim() ?? ''
  if (!id || !name || !streamUrl) return null

  return {
    id,
    name,
    streamUrl,
    homepage: dto.homepage ?? '',
    favicon: dto.favicon ?? '',
    tags: dto.tags ?? '',
    country: dto.country ?? ''
  }
}

async function fetchWithFallback<T>(builder: (base: string) => string): Promise<T> {
  let lastError: unknown

  for (const base of API_BASES) {
    try {
      const response = await fetch(builder(base))
      if (!response.ok) {
        throw new Error(`Radio Browser ${response.status}`)
      }
      return (await response.json()) as T
    } catch (error) {
      lastError = error
    }
  }

  throw lastError instanceof Error ? lastError : new Error('Radio Browser unavailable')
}

function encodeQuery(params: Record<string, string | number | boolean | undefined>) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === '') return
    searchParams.set(key, String(value))
  })
  return searchParams.toString()
}

function buildSearchTerms(query: string) {
  const normalized = query.trim().toLowerCase()
  const tokens = normalized
    .split(/[^a-z0-9]+/)
    .map((token) => token.trim())
    .filter((token) => token.length >= 2)

  return Array.from(new Set([normalized, ...tokens])).slice(0, 4)
}

function searchRelevanceScore(station: RadioStation, normalizedQuery: string) {
  const name = station.name.toLowerCase()
  const tags = station.tags.toLowerCase()
  const country = station.country.toLowerCase()
  const tokens = buildSearchTerms(normalizedQuery)

  let score = 0
  if (tags.includes(normalizedQuery)) score += 300
  if (country.includes(normalizedQuery)) score += 240
  if (name.includes(normalizedQuery)) score += 220

  tokens.forEach((token) => {
    if (tags.includes(token)) score += 60
    if (country.includes(token)) score += 45
    if (name.includes(token)) score += 40
  })

  return score
}

export async function getStationById(stationId: string): Promise<RadioStation | null> {
  const payload = await fetchWithFallback<RadioStationDto[]>((base) => {
    const query = encodeQuery({ hidebroken: true })
    return `${base}/json/stations/byuuid/${encodeURIComponent(stationId)}?${query}`
  })

  return payload.map(mapStation).find(Boolean) ?? null
}

export async function searchStations(input: {
  query: string
  genre: string
  limit?: number
}): Promise<RadioStation[]> {
  const query = input.query.trim()
  const genre = input.genre.trim()
  const limit = input.limit ?? (query ? 100 : genre && genre !== 'All' ? 50 : 80)

  if (!query) {
    if (genre && genre.toLowerCase() !== 'all') {
      const payload = await fetchWithFallback<RadioStationDto[]>((base) => {
        const search = encodeQuery({
          hidebroken: true,
          limit,
          order: 'clickcount',
          reverse: true
        })
        return `${base}/json/stations/bytag/${encodeURIComponent(genre.toLowerCase())}?${search}`
      })

      return payload
        .map(mapStation)
        .filter((station): station is RadioStation => station !== null)
        .filter((station) => {
          const genreToken = genre.toLowerCase()
          return (
            station.tags.toLowerCase().includes(genreToken) ||
            station.name.toLowerCase().includes(genreToken)
          )
        })
        .slice(0, limit)
    }

    const payload = await fetchWithFallback<RadioStationDto[]>((base) => {
      const search = encodeQuery({
        hidebroken: true,
        limit,
        order: 'random',
        reverse: false
      })
      return `${base}/json/stations/search?${search}`
    })

    return payload
      .map(mapStation)
      .filter((station): station is RadioStation => station !== null)
      .slice(0, limit)
  }

  const terms = buildSearchTerms(query)
  const bucketLimit = Math.max(limit, 60)
  const buckets = await Promise.all(
    terms.flatMap((term) => [
      fetchWithFallback<RadioStationDto[]>((base) => {
        const search = encodeQuery({
          tag: term,
          hidebroken: true,
          limit: bucketLimit,
          order: 'clickcount',
          reverse: true
        })
        return `${base}/json/stations/search?${search}`
      }),
      fetchWithFallback<RadioStationDto[]>((base) => {
        const search = encodeQuery({
          name: term,
          hidebroken: true,
          limit: bucketLimit,
          order: 'clickcount',
          reverse: true
        })
        return `${base}/json/stations/search?${search}`
      }),
      fetchWithFallback<RadioStationDto[]>((base) => {
        const search = encodeQuery({
          country: term,
          hidebroken: true,
          limit: bucketLimit,
          order: 'clickcount',
          reverse: true
        })
        return `${base}/json/stations/search?${search}`
      }),
      fetchWithFallback<RadioStationDto[]>((base) => {
        const search = encodeQuery({
          hidebroken: true,
          limit: bucketLimit,
          order: 'clickcount',
          reverse: true
        })
        return `${base}/json/stations/bytag/${encodeURIComponent(term)}?${search}`
      })
    ])
  )

  return buckets
    .flat()
    .map(mapStation)
    .filter((station): station is RadioStation => station !== null)
    .filter((station, index, array) => array.findIndex((candidate) => candidate.id === station.id) === index)
    .filter((station) => {
      const normalizedQuery = query.toLowerCase()
      return [station.name, station.tags, station.country].some((field) =>
        field.toLowerCase().includes(normalizedQuery)
      )
    })
    .sort((left, right) => searchRelevanceScore(right, query.toLowerCase()) - searchRelevanceScore(left, query.toLowerCase()))
    .slice(0, limit)
}
