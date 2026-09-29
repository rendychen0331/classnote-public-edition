# Feature module API — shared via DexClassLoader parent classloader, must not be renamed
-keep class com.rendy.classnote.feature.** { *; }

# Room entities
-keep class com.rendy.classnote.data.local.entity.** { *; }
-keep class com.rendy.classnote.data.local.dao.** { *; }

# WorkManager — workers instantiated by class name via reflection
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Quick Settings Tile
-keep class * extends android.service.quicksettings.TileService { *; }

# Kotlin coroutines internal fields
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
