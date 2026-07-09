package com.solar.dualscreen.util

/**
 * 太阳系天体数据：轨道参数 + 物理参数 + 纹理URL
 * 所有星球统一使用单张等距矩形投影纹理，不做瓦片区分。
 */
object PlanetData {

    /** 纹理基础 URL */
    const val TEXTURE_BASE = "https://www.solarsystemscope.com/textures/download/"

    /**
     * 行星/卫星定义
     * @param name 中文名
     * @param nameEn 英文名
     * @param orbitRadius 轨道半长轴 (AU)
     * @param orbitPeriod 公转周期 (地球日)
     * @param eccentricity 轨道偏心率
     * @param inclination 轨道倾角 (度)
     * @param radiusKm 实际半径 (km)
     * @param rotationPeriod 自转周期 (地球日)
     * @param axialTilt 轴倾角 (度)
     * @param texture2k 2K 纹理文件名
     * @param texture8k 8K 纹理文件名 (null 表示不可用)
     * @param color 程序化 fallback 颜色 RGB
     * @param moons 卫星列表
     * @param hasRing 是否有光环
     * @param ringInnerRatio 光环内径/行星半径
     * @param ringOuterRatio 光环外径/行星半径
     * @param ringTexture 光环纹理文件名
     */
    data class CelestialBody(
        val name: String,
        val nameEn: String,
        val orbitRadius: Float,       // AU
        val orbitPeriod: Float,       // 地球日
        val eccentricity: Float = 0f,
        val inclination: Float = 0f,  // 度
        val radiusKm: Float,
        val rotationPeriod: Float,    // 地球日 (自转)
        val axialTilt: Float = 0f,    // 度
        val texture2k: String? = null,
        val texture8k: String? = null,
        val color: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f),
        val moons: List<CelestialBody> = emptyList(),
        val hasRing: Boolean = false,
        val ringInnerRatio: Float = 0f,
        val ringOuterRatio: Float = 0f,
        val ringTexture: String? = null
    )

    // ==================== 太阳 ====================
    val SUN = CelestialBody(
        name = "太阳", nameEn = "Sun",
        orbitRadius = 0f, orbitPeriod = 0f,
        radiusKm = 696340f, rotationPeriod = 25.38f,
        axialTilt = 7.25f,
        texture2k = "2k_sun.jpg",
        texture8k = "8k_sun.jpg",
        color = floatArrayOf(1f, 0.85f, 0.2f)
    )

    // ==================== 水星 ====================
    val MERCURY = CelestialBody(
        name = "水星", nameEn = "Mercury",
        orbitRadius = 0.387f, orbitPeriod = 87.97f,
        eccentricity = 0.2056f, inclination = 7.0f,
        radiusKm = 2439.7f, rotationPeriod = 58.65f,
        axialTilt = 0.034f,
        texture2k = "2k_mercury.jpg",
        texture8k = "8k_mercury.jpg",
        color = floatArrayOf(0.7f, 0.7f, 0.7f)
    )

    // ==================== 金星 ====================
    val VENUS = CelestialBody(
        name = "金星", nameEn = "Venus",
        orbitRadius = 0.723f, orbitPeriod = 224.7f,
        eccentricity = 0.0068f, inclination = 3.39f,
        radiusKm = 6051.8f, rotationPeriod = -243.0f,  // 逆行
        axialTilt = 177.4f,
        texture2k = "2k_venus_atmosphere.jpg",
        texture8k = null,  // 大气只有 2K/4K
        color = floatArrayOf(0.9f, 0.8f, 0.5f)
    )

    // ==================== 地球 ====================
    val EARTH = CelestialBody(
        name = "地球", nameEn = "Earth",
        orbitRadius = 1.0f, orbitPeriod = 365.25f,
        eccentricity = 0.0167f, inclination = 0f,
        radiusKm = 6371f, rotationPeriod = 0.9973f,
        axialTilt = 23.44f,
        texture2k = "2k_earth_daymap.jpg",
        texture8k = "8k_earth_daymap.jpg",
        color = floatArrayOf(0.3f, 0.5f, 0.9f),
        moons = listOf(
            CelestialBody(
                name = "月球", nameEn = "Moon",
                orbitRadius = 0.00257f, orbitPeriod = 27.32f,
                eccentricity = 0.0549f, inclination = 5.14f,
                radiusKm = 1737.4f, rotationPeriod = 27.32f,
                texture2k = "2k_moon.jpg",
                texture8k = "8k_moon.jpg",
                color = floatArrayOf(0.6f, 0.6f, 0.6f)
            )
        )
    )

    // ==================== 火星 ====================
    val MARS = CelestialBody(
        name = "火星", nameEn = "Mars",
        orbitRadius = 1.524f, orbitPeriod = 686.98f,
        eccentricity = 0.0934f, inclination = 1.85f,
        radiusKm = 3389.5f, rotationPeriod = 1.026f,
        axialTilt = 25.19f,
        texture2k = "2k_mars.jpg",
        texture8k = "8k_mars.jpg",
        color = floatArrayOf(0.8f, 0.3f, 0.1f),
        moons = listOf(
            CelestialBody(
                name = "火卫一", nameEn = "Phobos",
                orbitRadius = 0.0000628f, orbitPeriod = 0.319f,
                radiusKm = 11.1f, rotationPeriod = 0.319f,
                color = floatArrayOf(0.5f, 0.4f, 0.3f)
            ),
            CelestialBody(
                name = "火卫二", nameEn = "Deimos",
                orbitRadius = 0.000157f, orbitPeriod = 1.263f,
                radiusKm = 6.2f, rotationPeriod = 1.263f,
                color = floatArrayOf(0.5f, 0.45f, 0.35f)
            )
        )
    )

    // ==================== 木星 ====================
    val JUPITER = CelestialBody(
        name = "木星", nameEn = "Jupiter",
        orbitRadius = 5.203f, orbitPeriod = 4332.59f,
        eccentricity = 0.0484f, inclination = 1.305f,
        radiusKm = 69911f, rotationPeriod = 0.4135f,
        axialTilt = 3.13f,
        texture2k = "2k_jupiter.jpg",
        texture8k = "8k_jupiter.jpg",
        color = floatArrayOf(0.85f, 0.75f, 0.55f),
        moons = listOf(
            CelestialBody(  // 伽利略卫星
                name = "木卫一", nameEn = "Io",
                orbitRadius = 0.00283f, orbitPeriod = 1.769f,
                radiusKm = 1821.6f, rotationPeriod = 1.769f,
                color = floatArrayOf(0.9f, 0.85f, 0.2f)
            ),
            CelestialBody(
                name = "木卫二", nameEn = "Europa",
                orbitRadius = 0.0045f, orbitPeriod = 3.551f,
                radiusKm = 1560.8f, rotationPeriod = 3.551f,
                color = floatArrayOf(0.8f, 0.85f, 0.9f)
            ),
            CelestialBody(
                name = "木卫三", nameEn = "Ganymede",
                orbitRadius = 0.0072f, orbitPeriod = 7.155f,
                radiusKm = 2634.1f, rotationPeriod = 7.155f,
                color = floatArrayOf(0.6f, 0.6f, 0.65f)
            ),
            CelestialBody(
                name = "木卫四", nameEn = "Callisto",
                orbitRadius = 0.0126f, orbitPeriod = 16.689f,
                radiusKm = 2410.3f, rotationPeriod = 16.689f,
                color = floatArrayOf(0.4f, 0.4f, 0.45f)
            )
        )
    )

    // ==================== 土星 ====================
    val SATURN = CelestialBody(
        name = "土星", nameEn = "Saturn",
        orbitRadius = 9.537f, orbitPeriod = 10759.22f,
        eccentricity = 0.0539f, inclination = 2.485f,
        radiusKm = 58232f, rotationPeriod = 0.444f,
        axialTilt = 26.73f,
        texture2k = "2k_saturn.jpg",
        texture8k = "8k_saturn.jpg",
        color = floatArrayOf(0.9f, 0.8f, 0.55f),
        hasRing = true,
        ringInnerRatio = 1.2f,
        ringOuterRatio = 2.25f,
        ringTexture = "2k_saturn_ring_alpha.png",
        moons = listOf(
            CelestialBody(
                name = "土卫六", nameEn = "Titan",
                orbitRadius = 0.00817f, orbitPeriod = 15.945f,
                radiusKm = 2574.7f, rotationPeriod = 15.945f,
                color = floatArrayOf(0.85f, 0.7f, 0.3f)
            ),
            CelestialBody(
                name = "土卫二", nameEn = "Enceladus",
                orbitRadius = 0.00159f, orbitPeriod = 1.370f,
                radiusKm = 252.1f, rotationPeriod = 1.370f,
                color = floatArrayOf(0.9f, 0.9f, 0.95f)
            ),
            CelestialBody(
                name = "土卫一", nameEn = "Mimas",
                orbitRadius = 0.00124f, orbitPeriod = 0.942f,
                radiusKm = 198.2f, rotationPeriod = 0.942f,
                color = floatArrayOf(0.7f, 0.7f, 0.7f)
            )
        )
    )

    // ==================== 天王星 ====================
    val URANUS = CelestialBody(
        name = "天王星", nameEn = "Uranus",
        orbitRadius = 19.19f, orbitPeriod = 30687.15f,
        eccentricity = 0.0473f, inclination = 0.772f,
        radiusKm = 25362f, rotationPeriod = -0.718f,
        axialTilt = 97.77f,
        texture2k = "2k_uranus.jpg",
        texture8k = null,
        color = floatArrayOf(0.5f, 0.8f, 0.85f),
        moons = listOf(
            CelestialBody(
                name = "天卫五", nameEn = "Miranda",
                orbitRadius = 0.00087f, orbitPeriod = 1.413f,
                radiusKm = 235.8f, rotationPeriod = 1.413f,
                color = floatArrayOf(0.6f, 0.6f, 0.65f)
            ),
            CelestialBody(
                name = "天卫一", nameEn = "Ariel",
                orbitRadius = 0.00128f, orbitPeriod = 2.520f,
                radiusKm = 578.9f, rotationPeriod = 2.520f,
                color = floatArrayOf(0.55f, 0.55f, 0.6f)
            ),
            CelestialBody(
                name = "天卫二", nameEn = "Umbriel",
                orbitRadius = 0.00178f, orbitPeriod = 4.144f,
                radiusKm = 584.7f, rotationPeriod = 4.144f,
                color = floatArrayOf(0.4f, 0.4f, 0.45f)
            ),
            CelestialBody(
                name = "天卫三", nameEn = "Titania",
                orbitRadius = 0.00292f, orbitPeriod = 8.706f,
                radiusKm = 788.9f, rotationPeriod = 8.706f,
                color = floatArrayOf(0.5f, 0.5f, 0.55f)
            ),
            CelestialBody(
                name = "天卫四", nameEn = "Oberon",
                orbitRadius = 0.0039f, orbitPeriod = 13.463f,
                radiusKm = 761.4f, rotationPeriod = 13.463f,
                color = floatArrayOf(0.45f, 0.45f, 0.5f)
            )
        )
    )

    // ==================== 海王星 ====================
    val NEPTUNE = CelestialBody(
        name = "海王星", nameEn = "Neptune",
        orbitRadius = 30.07f, orbitPeriod = 60190f,
        eccentricity = 0.0086f, inclination = 1.769f,
        radiusKm = 24622f, rotationPeriod = 0.671f,
        axialTilt = 28.32f,
        texture2k = "2k_neptune.jpg",
        texture8k = null,
        color = floatArrayOf(0.2f, 0.4f, 0.9f),
        moons = listOf(
            CelestialBody(
                name = "海卫一", nameEn = "Triton",
                orbitRadius = 0.00237f, orbitPeriod = -5.877f,  // 逆行
                radiusKm = 1353.4f, rotationPeriod = 5.877f,
                color = floatArrayOf(0.7f, 0.75f, 0.8f)
            )
        )
    )

    // ==================== 所有行星列表 (按轨道距离排序) ====================
    val ALL_PLANETS = listOf(MERCURY, VENUS, EARTH, MARS, JUPITER, SATURN, URANUS, NEPTUNE)

    // ==================== 所有天体(含太阳) ====================
    val ALL_BODIES = listOf(SUN) + ALL_PLANETS

    /** 获取纹理完整 URL */
    fun getTextureUrl(body: CelestialBody, highRes: Boolean = false): String? {
        val filename = if (highRes && body.texture8k != null) body.texture8k else body.texture2k
        return filename?.let { TEXTURE_BASE + it }
    }
}
