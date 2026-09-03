package br.com.openmonetis.companion.ui.screens.invoice

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.openmonetis.companion.data.local.dao.NotificationDao
import br.com.openmonetis.companion.data.local.entities.NotificationEntity
import br.com.openmonetis.companion.domain.parser.NfceInvoiceFetcher
import br.com.openmonetis.companion.domain.parser.NfceQrParser
import br.com.openmonetis.companion.domain.parser.NfceQrResult
import br.com.openmonetis.companion.service.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Immutable
data class PendingInvoice(
    val accessKey: String,
    val rawContent: String,
    val isFetchingDetails: Boolean,
    val merchantName: String,
    val amountText: String
)

@Immutable
data class InvoiceScannerUiState(
    val feedbackMessage: String? = null,
    val scanResult: NfceQrResult? = null,
    val pendingInvoice: PendingInvoice? = null
)

@HiltViewModel
class InvoiceScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val nfceQrParser: NfceQrParser,
    private val nfceInvoiceFetcher: NfceInvoiceFetcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceScannerUiState())
    val uiState: StateFlow<InvoiceScannerUiState> = _uiState.asStateFlow()

    private var isHandling = false

    fun onQrCodeDetected(rawValue: String) {
        if (isHandling || _uiState.value.pendingInvoice != null) return
        isHandling = true

        viewModelScope.launch {
            val parsed = nfceQrParser.parse(rawValue)
            if (parsed == null) {
                showTransientFeedback("QR Code inválido. Aponte para o QR Code de uma NFC-e.")
                return@launch
            }

            if (notificationDao.existsByOriginalText(parsed.rawContent)) {
                showTransientFeedback("Esta nota já foi capturada anteriormente.")
                return@launch
            }

            _uiState.update {
                it.copy(
                    pendingInvoice = PendingInvoice(
                        accessKey = parsed.accessKey,
                        rawContent = parsed.rawContent,
                        isFetchingDetails = true,
                        merchantName = "",
                        amountText = ""
                    )
                )
            }

            // Best-effort: each state's Sefaz portal has its own page layout,
            // so this may come back empty and the user fills it in by hand.
            val details = nfceInvoiceFetcher.fetch(parsed.rawContent)

            _uiState.update { state ->
                state.copy(
                    pendingInvoice = state.pendingInvoice?.copy(
                        isFetchingDetails = false,
                        merchantName = details?.merchantName.orEmpty(),
                        amountText = details?.amount?.let(::formatAmount).orEmpty()
                    )
                )
            }
        }
    }

    fun updatePendingMerchantName(value: String) {
        _uiState.update { it.copy(pendingInvoice = it.pendingInvoice?.copy(merchantName = value)) }
    }

    fun updatePendingAmount(value: String) {
        _uiState.update { it.copy(pendingInvoice = it.pendingInvoice?.copy(amountText = value)) }
    }

    fun cancelPendingInvoice() {
        _uiState.update { it.copy(pendingInvoice = null) }
        isHandling = false
    }

    fun confirmPendingInvoice() {
        val pending = _uiState.value.pendingInvoice ?: return
        if (pending.isFetchingDetails) return

        viewModelScope.launch {
            val amount = pending.amountText.trim().replace(",", ".").toDoubleOrNull()
            val merchantName = pending.merchantName.trim().ifBlank {
                "NFC-e ${nfceQrParser.formatAccessKey(pending.accessKey)}"
            }

            notificationDao.insert(
                NotificationEntity(
                    sourceApp = NfceQrParser.NFCE_SOURCE_APP,
                    sourceAppName = "Leitor de Nota Fiscal",
                    originalTitle = "NFC-e escaneada",
                    originalText = pending.rawContent,
                    notificationTimestamp = System.currentTimeMillis(),
                    parsedName = merchantName,
                    parsedAmount = amount,
                    parsedDate = null,
                    parsedCardLastDigits = null
                )
            )
            SyncWorker.enqueue(context)

            _uiState.update {
                it.copy(
                    pendingInvoice = null,
                    scanResult = NfceQrResult(pending.accessKey, pending.rawContent)
                )
            }
        }
    }

    private suspend fun showTransientFeedback(message: String) {
        _uiState.update { it.copy(feedbackMessage = message) }
        delay(1500)
        _uiState.update { it.copy(feedbackMessage = null) }
        isHandling = false
    }

    private fun formatAmount(amount: Double): String =
        String.format(Locale("pt", "BR"), "%.2f", amount).replace(".", ",")
}
