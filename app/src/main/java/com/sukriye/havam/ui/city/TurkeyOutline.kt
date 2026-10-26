package com.sukriye.havam.ui.city

// Türkiye sınırının basitleştirilmiş hâli: (boylam, enlem) çiftleri sırayla.
// Kaynak: Natural Earth (kamu malı) verisi, github.com/johan/world.geo.json üzerinden.
// Harita ekranında il noktalarının arkasına çizilir.
val turkeyOutline: List<List<Pair<Double, Double>>> = listOf(
    // Anadolu
    listOf(
        36.91 to 41.34, 38.35 to 40.95, 39.51 to 41.10, 40.37 to 41.01, 41.55 to 41.54,
        42.62 to 41.58, 43.58 to 41.09, 43.75 to 40.74, 43.66 to 40.25, 44.40 to 40.01,
        44.79 to 39.71, 44.11 to 39.43, 44.42 to 38.28, 44.23 to 37.97, 44.77 to 37.17,
        44.29 to 37.00, 43.94 to 37.26, 42.78 to 37.39, 42.35 to 37.23, 41.21 to 37.07,
        40.67 to 37.09, 39.52 to 36.72, 38.70 to 36.71, 38.17 to 36.90, 37.07 to 36.62,
        36.74 to 36.82, 36.69 to 36.26, 36.42 to 36.04, 36.15 to 35.82, 35.78 to 36.27,
        36.16 to 36.65, 35.55 to 36.57, 34.71 to 36.80, 34.03 to 36.22, 32.51 to 36.11,
        31.70 to 36.64, 30.62 to 36.68, 30.39 to 36.26, 29.70 to 36.14, 28.73 to 36.68,
        27.64 to 36.66, 27.05 to 37.65, 26.32 to 38.21, 26.80 to 38.99, 26.17 to 39.46,
        27.28 to 40.42, 28.82 to 40.46, 29.24 to 41.22, 31.15 to 41.09, 32.35 to 41.74,
        33.51 to 42.02, 35.17 to 42.04, 36.91 to 41.34
    ),
    // Trakya
    listOf(
        27.19 to 40.69, 26.36 to 40.15, 26.04 to 40.62, 26.06 to 40.82, 26.29 to 40.94,
        26.60 to 41.56, 26.12 to 41.83, 27.14 to 42.14, 28.00 to 42.01, 28.12 to 41.62,
        28.99 to 41.30, 28.81 to 41.05, 27.62 to 41.00, 27.19 to 40.69
    )
)
