package com.karadaglabs.cartv

/**
 * Park hâlinde video kuralı (Google "parked apps" / video kategorisi).
 *
 * Araç hareket etmeye başladığında Android Automotive OS ve Android
 * Auto'nun video desteği, park uygulamasının aktivitesini bir engelleme
 * katmanıyla örter ve onPause (uyumluluk modunda ayrıca onStop)
 * çağırır. Video uygulaması bu anda oynatmayı DURDURMALI ve sürüş
 * sırasında yeniden başlatmamalıdır (car-app-quality DD-2 / DD-3).
 * Hız veya vites bilgisinden kısıtlama tahmini yapılmaz; tek sinyal
 * yaşam döngüsüdür.
 *
 * Saf Kotlin: Android sınıfına bağlı değil, JVM unit testinde çalışır.
 */
class ParkedPlaybackPolicy {

    /** Aktivite sistem tarafından örtülü/arka planda mı. */
    var isHidden: Boolean = false
        private set

    private var resumeWhenVisible: Boolean = false

    /**
     * onPause. Dönen değer true ise oynatıcı hemen duraklatılmalıdır.
     */
    fun onHidden(wasPlaying: Boolean): Boolean {
        isHidden = true
        resumeWhenVisible = resumeWhenVisible || wasPlaying
        return true
    }

    /**
     * onResume. Dönen değer true ise oynatma, görünür olunduğu için
     * kaldığı yerden sürdürülebilir (park hâline dönüş).
     */
    fun onVisible(): Boolean {
        isHidden = false
        val resume = resumeWhenVisible
        resumeWhenVisible = false
        return resume
    }

    /**
     * Tamponlama bittiğinde (STATE_READY) veya canlı yayın tekrar
     * başlatılırken otomatik oynatmaya izin var mı.
     */
    fun mayAutoPlay(): Boolean = !isHidden
}
