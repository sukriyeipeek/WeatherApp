# Release derlemesinde R8 kullanilmayan kodu siler ve isimleri kisaltir.
# Asagidaki kurallar, calisma aninda yansima (reflection) ile erisilen seyleri korur.

# --- kotlinx.serialization ---
# @Serializable siniflarin derleyicinin urettigi serializer'lari korunmali.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.sukriye.havam.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.sukriye.havam.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit ---
# Servis arayuzleri yansima ile uygulanir.
-keep,allowobfuscation interface com.sukriye.havam.data.ForecastApi
-keep,allowobfuscation interface com.sukriye.havam.data.CityApi
-keep,allowobfuscation interface com.sukriye.havam.data.AirQualityApi
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
  @retrofit2.http.* <methods>;
}

# Retrofit'in opsiyonel bagimliliklarina dair uyarilar derlemeyi kesmesin.
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
