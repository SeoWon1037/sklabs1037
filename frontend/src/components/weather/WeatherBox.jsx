// ============================================
// 날씨 박스 (메인 화면 하단) — 네이버 날씨 스타일
// - 데이터: Open-Meteo (무료, API 키 없음) → 브라우저에서 바로 호출
//   · 날씨: 현재 기온/상태, 오늘 최저·최고, 시간별 기온
//   · 대기질: 미세먼지(PM10), 초미세먼지(PM2.5)
// - 위치: 서울
// - 화면이 열려 있는 동안 10분마다 자동으로 다시 불러옴
// ============================================
import { useEffect, useState } from 'react'

const LOCATION = { name: '서울', lat: 37.5665, lon: 126.978 }

const WEATHER_URL =
  `https://api.open-meteo.com/v1/forecast?latitude=${LOCATION.lat}&longitude=${LOCATION.lon}` +
  '&current=temperature_2m,weather_code' +
  '&hourly=temperature_2m,weather_code' +
  '&daily=temperature_2m_max,temperature_2m_min' +
  '&timezone=Asia%2FSeoul&forecast_days=2'

const AIR_URL =
  `https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${LOCATION.lat}&longitude=${LOCATION.lon}` +
  '&current=pm10,pm2_5&timezone=Asia%2FSeoul'

// 자동 갱신 주기 (10분)
const REFRESH_MS = 10 * 60 * 1000

// 시간별 예보: 2시간 간격으로 8개
const HOUR_STEP = 2
const HOUR_COUNT = 8

// 날씨 코드(WMO) → 아이콘 + 한글
const weatherInfo = (code) => {
  if (code === 0) return { icon: '☀️', text: '맑음' }
  if (code <= 2) return { icon: '🌤️', text: '구름 조금' }
  if (code === 3) return { icon: '☁️', text: '흐림' }
  if (code <= 48) return { icon: '🌫️', text: '안개' }
  if (code <= 57) return { icon: '🌦️', text: '이슬비' }
  if (code <= 67) return { icon: '🌧️', text: '비' }
  if (code <= 77) return { icon: '🌨️', text: '눈' }
  if (code <= 82) return { icon: '🌧️', text: '소나기' }
  if (code <= 86) return { icon: '🌨️', text: '눈 소나기' }
  return { icon: '⛈️', text: '뇌우' }
}

// 미세먼지 등급 (환경부 기준)
const dustGrade = (value, limits) => {
  if (value == null) return { text: '-', cls: '' }
  if (value <= limits[0]) return { text: '좋음', cls: 'dust-good' }
  if (value <= limits[1]) return { text: '보통', cls: 'dust-normal' }
  if (value <= limits[2]) return { text: '나쁨', cls: 'dust-bad' }
  return { text: '매우나쁨', cls: 'dust-worst' }
}

// 시간별 기온 선 그래프 크기 (가로는 화면 폭에 맞게 늘어남)
const CHART_W = 600
const CHART_H = 56

// Open-Meteo 응답 → 화면에서 쓸 모양으로 정리
const toWeatherData = (weather, air) => {

  // 지금 시각 이후의 시간별 예보만 (2시간 간격)
  const nowHour = weather.current.time.slice(0, 13)    // "2026-10-18T11"
  const start = weather.hourly.time.findIndex((t) => t.slice(0, 13) >= nowHour)

  const hours = []
  for (let i = start; i < weather.hourly.time.length && hours.length < HOUR_COUNT; i += HOUR_STEP) {
    hours.push({
      hour: Number(weather.hourly.time[i].slice(11, 13)),
      temp: weather.hourly.temperature_2m[i],
      code: weather.hourly.weather_code[i],
    })
  }

  return {
    current: weather.current,
    updatedAt: weather.current.time.slice(11, 16),     // "11:45" (데이터 기준 시각)
    hours,
    min: weather.daily.temperature_2m_min[0],
    max: weather.daily.temperature_2m_max[0],
    pm10: air?.current?.pm10,
    pm25: air?.current?.pm2_5,
  }
}

function WeatherBox() {

  const [data, setData] = useState(null)
  const [error, setError] = useState(false)

  useEffect(() => {

    let ignore = false   // 화면을 떠난 뒤 늦게 도착한 응답은 무시

    const load = () => {
      Promise.all([
        fetch(WEATHER_URL).then((res) => res.json()),
        fetch(AIR_URL).then((res) => res.json()).catch(() => null),   // 대기질은 실패해도 날씨는 보여줌
      ])
        .then(([weather, air]) => {
          if (ignore) return
          setData(toWeatherData(weather, air))
          setError(false)
        })
        .catch(() => {
          // 갱신에 실패하면 이전 날씨를 그대로 보여주고, 처음부터 실패했을 때만 에러 표시
          if (!ignore) setError(true)
        })
    }

    load()                                       // 화면이 열릴 때 1번
    const timer = setInterval(load, REFRESH_MS)  // 이후 10분마다

    // 화면을 떠나면 타이머 정리 (안 하면 다른 화면에서도 계속 호출됨)
    return () => {
      ignore = true
      clearInterval(timer)
    }

  }, [])

  // 처음 불러오기 실패 (이전 데이터가 있으면 그걸 계속 보여줌)
  if (!data && error) {
    return (
      <section className="home-box weather">
        <h2 className="home-box-title">날씨</h2>
        <p className="home-empty">날씨 정보를 불러오지 못했습니다.</p>
      </section>
    )
  }

  if (!data) {
    return (
      <section className="home-box weather">
        <h2 className="home-box-title">날씨</h2>
        <p className="home-empty">날씨를 불러오는 중...</p>
      </section>
    )
  }

  const now = weatherInfo(data.current.weather_code)
  const pm10 = dustGrade(data.pm10, [30, 80, 150])   // 미세먼지 기준 (㎍/㎥)
  const pm25 = dustGrade(data.pm25, [15, 35, 75])    // 초미세먼지 기준 (㎍/㎥)

  // ===== 시간별 기온 선 좌표 =====
  const temps = data.hours.map((h) => h.temp)
  const tMin = Math.min(...temps)
  const tMax = Math.max(...temps)
  const range = tMax - tMin || 1
  const colW = CHART_W / data.hours.length
  const toX = (i) => colW * i + colW / 2                                  // 각 칸의 가운데
  const toY = (t) => 10 + (1 - (t - tMin) / range) * (CHART_H - 20)       // 위아래 여백 10
  const line = temps.map((t, i) => `${i === 0 ? 'M' : 'L'}${toX(i)},${toY(t)}`).join(' ')

  return (
    <section className="home-box weather">

      <div className="home-box-head">
        <h2 className="home-box-title">날씨</h2>
        <span className="home-box-sub">{LOCATION.name} · {data.updatedAt} 기준 · Open-Meteo</span>
      </div>

      <div className="weather-body">

        {/* ===== 왼쪽: 현재 날씨 ===== */}
        <div className="weather-now">
          <div className="weather-now-main">
            <span className="weather-now-icon">{now.icon}</span>
            <div>
              <div className="weather-now-temp">{data.current.temperature_2m.toFixed(1)}°</div>
              <div className="weather-now-text">{now.text}</div>
            </div>
          </div>

          <div className="weather-minmax">
            <span className="weather-min">{Math.round(data.min)}°</span>
            <span className="weather-slash">/</span>
            <span className="weather-max">{Math.round(data.max)}°</span>
          </div>

          <div className="weather-dust">
            미세 <strong className={pm10.cls}>{pm10.text}</strong>
            <span className="weather-dot">·</span>
            초미세 <strong className={pm25.cls}>{pm25.text}</strong>
          </div>
        </div>

        {/* ===== 오른쪽: 시간별 예보 ===== */}
        <div className="weather-hours">

          {/* 기온 숫자 */}
          <div className="weather-row">
            {data.hours.map((h, i) => (
              <span key={i} className="weather-cell weather-temp">{Math.round(h.temp)}°</span>
            ))}
          </div>

          {/* 기온 선 */}
          <svg className="weather-line" viewBox={`0 0 ${CHART_W} ${CHART_H}`} preserveAspectRatio="none" width="100%" height={CHART_H}>
            <path d={line} fill="none" strokeWidth="2" vectorEffect="non-scaling-stroke" />
          </svg>

          {/* 아이콘 */}
          <div className="weather-row">
            {data.hours.map((h, i) => (
              <span key={i} className="weather-cell weather-icon" title={weatherInfo(h.code).text}>
                {weatherInfo(h.code).icon}
              </span>
            ))}
          </div>

          {/* 시각 */}
          <div className="weather-row">
            {data.hours.map((h, i) => (
              <span key={i} className={`weather-cell weather-hour ${i === 0 ? 'weather-hour-now' : ''}`}>
                {i === 0 ? `${h.hour}시` : h.hour}
              </span>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}

export default WeatherBox