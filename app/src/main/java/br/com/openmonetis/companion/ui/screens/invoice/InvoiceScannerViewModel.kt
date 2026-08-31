package br.com.openmonetis.companion.ui.screens.invoice

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.openmonetis.companion.data.local.dao.NotificationDao
import br.com.openmonetis.companion.data.local.entities.NotificationEntity
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
import javax.inject.Inject

@Immutable
data class InvoiceScannerUiState(
    val feedbackMessage: String? = null,
    val scanResult: NfceQrResult? = null
)

@HiltViewModel
class InvoiceScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    private val nfceQrParser: NfceQrParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoiceScannerUiState())
    val uiState: StateFlow<InvoiceScannerUiState> = _uiState.asStateFlow()

    private var isHandling = false

    fun onQrCodeDetected(rawValue: String) {
        if (isHandling) return
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

            notificationDao.insert(
                NotificationEntity(
                    sourceApp = NfceQrParser.NFCE_SOURCE_APP,
                    sourceAppName = "Leitor de Nota Fiscal",
                    originalTitle = "NFC-e escaneada",
                    originalText = parsed.rawContent,
                    notificationTimestamp = System.currentTimeMillis(),
                    parsedName = "NFC-e ${nfceQrParser.formatAccessKey(parsed.accessKey)}",
                    parsedAmount = null,
                    parsedDate = null,
                    parsedCardLastDigits = null
                )
            )
            SyncWorker.enqueue(context)

            _uiState.update { it.copy(scanResult = parsed) }
        }
    }

    private suspend fun showTransientFeedback(message: String) {
        _uiState.update { it.copy(feedbackMessage = message) }
        delay(1500)
        _uiState.update { it.copy(feedbackMessage = null) }
        isHandling = false
    }
}
