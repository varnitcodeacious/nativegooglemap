# Google Maps setup

The app reads `MAPS_API_KEY` from the root `local.properties` file. Add your Google Maps Platform key there:

```properties
MAPS_API_KEY=your_key_here
```

Enable **Maps SDK for Android** in the Google Cloud project and enable billing. Restrict the key to this Android application (`com.example.nativemap`) and the debug or release signing certificate SHA-1. The Maps screen cannot load tiles until a valid key is configured.

On launch, the map is centered to show Noida Sector 101 and Kashmere Gate. In **Mock · drag** mode, drag the blue driver marker to move it on the map. **Use my location** requests Android location permission and follows GPS/network updates, smoothing marker movement between fixes.
