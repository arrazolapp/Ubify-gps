package com.arrazolapp.gpstracker

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.webkit.*
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*

class MapActivity : AppCompatActivity() {

    private lateinit var webMap: WebView
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var isDark = true

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "GPS_UPDATE") {
                val lat = intent.getDoubleExtra("lat", 0.0)
                val lng = intent.getDoubleExtra("lng", 0.0)
                val accuracy = intent.getIntExtra("accuracy", 20)
                val speed = intent.getIntExtra("speed", 0)
                webMap.evaluateJavascript("onGpsUpdate($lat,$lng,$accuracy,${speed/3.6})", null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        webMap = findViewById(R.id.webMap)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnTheme = findViewById<Button>(R.id.btnTheme)

        btnBack.setOnClickListener { finish() }
        btnTheme.setOnClickListener {
            isDark = !isDark
            webMap.evaluateJavascript("toggleTheme()", null)
            btnTheme.text = if (isDark) "🌓" else "☀️"
        }

        webMap.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = true
        }
        webMap.webChromeClient = WebChromeClient()

        // ── NUEVO: Interceptar URLs externas (Waze, Google Maps, etc.) ──
        webMap.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val prefs = getSharedPreferences("agent_config", MODE_PRIVATE)
                val company = prefs.getString("company", "demo_corp") ?: "demo_corp"
                val userId  = prefs.getString("userId", "") ?: ""
                val nombre  = prefs.getString("nombre", "Agente") ?: "Agente"
                webMap.evaluateJavascript("setConfig('$company','$userId','$nombre')", null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleExternalUrl(url)
            }

            // Para versiones antiguas de Android
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false
                return handleExternalUrl(url)
            }
        }

        // ── NUEVO: JavascriptInterface para abrir apps externas ──
        webMap.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun openWaze(lat: Double, lng: Double) {
                runOnUiThread {
                    // Primero intentar abrir la app nativa de Waze
                    val wazeUri = Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
                    val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri)
                    if (wazeIntent.resolveActivity(packageManager) != null) {
                        startActivity(wazeIntent)
                    } else {
                        // Si no tiene Waze, abrir en Play Store
                        val playIntent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.waze"))
                        startActivity(playIntent)
                    }
                }
            }

            @JavascriptInterface
            fun openGoogleMaps(lat: Double, lng: Double) {
                runOnUiThread {
                    // Intentar abrir Google Maps nativo
                    val gmmUri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
                    val gmmIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (gmmIntent.resolveActivity(packageManager) != null) {
                        startActivity(gmmIntent)
                    } else {
                        // Fallback a navegador
                        val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving")
                        startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                    }
                }
            }

            @JavascriptInterface
            fun fetchRoute(originLat: Double, originLng: Double, destLat: Double, destLng: Double) {
                // Llamar OSRM desde Kotlin (sin restricción CORS)
                Thread {
                    try {
                        val url = java.net.URL(
                            "https://router.project-osrm.org/route/v1/driving/" +
                            "$originLng,$originLat;$destLng,$destLat" +
                            "?overview=full&geometries=geojson"
                        )
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        val json = conn.inputStream.bufferedReader().readText()
                        conn.disconnect()
                        // Devolver resultado al JS en el hilo principal
                        runOnUiThread {
                            // Pasar JSON via variable JS para evitar problemas de escape
                            val jsCode = "window._routeJson = " + json + "; onRouteResultFromAndroid();"
                            webMap.evaluateJavascript(jsCode, null)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            webMap.evaluateJavascript("onRouteError()", null)
                        }
                    }
                }.start()
            }
        }, "Android")

        webMap.loadUrl("file:///android_asset/map_agent.html")

        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        val locReq = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val bearing = if (loc.hasBearing()) loc.bearing else 0f
                val speed   = if (loc.hasSpeed()) loc.speed else 0f
                webMap.evaluateJavascript(
                    "updateUserPosition(${loc.latitude},${loc.longitude},${loc.accuracy.toInt()},$speed,$bearing)", null
                )
            }
        }

        try {
            fusedClient.requestLocationUpdates(locReq, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {}
    }

    private fun handleExternalUrl(url: String): Boolean {
        return when {
            url.startsWith("waze://") || url.startsWith("https://waze.com") || url.startsWith("https://www.waze.com") -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.waze")))
                }
                true
            }
            url.startsWith("google.navigation:") || (url.contains("google.com/maps") && !url.startsWith("file://")) -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {}
                true
            }
            url.startsWith("http://") || url.startsWith("https://") -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {}
                true
            }
            url.startsWith("file://") -> false  // Dejar que el WebView lo maneje
            else -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {}
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("GPS_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gpsReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(gpsReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(gpsReceiver) } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    }
}
