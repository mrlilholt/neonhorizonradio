export type RadioStation = {
  id: string
  name: string
  streamUrl: string
  homepage: string
  favicon: string
  tags: string
  country: string
}

export type WeatherDayForecast = {
  dayLabel: string
  summary: string
  highTemperature: number
  lowTemperature: number
  precipitationChance: number
  temperatureUnitSymbol: string
}

export type LocalWeatherForecast = {
  today: WeatherDayForecast
  tomorrow: WeatherDayForecast
}

export type MainTab = 'player' | 'stations' | 'favorites'
