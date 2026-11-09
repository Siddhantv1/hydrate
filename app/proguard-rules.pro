# Enum names are persisted in DataStore (HydrateStore stores Gender/VolumeUnit by name), so they must not be renamed.
-keepclassmembers enum com.example.waterreminder.domain.** { *; }
