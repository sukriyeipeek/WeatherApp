package com.sukriye.havam.ui.component

import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext

/**
 * Atmosfer arka planı. Android 13+ cihazlarda tek bir AGSL shader'ı tüm ekranı GPU'da çizer:
 * güneş huzmeleri, cihaz eğimine göre akan yağmur ve kar, cama yapışıp kayan damlalar,
 * fbm gürültüsüyle bulut/sis ve şimşek. Eski cihazlarda Canvas parçacık animasyonuna düşer.
 *
 * @param intensity 0..1 arası şiddet (yağış miktarı, bulutluluk vb.)
 * @param wind 0..1 arası rüzgar şiddeti: 0'da rüzgar efekti yok, 1'de fırtına gibi eser
 */
@Composable
fun AtmosphericBackground(weatherCode: Int, isDay: Boolean, intensity: Float, wind: Float = 0f, modifier: Modifier = Modifier) {
    val scene = sceneFor(weatherCode, isDay)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val tilt by rememberDeviceTilt()
        ShaderAtmosphere(scene = scene, intensity = intensity, wind = wind, tilt = { tilt }, modifier = modifier)
    } else {
        Box(modifier = modifier.background(Brush.verticalGradient(scene.colors))) {
            WeatherEffects(effect = scene.effect, wind = wind, modifier = Modifier.matchParentSize())
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderAtmosphere(scene: WeatherScene, intensity: Float, wind: Float, tilt: () -> Offset, modifier: Modifier) {
    val shader = remember { RuntimeShader(ATMOSPHERE_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }

    // Her karede güncellenen zaman; yalnızca çizim aşamasında okunur, yeniden kompozisyon olmaz.
    // Ekran görünmezken withFrameNanos askıda kalır, yani arka planda GPU/pil harcanmaz.
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame -> time = ((frame - start) / 1_000_000_000.0 % 3600.0).toFloat() }
        }
    }

    val top = scene.colors.first()
    val bottom = scene.colors.last()
    val mode = scene.effect.shaderMode()
    Canvas(modifier = modifier) {
        val t = tilt()
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iTilt", t.x, t.y)
        shader.setFloatUniform("iMode", mode)
        shader.setFloatUniform("iIntensity", intensity.coerceIn(0f, 1f))
        shader.setFloatUniform("iWind", wind.coerceIn(0f, 1f))
        shader.setFloatUniform("iTop", top.red, top.green, top.blue, 1f)
        shader.setFloatUniform("iBottom", bottom.red, bottom.green, bottom.blue, 1f)
        drawRect(brush = brush)
    }
}

private fun WeatherEffect.shaderMode(): Float = when (this) {
    WeatherEffect.Sun -> 0f
    WeatherEffect.Rain -> 1f
    WeatherEffect.Snow -> 2f
    WeatherEffect.Storm -> 3f
    WeatherEffect.Stars -> 4f
    WeatherEffect.Clouds, WeatherEffect.Fog -> 5f
}

/**
 * Yerçekimi sensöründen cihaz eğimi: x sağa/sola, y dik tutma. -1..1 aralığında, alçak geçiren
 * filtreyle yumuşatılmış. Sensör yoksa (0,0) kalır.
 */
@Composable
fun rememberDeviceTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(context) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = (-event.values[0] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val y = (event.values[1] / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val old = tilt.value
                tilt.value = Offset(old.x + (x - old.x) * 0.08f, old.y + (y - old.y) * 0.08f)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}

// AGSL (Android Graphics Shading Language). Koordinatlar ekran yüksekliğine bölünür ki en-boy oranı korunsun.
private const val ATMOSPHERE_SHADER = """
uniform float2 iResolution;
uniform float iTime;
uniform float2 iTilt;
uniform float iMode;
uniform float iIntensity;
uniform float iWind;
uniform float4 iTop;
uniform float4 iBottom;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// Gunes huzmeleri: kaynaktan cikan acisal desen, uzaklikla sonumlenir
float sunRays(float2 uv, float2 source) {
    float2 d = uv - source;
    float r = length(d);
    float angle = atan(d.y, d.x);
    float rays = 0.5 + 0.5 * sin(angle * 14.0 + iTime * 0.35) * sin(angle * 5.0 - iTime * 0.2);
    rays = rays * rays * rays;
    return rays * exp(-r * 1.4) * 0.6 + exp(-r * 7.0);
}

// Yagmur: her sutunda farkli hizda dusen ince cizgiler; cihaz egimi akis acisini degistirir
float rainLayer(float2 uv, float density, float speed, float seed) {
    float2 p = uv;
    // Egim ve ruzgar damlalari yana yatirir
    p.x += p.y * (iTilt.x * 0.45 - iWind * 0.5);
    float column = floor(p.x * density);
    float rnd = hash(float2(column, seed));
    float local = fract(p.x * density) - 0.5;
    float fall = fract(p.y * 1.6 - iTime * speed * (0.8 + rnd * 0.6) + rnd * 7.0);
    float streak = smoothstep(0.72, 0.97, fall) * (1.0 - smoothstep(0.97, 1.0, fall));
    float width = 1.0 - smoothstep(0.0, 0.06, abs(local));
    return streak * width * step(0.35, rnd);
}

// Cama yapisip yavasca asagi kayan damlalar; telefon dik tutuldukca hizlanir
float droplets(float2 uv) {
    float2 g = uv * float2(9.0, 5.0);
    g.y -= iTime * 0.06 * max(0.2, iTilt.y);
    float2 id = floor(g);
    float2 f = fract(g) - 0.5;
    float rnd = hash(id + 3.1);
    float2 center = float2(hash(id + 1.7) - 0.5, hash(id + 8.3) - 0.5) * 0.6;
    float d = length((f - center) * float2(1.0, 1.4));
    return (1.0 - smoothstep(0.05, 0.11, d)) * step(0.6, rnd);
}

float snowLayer(float2 uv, float scale, float speed, float seed) {
    float2 p = uv * scale;
    p.x += uv.y * iTilt.x * scale * 0.3;
    // Ruzgarda kar taneleri yana savrulur
    p.x -= iTime * iWind * scale * 0.25;
    p.y -= iTime * speed;
    p.x += sin(p.y * 0.8 + iTime * 0.7 + seed) * 0.35;
    float2 id = floor(p);
    float2 f = fract(p) - 0.5;
    float rnd = hash(id + seed);
    float2 o = float2(hash(id + seed + 4.7), hash(id + seed + 1.3)) - 0.5;
    float d = length(f - o * 0.6);
    float radius = 0.04 + rnd * 0.06;
    return (1.0 - smoothstep(radius * 0.4, radius, d)) * step(0.45, rnd);
}

// Ruzgar: soldan saga hizla gecen, hafifce dalgalanan ince ve kesik cizgiler
float windLayer(float2 uv, float rows, float speed, float seed) {
    float2 p = float2(uv.x * 1.2 - iTime * speed, uv.y * rows);
    float row = floor(p.y);
    float rnd = hash(float2(row, seed));
    float local = fract(p.y) - 0.5 + sin(p.x * 3.0 + rnd * 6.28) * 0.18;
    float along = fract(p.x * 0.35 + rnd * 5.0);
    float gust = smoothstep(0.0, 0.25, along) * (1.0 - smoothstep(0.25, 0.55, along));
    float line = 1.0 - smoothstep(0.0, 0.035, abs(local));
    return gust * line * step(0.55, rnd);
}

float stars(float2 uv) {
    float2 p = uv * 60.0;
    float2 id = floor(p);
    float2 f = fract(p) - 0.5;
    float rnd = hash(id);
    float twinkle = 0.5 + 0.5 * sin(iTime * (1.0 + rnd * 3.0) + rnd * 20.0);
    return (1.0 - smoothstep(0.0, 0.08, length(f))) * step(0.985, rnd) * twinkle;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / iResolution.y;
    float v = clamp(fragCoord.y / iResolution.y, 0.0, 1.0);
    float3 color = mix(iTop.rgb, iBottom.rgb, v);
    float aspect = iResolution.x / iResolution.y;
    float2 sunPos = float2(aspect * 0.82, 0.10);
    float intensity = clamp(iIntensity, 0.0, 1.0);
    float wind = clamp(iWind, 0.0, 1.0);
    // Bulutlarin kayma zamani: ruzgar arttikca bulutlar 5 kata kadar hizli akar
    float drift = iTime * (1.0 + wind * 4.0);

    if (iMode < 0.5) {
        // Acik gunduz: egimle hafifce kayan gunes ve huzmeler
        float rays = sunRays(uv, sunPos + iTilt * 0.03);
        color += float3(1.0, 0.86, 0.55) * rays * (0.45 + intensity * 0.2);
        color += float3(1.0) * fbm(uv * 2.0 + float2(drift * 0.02, 0.0)) * 0.05;
    } else if (iMode < 1.5) {
        // Yagmur: koyulasan bulutlar + iki katman damla + cam ustu damlalar
        float clouds = fbm(uv * 1.6 + float2(drift * 0.03, 0.0));
        color = mix(color, color * 0.75, clouds * 0.6);
        float r = rainLayer(uv, 38.0, 1.6, 1.0) * 0.55 + rainLayer(uv, 70.0, 2.4, 2.0) * 0.35;
        color += float3(0.75, 0.85, 1.0) * r * (0.5 + intensity);
        color += float3(0.9, 0.95, 1.0) * droplets(uv) * 0.12;
    } else if (iMode < 2.5) {
        // Kar: farkli derinlikte uc katman
        float s = snowLayer(uv, 7.0, 0.35, 1.0)
            + snowLayer(uv, 12.0, 0.55, 7.0) * 0.7
            + snowLayer(uv, 20.0, 0.8, 13.0) * 0.45;
        color += float3(1.0) * s * (0.6 + intensity * 0.4);
    } else if (iMode < 3.5) {
        // Firtina: hizli bulutlar, yogun yagmur ve rastgele simsek
        float clouds = fbm(uv * 1.3 + float2(drift * 0.06, iTime * 0.01));
        color = mix(color * 0.6, color, clouds);
        float r = rainLayer(uv, 45.0, 2.4, 3.0) * 0.6 + rainLayer(uv, 85.0, 3.2, 4.0) * 0.4;
        color += float3(0.8, 0.85, 1.0) * r * (0.7 + intensity);
        float beat = floor(iTime * 0.7);
        float flash = step(0.82, hash(float2(beat, 5.0))) * exp(-fract(iTime * 0.7) * 9.0);
        color += float3(0.85, 0.88, 1.0) * flash * (0.6 + 0.4 * fbm(uv * 3.0 + beat));
    } else if (iMode < 4.5) {
        // Gece: yildizlar ve ay isigi
        color += float3(1.0) * stars(uv) * (1.0 - v);
        color += float3(0.75, 0.8, 1.0) * exp(-length(uv - sunPos) * 5.0) * 0.35;
    } else {
        // Bulut / sis: iki olcekte kayan fbm
        float c1 = fbm(uv * 1.8 + float2(drift * 0.025, 0.0));
        float c2 = fbm(uv * 3.5 - float2(drift * 0.04, 0.0));
        float clouds = smoothstep(0.35, 0.85, c1 * 0.7 + c2 * 0.3);
        color = mix(color, float3(1.0), clouds * (0.18 + intensity * 0.3));
    }

    // Ruzgar her havada ustte eser: yakin katman hizli ve belirgin, uzak katman yavas ve soluk
    if (wind > 0.0) {
        float w = windLayer(uv, 14.0, 0.9 + wind * 1.4, 21.0) * 0.6
            + windLayer(uv, 26.0, 0.5 + wind * 0.8, 37.0) * 0.35;
        // Hafif esintide de fark edilsin diye taban parlaklik var
        color += float3(1.0) * w * (0.12 + wind * 0.4);
    }

    // Hafif vinyet: kenarlar koyulasir, kartlar one cikar
    float2 centered = fragCoord / iResolution - 0.5;
    color *= 1.0 - dot(centered, centered) * 0.45;
    return half4(half3(color), 1.0);
}
"""
