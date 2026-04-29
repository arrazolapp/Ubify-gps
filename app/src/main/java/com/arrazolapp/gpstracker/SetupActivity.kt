package com.arrazolapp.gpstracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etCompany   = findViewById<EditText>(R.id.etCompany)
        val etUserId    = findViewById<EditText>(R.id.etUserId)
        val etNombre    = findViewById<EditText>(R.id.etNombre)
        val spRol       = findViewById<Spinner>(R.id.spRol)
        val etPlaca     = findViewById<EditText>(R.id.etPlaca)
        val etWhatsapp  = findViewById<EditText>(R.id.etWhatsapp)
        val etWebhook   = findViewById<EditText>(R.id.etWebhook)
        val btnSave     = findViewById<Button>(R.id.btnSave)

        // Spinner de roles
        val roles = arrayOf("vendedor", "transportista")
        spRol.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)

        // Pre-cargar valores guardados
        val prefs = getSharedPreferences("agent_config", MODE_PRIVATE)
        etWebhook.setText(prefs.getString("webhookUrl", ""))

        // Verificar si viene de un deep link (arrazolapp://tracker?...)
        intent?.data?.let { uri ->
            etCompany.setText(uri.getQueryParameter("company") ?: "demo_corp")
            etUserId.setText(uri.getQueryParameter("id") ?: "")
            etNombre.setText(uri.getQueryParameter("name") ?: "")
            etPlaca.setText(uri.getQueryParameter("placa") ?: "")
            etWhatsapp.setText(uri.getQueryParameter("wa") ?: "")
            val rol = uri.getQueryParameter("rol") ?: "vendedor"
            spRol.setSelection(if (rol == "transportista") 1 else 0)
            // El webhook también puede venir en el deep link
            uri.getQueryParameter("webhook")?.let { wh ->
                if (wh.isNotEmpty()) etWebhook.setText(wh)
            }
        }

        btnSave.setOnClickListener {
            val company = etCompany.text.toString().trim().ifEmpty { "demo_corp" }
            val userId = etUserId.text.toString().trim()
            val nombre = etNombre.text.toString().trim()
            val rol = spRol.selectedItem.toString()
            val placa = etPlaca.text.toString().trim()
            val whatsapp = etWhatsapp.text.toString().trim()

            if (userId.isEmpty() || nombre.isEmpty()) {
                Toast.makeText(this, "ID de usuario y nombre son requeridos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Generar iniciales
            val partes = nombre.split(" ")
            val iniciales = if (partes.size >= 2) {
                "${partes.first().first()}${partes.last().first()}".uppercase()
            } else {
                nombre.take(2).uppercase()
            }

            // Guardar configuración
            getSharedPreferences("agent_config", MODE_PRIVATE).edit().apply {
                putString("company",    company)
                putString("userId",     userId)
                putString("nombre",     nombre)
                putString("iniciales",  iniciales)
                putString("rol",        rol)
                putString("placa",      placa)
                putString("whatsapp",   whatsapp)
                putString("webhookUrl", etWebhook.text.toString().trim())
                putBoolean("autoStart", true)
                apply()
            }

            Toast.makeText(this, "✅ Configuración guardada", Toast.LENGTH_SHORT).show()

            // Ir a MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
