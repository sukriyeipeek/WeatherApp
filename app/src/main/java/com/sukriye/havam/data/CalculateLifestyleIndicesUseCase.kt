package com.sukriye.havam.data

import com.sukriye.havam.model.HourlyPoint
import com.sukriye.havam.model.IndexFactor
import com.sukriye.havam.model.LifestyleIndex
import com.sukriye.havam.model.LifestyleReport
import com.sukriye.havam.model.PressureTendency
import com.sukriye.havam.model.PressureTrend
import com.sukriye.havam.model.RiskLevel
import com.sukriye.havam.model.TimeWindow
import com.sukriye.havam.model.UserInterest
import com.sukriye.havam.model.WeatherCode
import com.sukriye.havam.model.WeatherSnapshot
import com.sukriye.havam.model.formatClock
import com.sukriye.havam.model.formatWindow
import com.sukriye.havam.model.oneDecimal
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Cihaz içi yaşam tarzı motoru. Ağ ya da LLM gerektirmez: her indeks, ağırlıklı ceza/ödül
 * kurallarından oluşan açıklanabilir bir skor kartıdır (0–100, yüksek = senin için iyi).
 * Her skorun yanında onu neyin belirlediği (IndexFactor) ve en uygun zaman aralığı döner.
 */
class CalculateLifestyleIndicesUseCase @Inject constructor() {

    operator fun invoke(snapshot: WeatherSnapshot, interests: Set<UserInterest>, nowEpochSeconds: Long): LifestyleReport {
        val upcoming = snapshot.upcomingHours(nowEpochSeconds).take(24)
        val now = currentHour(snapshot, upcoming)
        val input = Input(snapshot, now, upcoming, pressureTrend(snapshot, nowEpochSeconds), nowEpochSeconds)

        val indices = UserInterest.entries
            .filter { it in interests }
            .map { interest ->
                when (interest) {
                    UserInterest.Running -> running(input)
                    UserInterest.Photography -> photography(input)
                    UserInterest.Laundry -> laundry(input)
                    UserInterest.Gardening -> gardening(input)
                    UserInterest.Driving -> driving(input)
                }
            }
        return LifestyleReport(indices, input.trend, insight(indices, input.trend))
    }

    private class Input(
        val snapshot: WeatherSnapshot,
        val now: HourlyPoint,
        val upcoming: List<HourlyPoint>,
        val trend: PressureTrend,
        val nowEpochSeconds: Long
    ) {
        val offset get() = snapshot.utcOffsetSeconds
        fun clock(epochSeconds: Long) = formatClock(epochSeconds, offset)
    }

    // Skor kartı: puan ekler/çıkarır, nedenini ve kullanıcıya verilecek ipucunu kaydeder
    private class ScoreCard(start: Double = 100.0) {
        private var score = start
        private val factors = mutableListOf<IndexFactor>()
        private val tips = mutableListOf<Pair<Double, String>>()

        fun penalize(label: String, points: Double, tip: String? = null) {
            if (points < 1) return
            val p = points.coerceAtMost(100.0)
            score -= p
            factors += IndexFactor(label, -p.roundToInt())
            if (tip != null) tips += p to tip
        }

        fun reward(label: String, points: Double, tip: String? = null) {
            if (points < 1) return
            score += points
            factors += IndexFactor(label, points.roundToInt())
            if (tip != null) tips += points to tip
        }

        // Tek başına belirleyici olan tehlikeler (yıldırım gibi) skoru bir tavana sabitler
        fun cap(label: String, max: Double, tip: String? = null) {
            if (score > max) {
                factors += IndexFactor(label, -(score - max).roundToInt())
                score = max
            }
            if (tip != null) tips += 1000.0 to tip
        }

        val value: Int get() = score.roundToInt().coerceIn(0, 100)
        val level: RiskLevel get() = RiskLevel.fromScore(value)
        fun topFactors(count: Int = 3) = factors.sortedByDescending { abs(it.impact) }.take(count)
        fun mainTip(default: String) = tips.maxByOrNull { it.first }?.second ?: default
    }

    // ---------------------------------------------------------------- Koşu / Outdoor

    private fun outdoorCard(h: HourlyPoint, aqi: Int?): ScoreCard = ScoreCard().apply {
        val t = h.apparentTemperature
        if (t < 8) penalize("Soğuk (hissedilen ${t.roundToInt()}°)", (8 - t) * 4, "Katmanlı giyin, ısınma süresini uzat")
        if (t > 18) penalize("Sıcak (hissedilen ${t.roundToInt()}°)", (t - 18) * 5, "Bol su al, gölgeli rotaları seç")
        if (h.humidity > 70) penalize("Nem %${h.humidity}", (h.humidity - 70) * 0.8, "Nem yüksek: temponu %10 düşür")
        if (h.windSpeed >
            20
        ) {
            penalize("Rüzgar ${h.windSpeed.roundToInt()} km/sa", (h.windSpeed - 20) * 1.2, "Dönüşü rüzgar arkandayken planla")
        }
        if (h.rainChance > 10) penalize("Yağış olasılığı %${h.rainChance}", h.rainChance * 0.5, "Su geçirmez rüzgarlık al")
        if (h.precipitation >= 0.5) penalize("Yağış ${h.precipitation.oneDecimal()} mm", 25.0, "Kaygan zemin: patika yerine asfalt seç")
        if (h.uvIndex > 6) penalize("UV ${h.uvIndex.roundToInt()}", (h.uvIndex - 6) * 6, "Güneş kremi ve şapka şart")
        if (!h.isDay) penalize("Karanlık", 10.0, "Yansıtıcı yelek ve kafa lambası kullan")
        if (aqi != null && aqi > 60) penalize("Hava kalitesi $aqi", (aqi - 60) * 0.8, "Hava kirli: kapalı alanda antrenman yap")
        if (WeatherCode.isThunder(h.weatherCode)) cap("Gök gürültüsü", 5.0, "Fırtına var: açık alanda antrenman yapma")
    }

    private fun running(input: Input): LifestyleIndex {
        val aqi = input.snapshot.airQuality?.aqi
        val card = outdoorCard(input.now, aqi)
        val best = input.upcoming.take(12)
            .map { it to outdoorCard(it, aqi).value }
            .maxByOrNull { it.second }
        val window = best?.takeIf { it.second >= card.value + 10 }?.let { (hour, _) ->
            TimeWindow(hour.epochSeconds, hour.epochSeconds + 3600)
        }
        // Belirgin şekilde daha iyi bir saat varsa "şimdi"den önce o söylenir
        val headline = when {
            window != null -> "En iyi koşu saati ${input.clock(window.startEpochSeconds)}"
            card.value >= 80 -> "Koşu için harika bir an"
            card.value >= 50 -> "Koşu için koşullar idare eder"
            else -> "Bugün dışarıda koşmak zor"
        }
        return index(UserInterest.Running, card, headline, card.mainTip("Tempolu bir koşu için koşullar uygun"), window)
    }

    // ---------------------------------------------------------------- Fotoğrafçılık (altın saat)

    private fun photography(input: Input): LifestyleIndex {
        val golden = nextGoldenHour(input)
        val hour = golden?.let { w -> input.snapshot.hourly.minByOrNull { abs(it.epochSeconds - w.startEpochSeconds) } }
            ?: input.now
        val pastRain = input.snapshot.hourly
            .filter { it.epochSeconds in (hour.epochSeconds - 3 * 3600) until hour.epochSeconds }
            .sumOf { it.precipitation }

        val card = ScoreCard(start = 85.0).apply {
            val cloud = hour.cloudCover
            when {
                cloud < 10 -> penalize("Bulutsuz gökyüzü", 15.0, "Bulutsuz gökyüzü: silüet ve güneş yıldızı çekimleri dene")
                cloud in 20..60 -> reward(
                    "Dramatik bulutlar %$cloud",
                    15.0,
                    "Bulutlar ışığı yakalayacak: geniş açıyla gökyüzünü kadraja al"
                )
                cloud > 80 -> penalize("Kapalı gökyüzü %$cloud", 20 + (cloud - 80) * 2.5, "Kapalı hava: yumuşak ışıkla portre çek")
            }
            if (hour.rainChance > 0) penalize("Yağış olasılığı %${hour.rainChance}", hour.rainChance * 0.4)
            val km = hour.visibilityMeters / 1000
            if (WeatherCode.isFog(hour.weatherCode)) {
                penalize("Sis", 10.0, "Sis: mistik, minimal kareler için fırsat")
            } else if (km < 10) {
                penalize("Görüş ${km.oneDecimal()} km", (10 - km) * 4)
            }
            if (pastRain > 0.2 &&
                hour.rainChance < 30
            ) {
                reward("Yağmur sonrası berrak hava", 10.0, "Yağmur sonrası: yansımalar ve doygun renkler için ideal")
            }
            if (hour.humidity > 85 &&
                !WeatherCode.isFog(hour.weatherCode)
            ) {
                reward("Nem: ışık kırılması", 5.0, "Nemli havada ışık kırılır: güneşe karşı çek")
            }
        }
        val headline = if (golden != null) {
            val prefix = if (card.value >= 60) "Altın saat ideal" else "Altın saat"
            "$prefix: ${formatWindow(golden, input.offset)}"
        } else {
            "Altın saat bilgisi yok"
        }
        return index(UserInterest.Photography, card, headline, card.mainTip("Işık güzel: yan ışıkla dokuları öne çıkar"), golden)
    }

    // Gün doğumundan sonraki ve gün batımından önceki 45 dakika; şu andan sonraki ilki
    private fun nextGoldenHour(input: Input): TimeWindow? {
        val goldenSeconds = 45 * 60L
        return input.snapshot.daily
            .flatMap { day ->
                listOf(
                    TimeWindow(day.sunriseEpochSeconds, day.sunriseEpochSeconds + goldenSeconds),
                    TimeWindow(day.sunsetEpochSeconds - goldenSeconds, day.sunsetEpochSeconds)
                )
            }
            .filter { it.endEpochSeconds > input.nowEpochSeconds }
            .minByOrNull { it.startEpochSeconds }
    }

    // ---------------------------------------------------------------- Çamaşır kurutma

    private fun laundryCard(hours: List<HourlyPoint>): ScoreCard = ScoreCard(start = 0.0).apply {
        val t = hours.map { it.temperature }.average()
        val humidity = hours.map { it.humidity }.average()
        val wind = hours.map { it.windSpeed }.average()
        val cloud = hours.map { it.cloudCover }.average()
        val rain = hours.maxOf { it.rainChance }
        val precipitation = hours.sumOf { it.precipitation }

        reward("Sıcaklık ${t.roundToInt()}°", ((t - 5) / 20).coerceIn(0.0, 1.0) * 30)
        reward("Nem %${humidity.roundToInt()}", ((90 - humidity) / 50).coerceIn(0.0, 1.0) * 30)
        reward("Rüzgar ${wind.roundToInt()} km/sa", (wind / 20).coerceIn(0.0, 1.0) * 20)
        reward("Güneş", (100 - cloud) / 100 * 20)
        if (humidity > 80) penalize("Nem çok yüksek", 10.0, "Nem yüksek: çamaşırlar yavaş kurur, aralıklı as")
        if (wind > 40) penalize("Sert rüzgar", 15.0, "Rüzgar sert: mandalları sağlam tak")
        if (rain >= 50 || precipitation >= 0.5) {
            cap("Yağış bekleniyor %$rain", 15.0, "Yağmur bekleniyor: çamaşırı içeride kurut")
        } else if (rain >= 30) {
            penalize("Yağış olasılığı %$rain", 25.0, "Yağmur ihtimali var: gözün çamaşırda olsun")
        }
    }

    private fun laundry(input: Input): LifestyleIndex {
        // Art arda 3 gündüz saatinden oluşan pencereler içinde en iyisi
        val best = input.upcoming
            .windowed(3)
            .filter { window -> window.all { it.isDay } }
            .map { it to laundryCard(it) }
            .maxByOrNull { it.second.value }

        if (best == null) {
            val card = laundryCard(listOf(input.now))
            return index(
                UserInterest.Laundry,
                card,
                "Gün ışığı yok: iç mekanda kurut",
                "Önümüzdeki 24 saatte uygun gündüz penceresi bulunamadı",
                null
            )
        }
        val (hours, card) = best
        val window = TimeWindow(hours.first().epochSeconds, hours.last().epochSeconds + 3600)
        val headline = if (card.value >= 50) {
            "En iyi kurutma: ${formatWindow(window, input.offset)}"
        } else {
            "Kurutma için zayıf gün"
        }
        return index(UserInterest.Laundry, card, headline, card.mainTip("Güneş ve rüzgar birlikte: çamaşır 2–3 saatte kurur"), window)
    }

    // ---------------------------------------------------------------- Bahçecilik

    private fun gardening(input: Input): LifestyleIndex {
        val nowSec = input.nowEpochSeconds
        val minNext24 = input.upcoming.minOfOrNull { it.temperature } ?: input.now.temperature
        val pastRain = input.snapshot.hourly.filter { it.epochSeconds in (nowSec - 24 * 3600) until nowSec }.sumOf { it.precipitation }
        val nextRain = input.upcoming.sumOf { it.precipitation }
        val h = input.now

        val card = ScoreCard(start = 90.0).apply {
            if (minNext24 <
                2
            ) {
                penalize("Don riski (${minNext24.roundToInt()}°)", 40.0, "Don bekleniyor: hassas bitkileri ört, saksıları içeri al")
            }
            if (h.temperature < 8) penalize("Soğuk", (8 - h.temperature) * 3)
            if (h.temperature > 28) penalize("Sıcak", (h.temperature - 28) * 4, "Sulamayı akşam serinliğine bırak")
            if (pastRain >= 5) reward("Son 24 saatte ${pastRain.oneDecimal()} mm yağış", 5.0, "Toprak nemli: bugün sulamaya gerek yok")
            if (nextRain >= 20) {
                penalize("Kuvvetli yağış bekleniyor", 20.0, "Ekim ve ilaçlamayı ertele, drenajı kontrol et")
            } else if (nextRain >= 2 && pastRain < 5) {
                reward("Hafif yağış geliyor", 5.0, "Yağmur öncesi ekim ve gübreleme için ideal")
            }
            if (h.windSpeed > 30) penalize("Rüzgar ${h.windSpeed.roundToInt()} km/sa", 15.0, "Rüzgarda ilaçlama yapma: sürüklenir")
            if (h.uvIndex > 7) penalize("UV ${h.uvIndex.roundToInt()}", 10.0, "Öğle güneşinde çalışma, şapka tak")
            if (WeatherCode.isThunder(h.weatherCode)) cap("Gök gürültüsü", 10.0, "Fırtınada bahçede durma")
        }
        val headline = when (card.level) {
            RiskLevel.Excellent, RiskLevel.Good -> "Bahçe işleri için güzel bir gün"
            RiskLevel.Moderate -> "Bahçede dikkatli çalış"
            else -> "Bahçe işlerini ertele"
        }
        return index(UserInterest.Gardening, card, headline, card.mainTip("Toprak ve hava dengede: budama ve dikim yapılabilir"), null)
    }

    // ---------------------------------------------------------------- Sürüş güvenliği

    private fun drivingCard(h: HourlyPoint): ScoreCard = ScoreCard().apply {
        val visibility = h.visibilityMeters
        when {
            visibility < 200 -> penalize(
                "Görüş ${visibility.roundToInt()} m",
                60.0,
                "Görüş çok düşük: sis farlarını aç, takip mesafesini üçe katla"
            )
            visibility < 1000 -> penalize("Görüş ${visibility.roundToInt()} m", 35.0, "Görüş düşük: kısa far ve yavaş tempo")
            visibility < 5000 -> penalize("Görüş ${(visibility / 1000).oneDecimal()} km", 15.0)
        }
        if (WeatherCode.isFreezing(
                h.weatherCode
            )
        ) {
            penalize("Dondurucu yağış", 50.0, "Dondurucu yağış: yollar cam gibi olabilir, zorunlu değilse çıkma")
        }
        if (WeatherCode.isSnow(
                h.weatherCode
            )
        ) {
            penalize("Kar", 35 + h.precipitation * 5, "Kar var: kış lastiği ve zincir olmadan yola çıkma")
        }
        if (h.temperature <= 1 && (h.precipitation > 0 || h.humidity >= 90)) {
            penalize("Buzlanma riski (${h.temperature.roundToInt()}°)", 35.0, "Köprü ve viyadüklerde gizli buzlanmaya dikkat")
        }
        if (h.precipitation >= 4) {
            penalize("Kuvvetli yağış", 30.0, "Kuvvetli yağış: su kızağına karşı hızını düşür")
        } else if (h.precipitation >= 0.5) {
            penalize("Islak yol", 12.0, "Islak zemin: fren mesafesi uzar")
        }
        if (h.windGusts >=
            50
        ) {
            penalize(
                "Rüzgar hamlesi ${h.windGusts.roundToInt()} km/sa",
                15 + (h.windGusts - 50) * 0.8,
                "Yüksek araçlarda hızını düşür, köprülerde dikkat"
            )
        }
        if (WeatherCode.isThunder(h.weatherCode)) penalize("Gök gürültülü fırtına", 25.0, "Fırtına: ani sağanak ve dolu olabilir")
    }

    private fun driving(input: Input): LifestyleIndex {
        // Önümüzdeki 3 saatin en kötüsü belirleyici
        val hours = listOf(input.now) + input.upcoming.drop(1).take(2)
        val card = hours.map { drivingCard(it) }.minBy { it.value }
        val headline = when (card.level) {
            RiskLevel.Excellent -> "Sürüş koşulları güvenli"
            RiskLevel.Good -> "Yollar genel olarak iyi"
            RiskLevel.Moderate -> "Dikkatli sür"
            else -> "Sürüş için riskli koşullar"
        }
        return index(UserInterest.Driving, card, headline, card.mainTip("Yol koşulları normal, iyi yolculuklar"), null)
    }

    // ---------------------------------------------------------------- Ortak

    private fun index(interest: UserInterest, card: ScoreCard, headline: String, advice: String, window: TimeWindow?) = LifestyleIndex(
        interest = interest,
        score = card.value,
        level = card.level,
        headline = headline,
        advice = advice,
        bestWindow = window,
        factors = card.topFactors()
    )

    private fun insight(indices: List<LifestyleIndex>, trend: PressureTrend): String {
        val worst = indices.filter { it.level >= RiskLevel.Poor }.minByOrNull { it.score }
        if (worst != null) return "${worst.interest.emoji} ${worst.headline}. ${worst.advice}"
        if (trend.tendency == PressureTendency.FallingFast) {
            return "↘ Basınç hızla düşüyor: hava birkaç saat içinde bozabilir"
        }
        val best = indices.filter { it.bestWindow != null }.maxByOrNull { it.score }
            ?: indices.maxByOrNull { it.score }
        return best?.let { "${it.interest.emoji} ${it.headline}. ${it.advice}" }
            ?: "İlgi alanlarını seç, sana özel öneriler burada görünsün"
    }

    companion object {
        // Mevcut koşulları saatlik nokta biçimine çevirir; anlık değerler saatlik tahminden daha taze
        fun currentHour(snapshot: WeatherSnapshot, upcoming: List<HourlyPoint>): HourlyPoint {
            val c = snapshot.current
            val base = upcoming.firstOrNull()
            return HourlyPoint(
                epochSeconds = c.epochSeconds,
                temperature = c.temperature,
                apparentTemperature = c.apparentTemperature,
                humidity = c.humidity,
                dewPoint = base?.dewPoint ?: dewPoint(c.temperature, c.humidity),
                pressureMsl = c.pressureMsl,
                cloudCover = c.cloudCover,
                visibilityMeters = c.visibilityMeters,
                windSpeed = c.windSpeed,
                windGusts = c.windGusts,
                uvIndex = c.uvIndex,
                rainChance = c.rainChance,
                precipitation = c.precipitation,
                weatherCode = c.weatherCode,
                isDay = c.isDay,
                isExtrapolated = base?.isExtrapolated ?: false
            )
        }

        // Magnus formülü ile çiy noktası
        fun dewPoint(temperature: Double, humidity: Int): Double {
            val a = 17.62
            val b = 243.12
            val gamma = ln(humidity.coerceIn(1, 100) / 100.0) + a * temperature / (b + temperature)
            return b * gamma / (a - gamma)
        }

        fun pressureTrend(snapshot: WeatherSnapshot, nowEpochSeconds: Long): PressureTrend {
            val current = snapshot.current.pressureMsl
            val hours = snapshot.hourly

            // En yakın saatlik ölçüm, en fazla 1,5 saat uzaktaysa
            fun pressureAt(epochSeconds: Long): Double? = hours
                .minByOrNull { abs(it.epochSeconds - epochSeconds) }
                ?.takeIf { abs(it.epochSeconds - epochSeconds) <= 5400 }
                ?.pressureMsl

            val p3 = pressureAt(nowEpochSeconds - 3 * 3600) ?: current
            val p24 = pressureAt(nowEpochSeconds - 24 * 3600) ?: p3
            val lowestNext6 = hours
                .filter { it.epochSeconds in nowEpochSeconds..(nowEpochSeconds + 6 * 3600) }
                .minOfOrNull { it.pressureMsl } ?: current

            val change3 = current - p3
            val forecast6 = (lowestNext6 - current).coerceAtMost(0.0)
            val tendency = when {
                change3 <= -3 || (change3 <= -1.5 && forecast6 <= -3) -> PressureTendency.FallingFast
                change3 <= -1 -> PressureTendency.Falling
                change3 >= 1 -> PressureTendency.Rising
                else -> PressureTendency.Steady
            }
            return PressureTrend(
                currentHpa = current,
                change3hHpa = change3,
                change24hHpa = current - p24,
                forecastChange6hHpa = forecast6,
                tendency = tendency
            )
        }
    }
}
