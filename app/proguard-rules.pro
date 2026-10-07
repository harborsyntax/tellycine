# Room, Media3, OkHttp and Coil ship their own consumer rules.
# Gson is only used through its streaming JsonReader (no reflection), so no extra rules are needed.
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.**

# The Cast options provider is only referenced by name from the manifest.
-keep class app.mosaic.cast.CastOptionsProvider { *; }
