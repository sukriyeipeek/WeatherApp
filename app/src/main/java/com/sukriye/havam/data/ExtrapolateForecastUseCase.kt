package com.sukriye.havam.data

import com.sukriye.havam.model.HourlyPoint
import com.sukriye.havam.model.WeatherSnapshot
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * "Zero-Network" devamlılığı: ağ yokken önbellekteki saatlik veri bittiğinde kalan saatleri
 * tahmin eder. Sıcaklıkta dünün aynı saati (günlük döngü) + bugünkü sapma, diğer büyüklüklerde
 * son 12 saatin en küçük kareler doğrusal trendi kullanılır. Trendin etkisi zamanla sönümlenir,
 * çünkü uzak saatlerde doğrusal varsayım güvenilmez olur.
 */
class ExtrapolateForecastUseCase @Inject constructor() {

    data class Result(val snapshot: WeatherSnapshot, val extrapolatedHours: Int)

    operator fun invoke(snapshot: WeatherSnapshot, nowEpochSeconds: Long, horizonHours: Int = 24): Result {
        val known = snapshot.hourly.sortedBy { it.epochSeconds }
        if (known.size < 2) return Result(snapshot, 0)

        val hourStart = nowEpochSeconds - Math.floorMod(nowEpochSeconds, HOUR)
        val horizonEnd = hourStart + horizonHours * HOUR
        val last = known.last()

        val generated = if (last.epochSeconds >= horizonEnd) {
            emptyList()
        } else {
            val byTime = known.associateBy { it.epochSeconds }
            val sample = known.takeLast(12)
            val trends = Trends(sample)
            generateSequence(last.epochSeconds + HOUR) { it + HOUR }
                .takeWhile { it <= horizonEnd }
                .map { time -> project(time, last, byTime, trends, snapshot) }
                .toList()
        }
        val hourly = known + generated
        return Result(
            snapshot = snapshot.copy(
                hourly = hourly,
                current = refreshCurrent(snapshot, hourly, nowEpochSeconds)
            ),
            extrapolatedHours = generated.size
        )
    }

    private fun project(
        time: Long,
        last: HourlyPoint,
        byTime: Map<Long, HourlyPoint>,
        trends: Trends,
        snapshot: WeatherSnapshot
    ): HourlyPoint {
        val hoursAhead = (time - last.epochSeconds) / HOUR.toDouble()

        // Sönümlü trend: eğim * tau * (1 - e^(-t/tau)). t küçükken doğrusal, büyüdükçe sabitlenir.
        fun damped(slope: Double) = slope * TAU * (1 - exp(-hoursAhead / TAU))
        val decay = exp(-hoursAhead / 6.0)

        val yesterday = byTime[time - DAY]
        val yesterdayOfLast = byTime[last.epochSeconds - DAY]
        val temperature = if (yesterday != null && yesterdayOfLast != null) {
            // Dünün eğrisi + bugün dünden ne kadar sapmışsak o fark (zamanla azalarak)
            yesterday.temperature + (last.temperature - yesterdayOfLast.temperature) * exp(-hoursAhead / 24.0)
        } else {
            last.temperature + damped(trends.temperature)
        }
        val apparentGap = last.apparentTemperature - last.temperature
        val isDay = isDayAt(time, snapshot) ?: yesterday?.isDay ?: last.isDay

        return HourlyPoint(
            epochSeconds = time,
            temperature = temperature,
            apparentTemperature = temperature + apparentGap,
            humidity = (last.humidity + damped(trends.humidity)).roundToInt().coerceIn(0, 100),
            dewPoint = last.dewPoint + damped(trends.dewPoint),
            pressureMsl = last.pressureMsl + damped(trends.pressure),
            cloudCover = (last.cloudCover + damped(trends.cloud)).roundToInt().coerceIn(0, 100),
            visibilityMeters = last.visibilityMeters,
            windSpeed = (last.windSpeed + damped(trends.wind)).coerceAtLeast(0.0),
            windGusts = (last.windGusts + damped(trends.gusts)).coerceAtLeast(0.0),
            uvIndex = if (isDay) yesterday?.uvIndex ?: last.uvIndex else 0.0,
            rainChance = (last.rainChance * decay).roundToInt(),
            precipitation = last.precipitation * decay,
            weatherCode = last.weatherCode,
            isDay = isDay,
            isExtrapolated = true
        )
    }

    // Önbellekteki "şimdi" çok eskiyse, şu anki saate denk gelen (gerçek ya da tahmini) saatle değiştir
    private fun refreshCurrent(snapshot: WeatherSnapshot, hourly: List<HourlyPoint>, now: Long) =
        if (now - snapshot.current.epochSeconds < 2 * HOUR) {
            snapshot.current
        } else {
            val hour = hourly.lastOrNull { it.epochSeconds <= now } ?: hourly.first()
            val c = snapshot.current
            c.copy(
                epochSeconds = hour.epochSeconds,
                temperature = hour.temperature,
                apparentTemperature = hour.apparentTemperature,
                humidity = hour.humidity,
                windSpeed = hour.windSpeed,
                windGusts = hour.windGusts,
                weatherCode = hour.weatherCode,
                precipitation = hour.precipitation,
                rainChance = hour.rainChance,
                isDay = hour.isDay,
                surfacePressure = c.surfacePressure + (hour.pressureMsl - c.pressureMsl),
                pressureMsl = hour.pressureMsl,
                cloudCover = hour.cloudCover,
                uvIndex = hour.uvIndex,
                visibilityMeters = hour.visibilityMeters
            )
        }

    private fun isDayAt(time: Long, snapshot: WeatherSnapshot): Boolean? {
        val day = snapshot.daily.lastOrNull { it.epochSeconds <= time } ?: return null
        if (time >= day.epochSeconds + DAY) return null
        return time in day.sunriseEpochSeconds until day.sunsetEpochSeconds
    }

    // Saat başına eğimler (birim/saat), en küçük kareler yöntemiyle
    private class Trends(private val sample: List<HourlyPoint>) {
        private val x = sample.map { (it.epochSeconds - sample.first().epochSeconds) / HOUR.toDouble() }
        val temperature = slope { it.temperature }
        val humidity = slope { it.humidity.toDouble() }
        val dewPoint = slope { it.dewPoint }
        val pressure = slope { it.pressureMsl }
        val cloud = slope { it.cloudCover.toDouble() }
        val wind = slope { it.windSpeed }
        val gusts = slope { it.windGusts }

        private fun slope(value: (HourlyPoint) -> Double): Double {
            val ys = sample.map(value)
            val meanX = x.average()
            val meanY = ys.average()
            var numerator = 0.0
            var denominator = 0.0
            for (i in x.indices) {
                numerator += (x[i] - meanX) * (ys[i] - meanY)
                denominator += (x[i] - meanX) * (x[i] - meanX)
            }
            return if (denominator == 0.0) 0.0 else numerator / denominator
        }
    }

    private companion object {
        const val HOUR = 3600L
        const val DAY = 24 * HOUR
        const val TAU = 8.0
    }
}
