package com.industri.smartpos

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PosActivity : AppCompatActivity() {

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var etBarcode: TextInputEditText
    private lateinit var etNominalTransaksi: TextInputEditText
    private lateinit var rgSimulation: RadioGroup
    private lateinit var rbSuccess: RadioButton
    private lateinit var rbBarcodeError: RadioButton
    private lateinit var rbNetworkTimeout: RadioButton
    private lateinit var btnProcessPayment: Button
    private lateinit var pbPosLoading: ProgressBar
    private lateinit var cardPosResult: MaterialCardView
    private lateinit var tvPosStatus: TextView
    private lateinit var tvPosDetails: TextView

    private var retryAttempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pos)

        coordinatorLayout = findViewById(R.id.coordinatorLayout)
        etBarcode = findViewById(R.id.etBarcode)
        etNominalTransaksi = findViewById(R.id.etNominalTransaksi)
        rgSimulation = findViewById(R.id.rgSimulation)
        rbSuccess = findViewById(R.id.rbSuccess)
        rbBarcodeError = findViewById(R.id.rbBarcodeError)
        rbNetworkTimeout = findViewById(R.id.rbNetworkTimeout)
        btnProcessPayment = findViewById(R.id.btnProcessPayment)
        pbPosLoading = findViewById(R.id.pbPosLoading)
        cardPosResult = findViewById(R.id.cardPosResult)
        tvPosStatus = findViewById(R.id.tvPosStatus)
        tvPosDetails = findViewById(R.id.tvPosDetails)

        btnProcessPayment.setOnClickListener {
            val barcode = etBarcode.text.toString().trim()
            val nominalStr = etNominalTransaksi.text.toString().trim()

            if (barcode.isEmpty()) {
                tampilkanSnackbarError("Barcode wajib diisi atau dipindai", false)
                return@setOnClickListener
            }

            val nominal = nominalStr.toDoubleOrNull() ?: 0.0

            prosesTransaksi(barcode, nominal)
        }
    }

    private fun prosesTransaksi(barcode: String, nominal: Double) {
        if (nominal > 10000000.0) {
            // Cashier limit exceeded
            try {
                throw CashierLimitExceededException(10000000.0)
            } catch (e: CashierLimitExceededException) {
                Timber.w(e, "Otorisasi Supervisor diperlukan")
                tampilkanDialogOtorisasi(barcode, nominal)
                return
            }
        }

        jalankanSimulasiTransaksi(barcode, nominal)
    }

    private fun tampilkanDialogOtorisasi(barcode: String, nominal: Double) {
        val input = TextInputEditText(this)
        input.hint = "PIN Supervisor (Simulasi: 1234)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Otorisasi Supervisor")
            .setMessage("Transaksi melebihi batas otorisasi kasir.\n\nDiperlukan otorisasi Supervisor.")
            .setView(input)
            .setPositiveButton("OTORISASI") { _, _ ->
                val pin = input.text.toString()
                if (pin == "1234") {
                    Timber.i("Otorisasi Supervisor berhasil")
                    tampilkanSnackbarError("Otorisasi Supervisor berhasil. Transaksi dapat dilanjutkan.", false)
                    jalankanSimulasiTransaksi(barcode, nominal)
                } else {
                    Timber.w("PIN Supervisor salah")
                    tampilkanSnackbarError("PIN Supervisor salah.", false)
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun jalankanSimulasiTransaksi(barcode: String, nominal: Double) {
        val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
            Timber.e(exception, "FATAL COROUTINE ERROR TERTANGKAP")
            runOnUiThread {
                resetUi()
                tampilkanSnackbarError("Terjadi kesalahan sistem fatal.", false)
            }
        }

        lifecycleScope.launch(Dispatchers.Main + coroutineExceptionHandler) {
            tampilkanLoading(true)

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    delay(1200)

                    when {
                        rbBarcodeError.isChecked -> {
                            throw ProductBarcodeNotFoundException(barcode)
                        }
                        rbNetworkTimeout.isChecked -> {
                            throw PaymentGatewayTimeoutException("QRIS Bank Settlement")
                        }
                        else -> {
                            PosTransaction(
                                transactionId = "TRX-2026-9901",
                                barcode = barcode,
                                productName = "Susu UHT Full Cream 1 Liter",
                                totalAmount = nominal,
                                status = "SETTLED_SUCCESS"
                            )
                        }
                    }
                }
            }

            tampilkanLoading(false)

            result.onSuccess { transaction ->
                Timber.i("Transaksi sukses: ${transaction.transactionId}")
                retryAttempt = 0 // Reset retry on success
                tampilkanHasilSukses(transaction)
            }.onFailure { error ->
                tanganiErrorTransaksi(error, barcode, nominal)
            }
        }
    }

    private fun tanganiErrorTransaksi(error: Throwable, barcode: String, nominal: Double) {
        when (error) {
            is ProductBarcodeNotFoundException -> {
                Timber.w(error, "Transaksi gagal: Barcode tidak ditemukan")
                tampilkanSnackbarError("Barcode tidak ditemukan", false)
            }
            is PaymentGatewayTimeoutException -> {
                Timber.e(error, "Transaksi gagal: Payment Timeout")
                retryAttempt++
                if (retryAttempt >= 3) {
                    tampilkanDialogManualCash()
                } else {
                    tampilkanSnackbarError(error.message ?: "Timeout", true) {
                        jalankanSimulasiTransaksi(barcode, nominal)
                    }
                }
            }
            is IllegalArgumentException -> {
                Timber.e(error, "Transaksi gagal: Argumen tidak valid")
                tampilkanSnackbarError(error.message ?: "Input tidak valid", false)
            }
            else -> {
                Timber.e(error, "Terjadi kesalahan transaksi tidak terduga")
                tampilkanSnackbarError("Error: ${error.message}", false)
            }
        }
    }

    private fun tampilkanDialogManualCash() {
        AlertDialog.Builder(this)
            .setTitle("Pembayaran Gagal")
            .setMessage("Pembayaran elektronik gagal setelah 3 kali percobaan.\n\nSilakan alihkan pelanggan ke:\nPembayaran Tunai Manual")
            .setPositiveButton("OK") { _, _ ->
                retryAttempt = 0
            }
            .setCancelable(false)
            .show()
    }

    private fun tampilkanLoading(isLoading: Boolean) {
        if (isLoading) {
            pbPosLoading.visibility = View.VISIBLE
            btnProcessPayment.isEnabled = false
            cardPosResult.visibility = View.GONE
        } else {
            pbPosLoading.visibility = View.GONE
            btnProcessPayment.isEnabled = true
        }
    }

    private fun resetUi() {
        tampilkanLoading(false)
        cardPosResult.visibility = View.GONE
    }

    private fun tampilkanHasilSukses(transaction: PosTransaction) {
        cardPosResult.visibility = View.VISIBLE
        tvPosStatus.text = "TRANSAKSI BERHASIL (LUNAS)"
        tvPosStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))

        val details = """
            ID Transaksi : ${transaction.transactionId}
            Barcode      : ${transaction.barcode}
            Produk       : ${transaction.productName}
            Total Bayar  : Rp %,.0f
            Status       : ${transaction.status}
            Waktu        : Baru Saja
        """.trimIndent().format(transaction.totalAmount)

        tvPosDetails.text = details
    }

    private fun tampilkanSnackbarError(pesan: String, canRetry: Boolean, onRetry: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(coordinatorLayout, pesan, Snackbar.LENGTH_LONG)
        snackbar.view.setBackgroundColor(android.graphics.Color.parseColor("#991B1B"))
        snackbar.setTextColor(android.graphics.Color.WHITE)

        if (canRetry && onRetry != null) {
            snackbar.setAction("COBA LAGI") {
                onRetry()
            }
            snackbar.setActionTextColor(android.graphics.Color.parseColor("#FBBF24"))
        }

        snackbar.show()
    }
}
