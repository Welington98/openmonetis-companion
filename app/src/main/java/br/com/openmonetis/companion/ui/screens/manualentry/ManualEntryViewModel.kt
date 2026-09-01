package br.com.openmonetis.companion.ui.screens.manualentry

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.openmonetis.companion.data.local.dao.NotificationDao
import br.com.openmonetis.companion.data.local.entities.NotificationEntity
import br.com.openmonetis.companion.service.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ManualEntryUiState(
    val amountText: String = "",
    val description: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val amountError: String? = null,
    val descriptionError: String? = null,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false
)

@HiltViewModel
class ManualEntryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualEntryUiState())
    val uiState: StateFlow<ManualEntryUiState> = _uiState.asStateFlow()

    fun updateAmount(text: String) {
        _uiState.update { it.copy(amountText = text, amountError = null) }
    }

    fun updateDescription(text: String) {
        _uiState.update { it.copy(description = text, descriptionError = null) }
    }

    fun updateDate(millis: Long) {
        _uiState.update { it.copy(dateMillis = millis) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val amount = state.amountText.replace(",", ".").toDoubleOrNull()
        val description = state.description.trim()

        val amountError = if (amount == null || amount <= 0) "amount" else null
        val descriptionError = if (description.isBlank()) "description" else null

        if (amountError != null || descriptionError != null) {
            _uiState.update {
                it.copy(
                    amountError = amountError,
                    descriptionError = descriptionError
                )
            }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            notificationDao.insert(
                NotificationEntity(
                    sourceApp = MANUAL_SOURCE_APP,
                    sourceAppName = "Lançamento manual",
                    originalTitle = "Lançamento manual",
                    originalText = description,
                    notificationTimestamp = state.dateMillis,
                    parsedName = description,
                    parsedAmount = amount,
                    parsedDate = state.dateMillis,
                    parsedCardLastDigits = null
                )
            )
            SyncWorker.enqueue(context)

            _uiState.update { it.copy(isSaving = false, saveCompleted = true) }
        }
    }

    companion object {
        const val MANUAL_SOURCE_APP = "openmonetis:manual-entry"
    }
}
