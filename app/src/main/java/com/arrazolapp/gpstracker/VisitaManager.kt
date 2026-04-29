package com.arrazolapp.gpstracker

import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/**
 * VisitaManager
 *
 * Responsabilidades:
 * 1. Cargar clientes desde Firebase (companies/{company}/clientes/)
 * 2. En cada update de GPS, detectar si el agente está dentro de alguna geocerca
 * 3. Registrar hora de entrada cuando entra al radio del cliente
 * 4. Registrar hora de salida y calcular duración cuando sale
 * 5. Enviar la visita al webhook de Google Apps Script (Sheets)
 *    Y también escribir en Firebase companies/{company}/visitas/
 */
class VisitaManager(private val context: Context) {

    companion object {
        const val TAG = "VisitaManager"
        // Tiempo mínimo dentro de la geocerca para considerarse visita válida (segundos)
        const val MIN_VISITA_SEG = 60
        // Radio por defecto si el cliente no tiene radio_m definido
        const val RADIO_DEFAULT_M = 50.0
    }

    // ── Modelo de cliente ──
    data class Cliente(
        val id: String,
        val nombre: String,
        val codigo: String,
        val zona: String,
        val direccion: String,
        val lat: Double,
        val lng: Double,
        val radioM: Double
    )

    // ── Visita activa en curso ──
    data class VisitaActiva(
        val clienteId: String,
        val clienteNombre: String,
        val clienteCodigo: String,
        val clienteZona: String,
        val clienteDireccion: String,
        val lat: Double,
        val lng: Double,
        val entradaTs: Long,
        val horaEntrada: String
    )

    private var clientes: List<Cliente> = emptyList()
    private var visitaActiva: VisitaActiva? = null
    private var clientesRef: DatabaseReference? = null
    private var clientesListener: ValueEventListener? = null
    private val httpClient = OkHttpClient()

    // ── Iniciar: suscribirse a los clientes en Firebase ──
    fun iniciar(companyId: String) {
        Log.d(TAG, "Iniciando VisitaManager para company=$companyId")
        clientesRef = FirebaseDatabase.getInstance()
            .getReference("companies/$companyId/clientes")

        clientesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<Cliente>()
                for (child in snapshot.children) {
                    val data = child.value as? Map<*, *> ?: continue
                    val lat = when (val v = data["lat"]) {
                        is Double -> v
                        is Long   -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: continue
                        else -> continue
                    }
                    val lng = when (val v = data["lng"]) {
                        is Double -> v
                        is Long   -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: continue
                        else -> continue
                    }
                    if (lat == 0.0 && lng == 0.0) continue
                    val radio = when (val v = data["radio_m"]) {
                        is Double -> v
                        is Long   -> v.toDouble()
                        is String -> v.toDoubleOrNull() ?: RADIO_DEFAULT_M
                        else -> RADIO_DEFAULT_M
                    }
                    lista.add(Cliente(
                        id        = child.key ?: continue,
                        nombre    = data["nombre"]?.toString() ?: "",
                        codigo    = data["codigo"]?.toString() ?: "",
                        zona      = data["zona"]?.toString() ?: "",
                        direccion = data["direccion"]?.toString() ?: "",
                        lat       = lat,
                        lng       = lng,
                        radioM    = if (radio < 10) RADIO_DEFAULT_M else radio
                    ))
                }
                clientes = lista
                Log.d(TAG, "${clientes.size} clientes cargados")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error cargando clientes: ${error.message}")
            }
        }
        clientesRef?.addValueEventListener(clientesListener!!)
    }

    // ── Llamar en cada update de GPS desde TrackingService ──
    fun onLocationUpdate(
        lat: Double,
        lng: Double,
        companyId: String,
        agenteName: String,
        agenteRol: String,
        webhookUrl: String
    ) {
        if (clientes.isEmpty()) return

        // Buscar si el agente está dentro de alguna geocerca
        val clienteActual = clientes.firstOrNull { cliente ->
            distanciaMetros(lat, lng, cliente.lat, cliente.lng) <= cliente.radioM
        }

        when {
            // ── Caso 1: Entró a un cliente nuevo ──
            clienteActual != null && visitaActiva == null -> {
                val ahora   = System.currentTimeMillis()
                val horaFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ahora))
                visitaActiva = VisitaActiva(
                    clienteId       = clienteActual.id,
                    clienteNombre   = clienteActual.nombre,
                    clienteCodigo   = clienteActual.codigo,
                    clienteZona     = clienteActual.zona,
                    clienteDireccion= clienteActual.direccion,
                    lat             = clienteActual.lat,
                    lng             = clienteActual.lng,
                    entradaTs       = ahora,
                    horaEntrada     = horaFmt
                )
                Log.d(TAG, "ENTRADA: ${clienteActual.nombre} a las $horaFmt")
            }

            // ── Caso 2: Cambió de cliente sin salir (caso raro, geocercas solapadas) ──
            clienteActual != null && visitaActiva != null &&
            clienteActual.id != visitaActiva!!.clienteId -> {
                // Cerrar la visita anterior y abrir nueva
                cerrarVisita(companyId, agenteName, agenteRol, webhookUrl)
                val ahora   = System.currentTimeMillis()
                val horaFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ahora))
                visitaActiva = VisitaActiva(
                    clienteId       = clienteActual.id,
                    clienteNombre   = clienteActual.nombre,
                    clienteCodigo   = clienteActual.codigo,
                    clienteZona     = clienteActual.zona,
                    clienteDireccion= clienteActual.direccion,
                    lat             = clienteActual.lat,
                    lng             = clienteActual.lng,
                    entradaTs       = ahora,
                    horaEntrada     = horaFmt
                )
                Log.d(TAG, "CAMBIO DE CLIENTE: ${clienteActual.nombre}")
            }

            // ── Caso 3: Salió del cliente ──
            clienteActual == null && visitaActiva != null -> {
                cerrarVisita(companyId, agenteName, agenteRol, webhookUrl)
            }

            // ── Caso 4: Sigue dentro del mismo cliente — no hacer nada ──
            else -> {}
        }
    }

    // ── Cerrar visita activa y enviarla ──
    private fun cerrarVisita(
        companyId: String,
        agenteName: String,
        agenteRol: String,
        webhookUrl: String
    ) {
        val visita = visitaActiva ?: return
        visitaActiva = null

        val salidaTs    = System.currentTimeMillis()
        val duracionSeg = ((salidaTs - visita.entradaTs) / 1000).toInt()
        val duracionMin = duracionSeg / 60.0

        // Ignorar visitas menores al mínimo (ruido GPS, pasadas rápidas)
        if (duracionSeg < MIN_VISITA_SEG) {
            Log.d(TAG, "Visita ignorada (${duracionSeg}s < ${MIN_VISITA_SEG}s): ${visita.clienteNombre}")
            return
        }

        val horaFmt  = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val fechaFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val horaSalida  = horaFmt.format(Date(salidaTs))
        val fecha       = fechaFmt.format(Date(visita.entradaTs))
        val durMinRedon = Math.round(duracionMin * 10.0) / 10.0
        val idVisita    = "vis_${System.currentTimeMillis()}"

        Log.d(TAG, "SALIDA: ${visita.clienteNombre} — ${durMinRedon} min")

        // Construir objeto de visita
        val visitaData = mapOf(
            "fecha"         to fecha,
            "horaEntrada"   to visita.horaEntrada,
            "horaSalida"    to horaSalida,
            "duracionMin"   to durMinRedon,
            "agente"        to agenteName,
            "rol"           to agenteRol,
            "cliente"       to visita.clienteNombre,
            "codigoCliente" to visita.clienteCodigo,
            "zona"          to visita.clienteZona,
            "direccion"     to visita.clienteDireccion,
            "lat"           to visita.lat,
            "lng"           to visita.lng,
            "id"            to idVisita
        )

        // 1. Guardar en Firebase
        guardarEnFirebase(companyId, idVisita, visitaData)

        // 2. Enviar al webhook de Google Sheets
        if (webhookUrl.isNotEmpty()) {
            enviarASheets(webhookUrl, visitaData)
        }
    }

    // ── Guardar en Firebase companies/{company}/visitas/ ──
    private fun guardarEnFirebase(companyId: String, id: String, data: Map<String, Any>) {
        try {
            // Convertir a formato compatible con Firebase (timestamps como Long)
            val firebaseData = hashMapOf<String, Any>(
                "entradaTs"      to (data["fecha"].toString() + "T" + data["horaEntrada"]).let {
                    try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)?.time ?: 0L }
                    catch (e: Exception) { 0L }
                },
                "salidaTs"       to (data["fecha"].toString() + "T" + data["horaSalida"]).let {
                    try { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)?.time ?: 0L }
                    catch (e: Exception) { 0L }
                },
                "duracionMin"    to (data["duracionMin"] as? Double ?: 0.0),
                "agenteId"       to (data["agente"] as? String ?: ""),
                "agenteName"     to (data["agente"] as? String ?: ""),
                "rol"            to (data["rol"] as? String ?: ""),
                "clienteNombre"  to (data["cliente"] as? String ?: ""),
                "codigoCliente"  to (data["codigoCliente"] as? String ?: ""),
                "clienteZona"    to (data["zona"] as? String ?: ""),
                "clienteDireccion" to (data["direccion"] as? String ?: ""),
                "lat"            to (data["lat"] as? Double ?: 0.0),
                "lng"            to (data["lng"] as? Double ?: 0.0),
                "fechaStr"       to (data["fecha"] as? String ?: "")
            )
            FirebaseDatabase.getInstance()
                .getReference("companies/$companyId/visitas/$id")
                .setValue(firebaseData)
                .addOnSuccessListener { Log.d(TAG, "Visita guardada en Firebase: $id") }
                .addOnFailureListener { e -> Log.e(TAG, "Error guardando en Firebase: ${e.message}") }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparando datos para Firebase: ${e.message}")
        }
    }

    // ── Enviar al webhook de Google Apps Script ──
    private fun enviarASheets(webhookUrl: String, data: Map<String, Any>) {
        try {
            val json = JSONObject(data as Map<*, *>).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Error enviando a Sheets: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, "Visita enviada a Sheets: ${response.code}")
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error en enviarASheets: ${e.message}")
        }
    }

    // ── Forzar cierre de visita activa (cuando se detiene el tracking) ──
    fun detener(companyId: String, agenteName: String, agenteRol: String, webhookUrl: String) {
        if (visitaActiva != null) {
            cerrarVisita(companyId, agenteName, agenteRol, webhookUrl)
        }
        clientesListener?.let { clientesRef?.removeEventListener(it) }
        clientesListener = null
        Log.d(TAG, "VisitaManager detenido")
    }

    // ── Haversine: distancia en metros entre dos coordenadas ──
    private fun distanciaMetros(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371000.0 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
