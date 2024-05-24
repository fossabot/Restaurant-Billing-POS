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

package com.niyaj.market.market_list

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.viewModelScope
import com.niyaj.common.result.Resource
import com.niyaj.common.utils.toFormattedDate
import com.niyaj.common.utils.toSafeString
import com.niyaj.data.repository.MarketListRepository
import com.niyaj.feature.printer.bluetooth_printer.BluetoothPrinter
import com.niyaj.model.MarketItemAndQuantity
import com.niyaj.ui.event.BaseViewModel
import com.niyaj.ui.event.UiState
import com.niyaj.ui.utils.UiEvent
import com.samples.apps.core.analytics.AnalyticsEvent
import com.samples.apps.core.analytics.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarketListViewModel @Inject constructor(
    private val marketListRepository: MarketListRepository,
    private val bluetoothPrinter: BluetoothPrinter,
    private val analyticsHelper: AnalyticsHelper,
) : BaseViewModel() {

    private val _expandedItems = mutableStateListOf<Int>()

    val items = snapshotFlow { mSearchText.value }.flatMapLatest {
        marketListRepository.getAllMarketLists(it)
    }.mapLatest { items ->
        totalItems = items.map { it.marketList.marketId }
        if (items.isEmpty()) UiState.Empty else UiState.Success(items)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState.Loading,
    )

    private val _shareableItems = MutableStateFlow<List<MarketItemAndQuantity>>(emptyList())
    val shareableItems = _shareableItems.asStateFlow()

    fun getListItems(listTypeIds: List<Int>) {
        viewModelScope.launch {
            marketListRepository.getShareableMarketItems(listTypeIds).collectLatest { list ->
                _shareableItems.update { list }
            }
        }
    }

    fun doesSelected(marketId: Int): Boolean {
        return mSelectedItems.contains(marketId)
    }

    fun doesExpanded(marketId: Int): Boolean {
        return _expandedItems.contains(marketId)
    }

    fun onClickExpand(marketId: Int) {
        if (_expandedItems.contains(marketId)) {
            _expandedItems.remove(marketId)
        } else {
            _expandedItems.add(marketId)
        }
    }

    override fun deleteItems() {
        super.deleteItems()

        viewModelScope.launch {
            when (val result = marketListRepository.deleteMarketLists(mSelectedItems.toList())) {
                is Resource.Error -> {
                    mEventFlow.emit(UiEvent.OnError(result.message ?: "unable"))
                }

                is Resource.Success -> {
                    mEventFlow.emit(UiEvent.OnSuccess("${mSelectedItems.size} items has been deleted"))
                }
            }

            mSelectedItems.clear()
        }
    }

    fun printMarketList(listTypeIds: List<Int>, marketDate: Long) {
        viewModelScope.launch {
            try {
                val marketList =
                    marketListRepository.getShareableMarketItems(listTypeIds).stateIn(this)

                bluetoothPrinter.connectBluetoothPrinter()
                val escposPrinter = bluetoothPrinter.printer

                escposPrinter?.let { printer ->
                    var printItems = ""

                    printItems += bluetoothPrinter.getPrintableHeader(
                        title = "MARKET LIST",
                        marketDate.toString(),
                    )
                    printItems += getPrintableItems(marketList.value)

                    printItems += "[L]-------------------------------\n"
                    printItems += "[C]{^..^}--END OF REPORTS--{^..^}\n"
                    printItems += "[L]-------------------------------\n"

                    printer.printFormattedTextAndCut(printItems, 10f)
                    analyticsHelper.logPrintMarketList(marketDate)
                }
            } catch (e: Exception) {
                viewModelScope.launch {
                    mEventFlow.emit(UiEvent.OnError("Unable to print"))
                }
            }
        }
    }

    private fun getPrintableItems(marketList: List<MarketItemAndQuantity>): String {
        var printableString = ""

        val groupByType = marketList.groupBy {
            it.typeName
        }

        groupByType.forEach { (itemType, groupedByType) ->
            val groupByListType = groupedByType.groupBy { it.listType }

            if (groupByListType.isEmpty()) {
                printableString += "[L]You have not added any item in the list\n"
            }

            groupByListType.forEach { (listType, groupedByList) ->

                printableString += "[L]-------------------------------\n"
                printableString += "[L]${itemType} [R]${listType}[${groupedByList.size}]\n"
                printableString += "[L]-------------------------------\n"

                groupedByList.fastForEachIndexed { i, it ->
                    printableString += "[L]${it.itemName} [R]${it.itemQuantity?.toSafeString()} ${it.unitName}\n"

                    if (i != groupedByList.size - 1) {
                        printableString += "[L]\n"
                    }
                }
            }
        }

        return printableString
    }

}

private fun AnalyticsHelper.logPrintMarketList(marketDate: Long) {
    logEvent(
        event = AnalyticsEvent(
            type = "market_list_printed_for",
            extras = listOf(
                AnalyticsEvent.Param("market_list_printed_for", marketDate.toFormattedDate),
            ),
        ),
    )
}