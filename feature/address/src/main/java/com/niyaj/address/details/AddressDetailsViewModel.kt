package com.niyaj.address.details

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niyaj.data.repository.AddressRepository
import com.niyaj.model.TotalOrderDetails
import com.niyaj.ui.event.UiState
import com.samples.apps.core.analytics.AnalyticsEvent
import com.samples.apps.core.analytics.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AddressDetailsViewModel @Inject constructor(
    private val addressRepository: AddressRepository,
    analyticsHelper: AnalyticsHelper,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val addressId = savedStateHandle.get<Int>("addressId") ?: 0

    private val _totalOrders = MutableStateFlow(TotalOrderDetails())
    val totalOrders = _totalOrders.asStateFlow()

    val addressDetails = snapshotFlow { addressId }.mapLatest {
        val data = addressRepository.getAddressById(it).data
        if (data == null) UiState.Empty else UiState.Success(data)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState.Loading
    )

    val orderDetails = snapshotFlow { addressId }.flatMapLatest { addressId ->
        addressRepository.getAddressWiseOrders(addressId).mapLatest { orders ->
            if (orders.isEmpty()) UiState.Empty else {
                val startDate = orders.first().updatedAt
                val endDate = orders.last().updatedAt
                val repeatedOrder = orders.groupingBy { it.customerPhone }
                    .eachCount()
                    .filter { it.value > 1 }.size

                _totalOrders.value = _totalOrders.value.copy(
                    totalAmount = orders.sumOf { it.totalPrice },
                    totalOrder = orders.size,
                    repeatedOrder = repeatedOrder,
                    datePeriod = Pair(startDate, endDate)
                )

                UiState.Success(orders)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState.Loading
    )

    init {
        analyticsHelper.logAddressDetailsViewed(addressId)
    }
}


internal fun AnalyticsHelper.logAddressDetailsViewed(addressId: Int) {
    logEvent(
        event = AnalyticsEvent(
            type = "address_details_viewed",
            extras = listOf(
                AnalyticsEvent.Param("opened_address_details", addressId.toString()),
            ),
        ),
    )
}