package com.onikki.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.data.local.CityLocation
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.UZBEKISTAN_CITIES
import com.onikki.app.domain.permissions.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedCity: CityLocation = UZBEKISTAN_CITIES.first(),
    val isCityPickerOpen: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasAccessibilityEnabled: Boolean = false,
    val hasApiKey: Boolean = false,
    val isApiKeySheetOpen: Boolean = false
)

class SettingsViewModel(
    private val appContext: Context,
    private val locationStore: LocationStore,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        refreshPermissions()
        viewModelScope.launch {
            combine(locationStore.city, apiKeyStore.apiKey) { city, key -> city to !key.isNullOrBlank() }
                .collect { (city, hasKey) -> _uiState.update { it.copy(selectedCity = city, hasApiKey = hasKey) } }
        }
    }

    fun refreshPermissions() {
        _uiState.update {
            it.copy(
                hasNotificationPermission = PermissionChecker.hasNotificationPermission(appContext),
                hasUsageAccess = PermissionChecker.hasUsageAccess(appContext),
                hasAccessibilityEnabled = PermissionChecker.isAccessibilityServiceEnabled(appContext)
            )
        }
    }

    fun openCityPicker() {
        _uiState.update { it.copy(isCityPickerOpen = true) }
    }

    fun dismissCityPicker() {
        _uiState.update { it.copy(isCityPickerOpen = false) }
    }

    fun selectCity(city: CityLocation) {
        _uiState.update { it.copy(isCityPickerOpen = false) }
        viewModelScope.launch { locationStore.setCity(city) }
    }

    fun openApiKeySheet() {
        _uiState.update { it.copy(isApiKeySheetOpen = true) }
    }

    fun dismissApiKeySheet() {
        _uiState.update { it.copy(isApiKeySheetOpen = false) }
    }

    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            apiKeyStore.setApiKey(key.trim())
            _uiState.update { it.copy(isApiKeySheetOpen = false) }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch { apiKeyStore.clearApiKey() }
    }

    companion object {
        fun factory(appContext: Context, locationStore: LocationStore, apiKeyStore: ApiKeyStore) = viewModelFactory {
            initializer { SettingsViewModel(appContext, locationStore, apiKeyStore) }
        }
    }
}
