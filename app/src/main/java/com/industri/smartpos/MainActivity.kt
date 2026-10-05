package com.industri.smartpos

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var etNominal: TextInputEditText
    private lateinit var etQty: TextInputEditText
    private lateinit var btnHitung: Button
    private lateinit var tvHasil: TextView
    private lateinit var btnKePos: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etNominal = findViewById(R.id.etNominal)
        etQty = findViewById(R.id.etQty)
        btnHitung = findViewById(R.id.btnHitung)
        tvHasil = findViewById(R.id.tvHasil)
        btnKePos = findViewById(R.id.btnKePos)

        btnHitung.setOnClickListener {
            val nominalStr = etNominal.text.toString()
            val qtyStr = etQty.text.toString()

            Timber.d("Input Nominal: $nominalStr, Qty: $qtyStr")

            // Latihan Debugger
            // BREAKPOINT: periksa nilai nominalStr dan qtyStr
            val kalkulasiResult = runCatching {
                val nominal = nominalStr.toDouble()
                val qty = qtyStr.toInt()

                if (qty <= 0) {
                    throw IllegalArgumentException("Kuantitas barang minimal 1")
                }

                // BREAKPOINT: periksa hasil kalkulasi
                nominal * qty
            }

            kalkulasiResult
                .onSuccess { total ->
                    Timber.i("Kalkulasi sukses: $total")
                    tvHasil.text = "Total Transaksi: Rp %,.2f".format(total)
                    tvHasil.setTextColor(android.graphics.Color.parseColor("#16A34A"))
                }
                .onFailure { error ->
                    Timber.e(error, "Terjadi kesalahan kalkulasi input kasir")
                    tvHasil.text = "Error Input: ${error.message ?: "Input tidak valid"}"
                    tvHasil.setTextColor(android.graphics.Color.parseColor("#991B1B"))
                }
        }

        btnKePos.setOnClickListener {
            startActivity(Intent(this, PosActivity::class.java))
        }
    }
}
