package com.niyaj.poposroom.features.product.presentation.add_edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niyaj.poposroom.features.category.domain.model.Category
import com.niyaj.poposroom.features.common.utils.Dispatcher
import com.niyaj.poposroom.features.common.utils.PoposDispatchers
import com.niyaj.poposroom.features.common.utils.Resource
import com.niyaj.poposroom.features.common.utils.UiEvent
import com.niyaj.poposroom.features.common.utils.safeInt
import com.niyaj.poposroom.features.common.utils.safeString
import com.niyaj.poposroom.features.product.domain.model.Product
import com.niyaj.poposroom.features.product.domain.repository.ProductRepository
import com.niyaj.poposroom.features.product.domain.repository.ProductValidationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AddEditProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val validationRepository: ProductValidationRepository,
    @Dispatcher(PoposDispatchers.IO)
    private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val productId = savedStateHandle.get<Int>("productId")

    var state by mutableStateOf(AddEditProductState())

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val _selectedCategory = MutableStateFlow(Category())
    val selectedCategory = _selectedCategory.asStateFlow()

    val categories = productRepository.getAllCategory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryError: StateFlow<String?> = _selectedCategory
        .mapLatest {
            validationRepository.validateCategoryName(it.categoryId).errorMessage
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val nameError: StateFlow<String?> = snapshotFlow { state.productName }
        .mapLatest {
            validationRepository.validateProductName(it, productId).errorMessage
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val priceError: StateFlow<String?> = snapshotFlow { state.productPrice }
        .mapLatest {
            validationRepository.validateProductPrice(safeString(it)).errorMessage
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    init {
        savedStateHandle.get<Int>("productId")?.let {productId ->
            getProductById(productId)
        }
    }

    fun onEvent(event: AddEditProductEvent) {
        when(event) {
            is AddEditProductEvent.CategoryChanged -> {
                viewModelScope.launch {
                    _selectedCategory.value = event.category
                }
            }

            is AddEditProductEvent.ProductNameChanged -> {
                viewModelScope.launch {
                    state = state.copy(
                        productName = event.productName
                    )
                }
            }

            is AddEditProductEvent.ProductPriceChanged -> {
                viewModelScope.launch {
                    state = state.copy(
                        productPrice = event.productPrice
                    )
                }
            }

            is AddEditProductEvent.ProductDescChanged -> {
                viewModelScope.launch {
                    state = state.copy(
                        productDesc = event.productDesc
                    )
                }
            }

            is AddEditProductEvent.ProductAvailabilityChanged -> {
                viewModelScope.launch {
                    state = state.copy(
                        productAvailability = !state.productAvailability
                    )
                }
            }

            is AddEditProductEvent.AddOrUpdateProduct -> {
                createOrUpdateProduct(event.productId)
            }
        }
    }

    private fun createOrUpdateProduct(productId: Int = 0) {
        viewModelScope.launch(ioDispatcher) {
            val hasError = listOf(nameError, priceError, categoryError).all {
                it.value != null
            }

            if (!hasError) {
                val newProduct = Product(
                    productId = productId,
                    categoryId = _selectedCategory.value.categoryId,
                    productName = state.productName,
                    productPrice = state.productPrice.safeInt(),
                    productDescription = state.productDesc,
                    productAvailability = state.productAvailability,
                    createdAt = Date(),
                    updatedAt = if (productId == 0) null else Date()
                )

                when (productRepository.upsertProduct(newProduct)) {
                    is Resource.Error -> {
                        _eventFlow.emit(UiEvent.OnError("Unable to update or create product"))
                    }
                    is Resource.Success -> {
                        _eventFlow.emit(UiEvent.OnSuccess("Product created or updated successfully"))
                    }
                }
            }
        }
    }

    private fun getProductById(productId: Int) {
        viewModelScope.launch {
            when (val result = productRepository.getProductById(productId)) {
                is Resource.Error -> {
                    _eventFlow.emit(UiEvent.OnError("Unable to retrieve product"))
                }
                is Resource.Success -> {
                    result.data?.let { product ->
                        getCategoryById(product.categoryId)

                        state = state.copy(
                            productName = product.productName,
                            productPrice = product.productPrice.toString(),
                            productDesc = product.productDescription,
                            productAvailability = product.productAvailability
                        )
                    }
                }
            }
        }
    }

    private fun getCategoryById(categoryId: Int) {
        viewModelScope.launch {
            productRepository.getCategoryById(categoryId)?.let { category ->
                _selectedCategory.value = category
            }
        }
    }
}