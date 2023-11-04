package com.niyaj.cartorder

import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.niyaj.common.result.Resource
import com.niyaj.data.repository.CartOrderRepository
import com.niyaj.model.Selected
import com.niyaj.ui.event.BaseViewModel
import com.niyaj.ui.event.UiState
import com.niyaj.ui.utils.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartOrderViewModel @Inject constructor(
    private val cartOrderRepository: CartOrderRepository,
) : BaseViewModel() {

    override var totalItems: List<Int> = emptyList()

    private val _viewAll = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedId = cartOrderRepository.getSelectedCartOrder()
        .mapLatest {
            it?.orderId ?: 0
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val cartOrders = snapshotFlow { mSearchText.value }
        .combine(_viewAll) { text, viewAll ->
            cartOrderRepository.getAllCartOrders(text, viewAll)
        }.flatMapLatest { listFlow ->
            listFlow.mapLatest { list ->
                val data = list.sortedByDescending { it.orderId == selectedId.value }

                totalItems = data.map { it.orderId }
                if (data.isEmpty()) {
                    UiState.Empty
                } else UiState.Success(data)

            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading
        )


    fun selectCartOrder() {
        viewModelScope.launch {
            val result = cartOrderRepository.insertOrUpdateSelectedOrder(
                Selected(orderId = selectedItems.first())
            )

            when (result) {
                is Resource.Error -> {
                    mEventFlow.emit(UiEvent.OnError(result.message ?: "Unable"))
                }

                is Resource.Success -> {
                    deselectItems()
                }
            }
        }
    }

    fun onClickViewAllOrder() {
        viewModelScope.launch {
            _viewAll.value = !_viewAll.value
        }
    }

    override fun deleteItems() {
        super.deleteItems()

        viewModelScope.launch {
            when (val result = cartOrderRepository.deleteCartOrders(selectedItems.toList())) {
                is Resource.Error -> {
                    mEventFlow.emit(UiEvent.OnError(result.message ?: "Unable"))
                }

                is Resource.Success -> {
                    mEventFlow.emit(
                        UiEvent.OnSuccess(
                            "${selectedItems.size} cart orders has been deleted"
                        )
                    )
                }
            }

            mSelectedItems.clear()
        }
    }
}