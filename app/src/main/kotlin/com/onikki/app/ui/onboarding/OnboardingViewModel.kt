package com.onikki.app.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.local.CityLocation
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.OnboardingStore
import com.onikki.app.data.local.UZBEKISTAN_CITIES
import com.onikki.app.domain.permissions.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val hasNotificationPermission: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasAccessibilityEnabled: Boolean = false,
    val selectedCity: CityLocation = UZBEKISTAN_CITIES.first(),
    val isCityPickerOpen: Boolean = false
)

class OnboardingViewModel(
    private val appContext: Context,
    private val locationStore: LocationStore,
    private val onboardingStore: OnboardingStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    init {
        refreshPermissions()
        viewModelScope.launch {
            val city = locationStore.city.first()
            _uiState.update { it.copy(selectedCity = city) }
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
        _uiState.update { it.copy(selectedCity = city, isCityPickerOpen = false) }
    }

    /** Persists the chosen city and marks onboarding done — MainActivity reactively swaps to the main nav host. */
    fun finish() {
        viewModelScope.launch {
            locationStore.setCity(_uiState.value.selectedCity)
            onboardingStore.markComplete()
        }
    }

    companion object {
        fun factory(appContext: Context, locationStore: LocationStore, onboardingStore: OnboardingStore) = viewModelFactory {
            initializer { OnboardingViewModel(appContext, locationStore, onboardingStore) }
        }
    }
}
