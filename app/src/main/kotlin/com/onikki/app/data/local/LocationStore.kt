package com.onikki.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.locationDataStore by preferencesDataStore(name = "onikki_location")
private val KEY_CITY_NAME = stringPreferencesKey("city_name")
private val KEY_LATITUDE = doublePreferencesKey("latitude")
private val KEY_LONGITUDE = doublePreferencesKey("longitude")

data class CityLocation(val name: String, val latitude: Double, val longitude: Double) {
    /** Every listed city is in Uzbekistan — UTC+5 year-round, no DST. */
    val utcOffsetHours: Double get() = 5.0
}

/** Major Uzbekistan cities (TZ 3.4: "foydalanuvchi bir marta shahar/joylashuvni tanlaydi"). */
val UZBEKISTAN_CITIES = listOf(
    CityLocation("Toshkent", 41.2995, 69.2401),
    CityLocation("Samarqand", 39.6270, 66.9750),
    CityLocation("Buxoro", 39.7747, 64.4286),
    CityLocation("Andijon", 40.7821, 72.3442),
    CityLocation("Namangan", 40.9983, 71.6726),
    CityLocation("Farg'ona", 40.3894, 71.7864),
    CityLocation("Nukus", 42.4531, 59.6103),
    CityLocation("Qarshi", 38.8606, 65.7891),
    CityLocation("Termiz", 37.2242, 67.2783),
    CityLocation("Urganch", 41.5506, 60.6317),
    CityLocation("Jizzax", 40.1158, 67.8422),
    CityLocation("Guliston", 40.4897, 68.7842),
    CityLocation("Navoiy", 40.0844, 65.3792)
)

private val DEFAULT_CITY = UZBEKISTAN_CITIES.first()

/**
 * The user's chosen city for offline prayer-time calculation (TZ 3.4). City
 * coordinates here are approximate (general knowledge, not surveyed) — fine
 * for prayer-time purposes, where a few km of error changes times by well
 * under a minute.
 */
class LocationStore(private val context: Context) {
    val city: Flow<CityLocation> = context.locationDataStore.data.map { prefs ->
        val name = prefs[KEY_CITY_NAME]
        val lat = prefs[KEY_LATITUDE]
        val lon = prefs[KEY_LONGITUDE]
        if (name != null && lat != null && lon != null) CityLocation(name, lat, lon) else DEFAULT_CITY
    }

    suspend fun setCity(cityLocation: CityLocation) {
        context.locationDataStore.edit { prefs ->
            prefs[KEY_CITY_NAME] = cityLocation.name
            prefs[KEY_LATITUDE] = cityLocation.latitude
            prefs[KEY_LONGITUDE] = cityLocation.longitude
        }
    }
}
