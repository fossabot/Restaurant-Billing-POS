/*
 *      Copyright 2024 Sk Niyaj Ali
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *              http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */

package com.niyaj.market.market_item.add_edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niyaj.common.result.Resource
import com.niyaj.common.utils.capitalizeWords
import com.niyaj.common.utils.safeString
import com.niyaj.data.repository.MarketItemRepository
import com.niyaj.data.repository.validation.MarketItemValidationRepository
import com.niyaj.model.MarketItem
import com.niyaj.model.MeasureUnit
import com.niyaj.ui.utils.UiEvent
import com.samples.apps.core.analytics.AnalyticsEvent
import com.samples.apps.core.analytics.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditMarketItemViewModel @Inject constructor(
    private val repository: MarketItemRepository,
    private val validationRepository: MarketItemValidationRepository,
    private val analyticsHelper: AnalyticsHelper,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId = savedStateHandle.get<Int>("itemId") ?: 0

    var state by mutableStateOf(AddEditMarketItemState())

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        savedStateHandle.get<Int>("itemId")?.let {
            getMarketListById(it)
        }
    }

    val itemTypes = snapshotFlow { state.marketType }.flatMapLatest {
        repository.getAllItemType(it.typeName)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val measureUnits = snapshotFlow { state.itemMeasureUnit.unitName }.flatMapLatest { searchText ->
        repository.getAllMeasureUnits(searchText)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val typeError = snapshotFlow { state.marketType }.mapLatest {
        validationRepository.validateItemType(it.typeId).errorMessage
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val nameError = snapshotFlow { state.itemName }.mapLatest {
        validationRepository.validateItemName(it, itemId).errorMessage
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val priceError = snapshotFlow { state.itemPrice }.mapLatest {
        validationRepository.validateItemPrice(it).errorMessage
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    val unitError = snapshotFlow { state.itemMeasureUnit }.mapLatest {
        validationRepository.validateItemMeasureUnit(it.unitId).errorMessage
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    fun onEvent(event: AddEditMarketItemEvent) {
        when (event) {
            is AddEditMarketItemEvent.ItemTypeChanged -> {
                state = state.copy(marketType = event.marketType)
            }

            is AddEditMarketItemEvent.ItemNameChanged -> {
                state = state.copy(itemName = event.name)
            }

            is AddEditMarketItemEvent.ItemMeasureUnitChanged -> {
                state = state.copy(itemMeasureUnit = event.unit)
            }

            is AddEditMarketItemEvent.ItemMeasureUnitNameChanged -> {
                state = state.copy(
                    itemMeasureUnit = MeasureUnit(unitName = event.unitName.lowercase()),
                )
            }

            is AddEditMarketItemEvent.ItemDescriptionChanged -> {
                state = state.copy(itemDesc = event.description)
            }

            is AddEditMarketItemEvent.ItemPriceChanged -> {
                state = state.copy(itemPrice = event.price.safeString())
            }

            is AddEditMarketItemEvent.AddOrUpdateItem -> {
                createOrUpdateItem(itemId)
            }

        }
    }

    private fun getMarketListById(itemId: Int) {
        viewModelScope.launch {
            repository.getMarketItemById(itemId).data?.let {
                state = state.copy(
                    marketType = it.itemType,
                    itemName = it.itemName,
                    itemPrice = it.itemPrice,
                    itemDesc = it.itemDescription,
                    itemMeasureUnit = it.itemMeasureUnit,
                )
            }
        }
    }

    private fun createOrUpdateItem(itemId: Int) {
        viewModelScope.launch {
            val hasError =
                listOf(nameError, typeError, priceError, unitError).all { it.value != null }

            if (!hasError) {
                val newItem = MarketItem(
                    itemId = itemId,
                    itemName = state.itemName.trim().capitalizeWords,
                    itemType = state.marketType,
                    itemMeasureUnit = state.itemMeasureUnit,
                    itemPrice = state.itemPrice?.trim(),
                    itemDescription = state.itemDesc?.trim()?.capitalizeWords,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = if (itemId != 0) System.currentTimeMillis() else null,
                )

                when (val result = repository.upsertMarketItem(newItem)) {
                    is Resource.Error -> {
                        _eventFlow.emit(UiEvent.OnError(result.message ?: "Invalid"))
                    }

                    is Resource.Success -> {
                        val message = if (itemId == 0) "created" else "updated"
                        _eventFlow.emit(
                            UiEvent.OnSuccess("Item has been successfully $message"),
                        )
                        analyticsHelper.logOnCreateOrUpdateMarketItem(itemId, message)
                    }
                }

                state = AddEditMarketItemState()
            }
        }
    }
}


private fun AnalyticsHelper.logOnCreateOrUpdateMarketItem(data: Int, message: String) {
    logEvent(
        event = AnalyticsEvent(
            type = "market_item_$message",
            extras = listOf(
                AnalyticsEvent.Param("market_item_$message", data.toString()),
            ),
        ),
    )
}