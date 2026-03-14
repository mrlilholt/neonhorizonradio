import type { LocalWeatherForecast, WeatherDayForecast } from '../types'

type WeatherCoordinates = {
  latitude: number
  longitude: number
}

type OpenMeteoPayload = {
  daily: {
    weather_code: number[]
    temperature_2m_max: number[]
    temperature_2m_min: number[]
    precipitation_probability_max: number[]
  }
  daily_units?: {
    temperature_2m_max?: string
  }
}

const WEATHER_SUMMARY: Record<number, string> = {
  0: 'Clear',
  1: 'Mostly Clear',
  2: 'Partly Cloudy',
  3: 'Overcast',
  45: 'Fog',
  48: 'Fog',
  51: 'Light Drizzle',
  53: 'Drizzle',
  55: 'Heavy Drizzle',
  61: 'Light Rain',
  63: 'Rain',
  65: 'Heavy Rain',
  71: 'Light Snow',
  73: 'Snow',
  75: 'Heavy Snow',
  80: 'Showers',
  81: 'Rain Showers',
  82: 'Heavy Showers',
  95: 'Thunderstorm'
}

function summarize(code: number) {
  return WEATHER_SUMMARY[code] ?? 'Open Skies'
}

function parseCoordinate(value: string | undefined): number | null {
  if (!value) {
    return null
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function mapDay(label: string, payload: OpenMeteoPayload, index: number): WeatherDayForecast {
  const unit = payload.daily_units?.temperature_2m_max ?? '°F'
  return {
    dayLabel: label,
    summary: summarize(payload.daily.weather_code[index] ?? 0),
    highTemperature: Math.round(payload.daily.temperature_2m_max[index] ?? 0),
    lowTemperature: Math.round(payload.daily.temperature_2m_min[index] ?? 0),
    precipitationChance: Math.round(payload.daily.precipitation_probability_max[index] ?? 0),
    temperatureUnitSymbol: unit
  }
}

export async function getLocalForecast(latitude: number, longitude: number): Promise<LocalWeatherForecast> {
  const url = new URL('https://api.open-meteo.com/v1/forecast')
  url.searchParams.set('latitude', String(latitude))
  url.searchParams.set('longitude', String(longitude))
  url.searchParams.set('daily', 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max')
  url.searchParams.set('forecast_days', '2')
  url.searchParams.set('temperature_unit', 'fahrenheit')
  url.searchParams.set('timezone', 'auto')

  const response = await fetch(url)
  if (!response.ok) {
    throw new Error('Weather unavailable')
  }

  const payload = (await response.json()) as OpenMeteoPayload

  return {
    today: mapDay('Today', payload, 0),
    tomorrow: mapDay('Tomorrow', payload, 1)
  }
}

export function getConfiguredWeatherCoordinates(): WeatherCoordinates | null {
  const latitude = parseCoordinate(import.meta.env.VITE_WEATHER_FALLBACK_LATITUDE)
  const longitude = parseCoordinate(import.meta.env.VITE_WEATHER_FALLBACK_LONGITUDE)

  if (latitude === null || longitude === null) {
    return null
  }

  return { latitude, longitude }
}

export function getConfiguredWeatherLabel(): string {
  return (import.meta.env.VITE_WEATHER_FALLBACK_LABEL ?? '').trim()
}
