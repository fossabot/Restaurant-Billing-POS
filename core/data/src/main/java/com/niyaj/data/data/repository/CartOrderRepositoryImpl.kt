package com.niyaj.data.data.repository

import com.niyaj.common.network.Dispatcher
import com.niyaj.common.network.PoposDispatchers
import com.niyaj.common.result.Resource
import com.niyaj.common.result.ValidationResult
import com.niyaj.common.tags.CartOrderTestTags.ADDRESS_NAME_LENGTH_ERROR
import com.niyaj.common.tags.CartOrderTestTags.CART_ORDER_NAME_EMPTY_ERROR
import com.niyaj.common.tags.CartOrderTestTags.CART_ORDER_NAME_ERROR
import com.niyaj.common.tags.CartOrderTestTags.CART_ORDER_PHONE_EMPTY_ERROR
import com.niyaj.common.tags.CartOrderTestTags.CART_ORDER_PHONE_ERROR
import com.niyaj.common.tags.CartOrderTestTags.CUSTOMER_PHONE_LENGTH_ERROR
import com.niyaj.common.tags.CartOrderTestTags.CUSTOMER_PHONE_LETTER_ERROR
import com.niyaj.common.tags.CartOrderTestTags.ORDER_PRICE_LESS_THAN_TWO_ERROR
import com.niyaj.common.tags.CartOrderTestTags.ORDER_SHORT_NAME_EMPTY_ERROR
import com.niyaj.data.mapper.toEntity
import com.niyaj.data.repository.CartOrderRepository
import com.niyaj.data.repository.validation.CartOrderValidationRepository
import com.niyaj.database.dao.AddressDao
import com.niyaj.database.dao.CartDao
import com.niyaj.database.dao.CartOrderDao
import com.niyaj.database.dao.CartPriceDao
import com.niyaj.database.dao.CustomerDao
import com.niyaj.database.dao.SelectedDao
import com.niyaj.database.model.CartAddOnItemsEntity
import com.niyaj.database.model.CartChargesEntity
import com.niyaj.database.model.CartOrderEntity
import com.niyaj.database.model.CartPriceEntity
import com.niyaj.database.model.SelectedEntity
import com.niyaj.database.model.asExternalModel
import com.niyaj.model.AddOnItem
import com.niyaj.model.Address
import com.niyaj.model.CartOrder
import com.niyaj.model.CartOrderWithAddOnAndCharges
import com.niyaj.model.Charges
import com.niyaj.model.Customer
import com.niyaj.model.OrderStatus
import com.niyaj.model.OrderType
import com.niyaj.model.SELECTED_ID
import com.niyaj.model.Selected
import com.niyaj.model.filterCartOrder
import com.niyaj.model.searchAddress
import com.niyaj.model.searchCustomer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import timber.log.Timber


class CartOrderRepositoryImpl(
    private val cartDao: CartDao,
    private val cartOrderDao: CartOrderDao,
    private val customerDao: CustomerDao,
    private val addressDao: AddressDao,
    private val selectedDao: SelectedDao,
    private val cartPriceDao: CartPriceDao,
    @Dispatcher(PoposDispatchers.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : CartOrderRepository, CartOrderValidationRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getAllProcessingCartOrders(): Flow<List<CartOrder>> {
        return withContext(ioDispatcher) {
            selectedDao.getAllProcessingCartOrders().mapLatest { list ->
                list.map { cartOrder ->
                    val address = if (cartOrder.orderType != OrderType.DineIn) {
                        withContext(ioDispatcher) {
                            addressDao.getAddressById(cartOrder.addressId)?.asExternalModel()
                        }
                    } else null

                    val customer = if (cartOrder.orderType != OrderType.DineIn) {
                        withContext(ioDispatcher) {
                            customerDao.getCustomerById(cartOrder.customerId)?.asExternalModel()
                        }
                    } else null

                    CartOrder(
                        orderId = cartOrder.orderId,
                        orderType = cartOrder.orderType,
                        orderStatus = cartOrder.orderStatus,
                        doesChargesIncluded = cartOrder.doesChargesIncluded,
                        customer = customer ?: Customer(),
                        address = address ?: Address(),
                        createdAt = cartOrder.createdAt,
                        updatedAt = cartOrder.updatedAt
                    )
                }
            }
        }
    }

    override fun getSelectedCartOrder(): Flow<Selected?> {
        return selectedDao.getSelectedCartOrder().map {
            it?.asExternalModel()
        }
    }

    override suspend fun getAllAddOnItem(): Flow<List<AddOnItem>> {
        return withContext(ioDispatcher) {
            cartOrderDao.getAllAddOnItems().mapLatest { list -> list.map { it.asExternalModel() } }
        }
    }

    override suspend fun getAllCharges(): Flow<List<Charges>> {
        return withContext(ioDispatcher) {
            cartOrderDao.getAllCharges().mapLatest { list -> list.map { it.asExternalModel() } }
        }
    }

    override suspend fun insertOrUpdateSelectedOrder(selected: Selected): Resource<Boolean> {
        return withContext(ioDispatcher) {
            try {
                val result = selectedDao.insertOrUpdateSelectedOrder(selected.toEntity())

                Resource.Success(result > 0)
            } catch (e: Exception) {
                Resource.Error(e.message)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getAllAddresses(searchText: String): Flow<List<Address>> {
        return withContext(ioDispatcher) {
            addressDao.getAllAddresses().mapLatest { it ->
                it.map {
                    it.asExternalModel()
                }.searchAddress(searchText)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getAllCustomer(searchText: String): Flow<List<Customer>> {
        return withContext(ioDispatcher) {
            customerDao.getAllCustomer().mapLatest { it ->
                it.map {
                    it.asExternalModel()
                }.searchCustomer(searchText)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getAllCartOrders(
        searchText: String,
        viewAll: Boolean,
    ): Flow<List<CartOrder>> {
        withContext(ioDispatcher) {
            async {
                updateOrIgnoreSelectedOrder()
            }.await()
        }

        return withContext(ioDispatcher) {
            val result = if (viewAll) {
                cartOrderDao.getAllCartOrders()
            } else cartOrderDao.getProcessingCartOrders()

            result.mapLatest { list ->
                list.map { cartOrder ->
                    val address = if (cartOrder.orderType != OrderType.DineIn) {
                        withContext(ioDispatcher) {
                            addressDao.getAddressById(cartOrder.addressId)?.asExternalModel()
                        }
                    } else null

                    val customer = if (cartOrder.orderType != OrderType.DineIn) {
                        withContext(ioDispatcher) {
                            customerDao.getCustomerById(cartOrder.customerId)?.asExternalModel()
                        }
                    } else null

                    CartOrder(
                        orderId = cartOrder.orderId,
                        orderType = cartOrder.orderType,
                        orderStatus = cartOrder.orderStatus,
                        doesChargesIncluded = cartOrder.doesChargesIncluded,
                        customer = customer ?: Customer(),
                        address = address ?: Address(),
                        createdAt = cartOrder.createdAt,
                        updatedAt = cartOrder.updatedAt
                    )
                }
            }.mapLatest {
                it.filterCartOrder(searchText)
            }
        }
    }

    override suspend fun getCartOrderById(orderId: Int): Resource<CartOrderWithAddOnAndCharges?> {
        return withContext(ioDispatcher) {
            try {
                val result = cartOrderDao.getCartOrderById(orderId)

                val data = result?.cartOrder?.let { cartOrder ->
                    val address = if (cartOrder.orderType != OrderType.DineIn) {
                        withContext(ioDispatcher) {
                            addressDao.getAddressById(cartOrder.addressId)?.asExternalModel()
                        }
                    } else null

                    val customer = if (cartOrder.orderType != OrderType.DineIn) {
                        withContext(ioDispatcher) {
                            customerDao.getCustomerById(cartOrder.customerId)?.asExternalModel()
                        }
                    } else null

                    val cartOrderItem = CartOrder(
                        orderId = cartOrder.orderId,
                        orderType = cartOrder.orderType,
                        orderStatus = cartOrder.orderStatus,
                        doesChargesIncluded = cartOrder.doesChargesIncluded,
                        customer = customer ?: Customer(),
                        address = address ?: Address(),
                        createdAt = cartOrder.createdAt,
                        updatedAt = cartOrder.updatedAt
                    )

                    CartOrderWithAddOnAndCharges(
                        cartOrder = cartOrderItem,
                        addOnItems = result.addOnItems,
                        charges = result.charges
                    )
                }

                Resource.Success(data)
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unable to get CartOrder")
            }
        }
    }

    override suspend fun getLastCreatedOrderId(orderId: Int): Int {
        return withContext(ioDispatcher) {
            if (orderId == 0) {
                val result = cartOrderDao.getLastCreatedOrderId()

                if (result == null) 1 else result + 1
            } else orderId
        }
    }

    override suspend fun addOrIgnoreAddress(newAddress: Address): Int {
        return try {
            withContext(ioDispatcher) {
                val validateAddressName = validateAddressName(newAddress.addressName)
                val validateAddressShortName = validateAddressShortName(newAddress.shortName)

                val hasError =
                    listOf(validateAddressName, validateAddressShortName).any { !it.successful }

                if (!hasError) {
                    withContext(ioDispatcher) {
                        addressDao.getAddressByName(newAddress.addressName)
                            ?: addressDao.insertOrIgnoreAddress(newAddress.toEntity()).toInt()
                    }
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun addOrIgnoreCustomer(newCustomer: Customer): Int {
        return try {
            withContext(ioDispatcher) {
                val validateCustomerPhone = validateCustomerPhone(newCustomer.customerPhone)

                if (validateCustomerPhone.successful) {
                    customerDao.getCustomerByPhone(newCustomer.customerPhone)
                        ?: customerDao.insertOrIgnoreCustomer(newCustomer.toEntity()).toInt()
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun createOrUpdateCartOrder(newCartOrder: CartOrderWithAddOnAndCharges): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val cartOrder = newCartOrder.cartOrder
                val isDineOut = cartOrder.orderType == OrderType.DineOut

                val addressId = async {
                    if (isDineOut) {
                        if (cartOrder.address.addressId == 0) {
                            addOrIgnoreAddress(cartOrder.address)
                        } else cartOrder.address.addressId
                    } else 0
                }.await()

                val customerId = async {
                    if (isDineOut) {
                        if (cartOrder.customer.customerId == 0) {
                            addOrIgnoreCustomer(cartOrder.customer)
                        } else cartOrder.customer.customerId
                    } else 0
                }.await()

                val validatedCustomer = validateCustomerPhone(cartOrder.orderType, customerId)
                val validatedAddress = validateCustomerAddress(cartOrder.orderType, addressId)

                val hasError = listOf(validatedCustomer, validatedAddress).any {
                    !it.successful
                }

                if (!hasError) {
                    val newOrder = CartOrderEntity(
                        orderId = cartOrder.orderId,
                        orderType = cartOrder.orderType,
                        orderStatus = cartOrder.orderStatus,
                        doesChargesIncluded = cartOrder.doesChargesIncluded,
                        addressId = addressId,
                        customerId = customerId,
                        createdAt = cartOrder.createdAt,
                        updatedAt = cartOrder.updatedAt
                    )

                    val result = cartOrderDao.createOrUpdateCartOrder(newOrder)

                    Timber.d("Result $result")


                    if (result > 0) {
                        async(ioDispatcher) {
                            insertOrIgnoreCartPrice(result.toInt(), newOrder.orderType, newOrder.doesChargesIncluded)
                        }.await()

                        async(ioDispatcher) {
                            selectedDao.insertOrUpdateSelectedOrder(
                                SelectedEntity(
                                    selectedId = SELECTED_ID,
                                    orderId = result.toInt()
                                )
                            )
                        }.await()

                        updateAddOnItems(result.toInt(), newCartOrder.addOnItems)

                        updateCartCharges(result.toInt(), newCartOrder.charges)
                    }else {
                        updateAddOnItems(newOrder.orderId, newCartOrder.addOnItems)

                        updateCartCharges(newOrder.orderId, newCartOrder.charges)
                    }

                    Resource.Success(result > 0)
                } else {
                    Resource.Error("Unable to validate order details.")
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            Resource.Error(e.message)
        }
    }

    override suspend fun deleteCartOrder(orderId: Int): Resource<Boolean> {
        return try {
            val result = withContext(ioDispatcher) {
                cartOrderDao.deleteCartOrder(orderId)
            }

            if (result > 0) {
                withContext(ioDispatcher) {
                    async {
                        updateOrDeleteSelectedOrder()
                    }.await()
                }
            }

            return Resource.Success(result > 0)
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun deleteCartOrders(orderIds: List<Int>): Resource<Boolean> {
        return try {
            val result = withContext(ioDispatcher) {
                cartOrderDao.deleteCartOrders(orderIds)
            }

            if (result > 0) {
                withContext(ioDispatcher) {
                    async {
                        updateOrDeleteSelectedOrder()
                    }.await()
                }
            }

            return Resource.Success(result > 0)
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun validateCustomerPhone(customerPhone: String): ValidationResult {
        if (customerPhone.isEmpty()) {
            return ValidationResult(
                successful = false,
                errorMessage = CART_ORDER_PHONE_EMPTY_ERROR
            )
        }

        if (customerPhone.length < 10 || customerPhone.length > 10) {
            return ValidationResult(
                successful = false,
                errorMessage = CUSTOMER_PHONE_LENGTH_ERROR
            )
        }

        val containsLetters = customerPhone.any { it.isLetter() }

        if (containsLetters) {
            return ValidationResult(
                successful = false,
                errorMessage = CUSTOMER_PHONE_LETTER_ERROR
            )
        }

        return ValidationResult(
            successful = true
        )
    }

    override suspend fun validateAddressName(addressName: String): ValidationResult {
        if (addressName.isEmpty()) {
            return ValidationResult(
                successful = false,
                errorMessage = CART_ORDER_NAME_EMPTY_ERROR,
            )
        }

        if (addressName.length < 2) {
            return ValidationResult(
                successful = false,
                errorMessage = ADDRESS_NAME_LENGTH_ERROR
            )
        }

        return ValidationResult(
            successful = true
        )
    }

    override fun validateAddressShortName(addressShortName: String): ValidationResult {
        if (addressShortName.isEmpty()) {
            return ValidationResult(
                successful = false,
                errorMessage = ORDER_SHORT_NAME_EMPTY_ERROR
            )
        }

        if (addressShortName.length < 2) {
            return ValidationResult(
                successful = false,
                errorMessage = ORDER_PRICE_LESS_THAN_TWO_ERROR
            )
        }

        return ValidationResult(
            successful = true
        )
    }

    private fun validateCustomerAddress(orderType: OrderType, addressId: Int): ValidationResult {
        if (orderType != OrderType.DineIn) {
            if (addressId == 0) {
                return ValidationResult(
                    successful = false,
                    errorMessage = CART_ORDER_NAME_EMPTY_ERROR
                )
            }

            if (addressId < 0) {
                return ValidationResult(
                    successful = false,
                    errorMessage = CART_ORDER_NAME_ERROR
                )
            }
        }

        return ValidationResult(
            successful = true
        )
    }

    private fun validateCustomerPhone(orderType: OrderType, customerId: Int): ValidationResult {
        if (orderType != OrderType.DineIn) {
            if (customerId == 0) {
                return ValidationResult(
                    successful = false,
                    errorMessage = CART_ORDER_PHONE_EMPTY_ERROR
                )
            }

            if (customerId < 0) {
                return ValidationResult(
                    successful = false,
                    errorMessage = CART_ORDER_PHONE_ERROR
                )
            }
        }

        return ValidationResult(
            successful = true
        )
    }

    private suspend fun updateOrDeleteSelectedOrder() {
        withContext(ioDispatcher) {
            val lastId = cartOrderDao.getLastProcessingId()

            lastId?.let {
                selectedDao.insertOrUpdateSelectedOrder(
                    SelectedEntity(
                        selectedId = SELECTED_ID,
                        orderId = it
                    )
                )
            } ?: selectedDao.deleteSelectedOrder(SELECTED_ID)
        }
    }

    private suspend fun updateOrIgnoreSelectedOrder() {
        withContext(ioDispatcher) {
            val currentId = selectedDao.getSelectedCartOrderId()

            if (currentId != null) {
                val status = cartOrderDao.getOrderStatus(currentId)

                if (status == OrderStatus.PLACED) {
                    selectedDao.deleteSelectedOrder(SELECTED_ID)

                    val lastId = cartOrderDao.getLastProcessingId()

                    lastId?.let {
                        selectedDao.insertOrUpdateSelectedOrder(
                            SelectedEntity(
                                SELECTED_ID,
                                orderId = it
                            )
                        )
                    }
                } else {
                }
            } else {
                val lastId = cartOrderDao.getLastProcessingId()

                lastId?.let {
                    selectedDao.insertOrUpdateSelectedOrder(
                        SelectedEntity(
                            SELECTED_ID,
                            orderId = it
                        )
                    )
                }
            }
        }
    }

    private suspend fun insertOrIgnoreCartPrice(orderId: Int, orderType: OrderType, included: Boolean) {
        return withContext(ioDispatcher) {
            var basePrice = 0

            if (included) {
                cartOrderDao.getAllChargesPrice().forEach {
                    if (it.isApplicable && orderType == OrderType.DineOut) {
                        basePrice += it.chargesPrice
                    }
                }
            }

            cartPriceDao.insertOrIgnoreCartPrice(
                CartPriceEntity(
                    orderId = orderId,
                    basePrice = basePrice.toLong(),
                    totalPrice = basePrice.toLong()
                )
            )
        }
    }

    private suspend fun updateAddOnItems(orderId: Int, items: List<Int>): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val cartItems = cartOrderDao.getCartAddOnItems(orderId)

                // Delete items not in the provided list
                cartItems.filterNot { items.contains(it) }.forEach { item ->
                    processAddOnItem(orderId, item, insert = false)
                }

                // Insert items not in the cart
                items.filterNot { cartItems.contains(it) }.forEach { item ->
                    processAddOnItem(orderId, item, insert = true)
                }
            }

            Resource.Success(true)
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    private suspend fun processAddOnItem(orderId: Int, item: Int, insert: Boolean) {
        try {
            if (insert) {
                cartDao.insertCartAddOnItem(CartAddOnItemsEntity(orderId, item))
                insertAddOnItemPrice(orderId, item)
            } else {
                cartDao.deleteCartAddOnItem(orderId, item)
                removeAddOnItemPrice(orderId, item)
            }
        }catch (e: Exception) {
            Timber.e(e)
        }
    }

    private suspend fun updateCartCharges(orderId: Int, items: List<Int>): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val cartItems = cartOrderDao.getCartCharges(orderId)

                // Delete items not in the provided list
                cartItems.filterNot { items.contains(it) }.forEach { item ->
                    processCharges(orderId, item, insert = false)
                }

                // Insert items not in the cart
                items.filterNot { cartItems.contains(it) }.forEach { item ->
                    processCharges(orderId, item, insert = true)
                }

                Resource.Success(true)
            }
        } catch (e: Exception) {
            // Log the exception for debugging purposes
            Resource.Error(e.message)
        }
    }

    private suspend fun processCharges(orderId: Int, chargesId: Int, insert: Boolean) {
        if (insert) {
            cartOrderDao.insertCartCharge(CartChargesEntity(orderId, chargesId)).toInt()
            insertChargesPrice(orderId, chargesId)
        } else {
            cartOrderDao.deleteCartCharges(orderId, chargesId)
            removeChargesPrice(orderId, chargesId)
        }
    }

    private suspend fun insertAddOnItemPrice(orderId: Int, itemId: Int): Int {
        return withContext(ioDispatcher) {
            val addOnPrice = async(ioDispatcher) {
                cartDao.getAddOnPrice(itemId)
            }.await()

            val cartPrice = async(ioDispatcher) {
                cartPriceDao.getCartPriceByOrderId(orderId)
            }.await()

            val basePrice = cartPrice.basePrice + addOnPrice.itemPrice

            val discountPrice = if (!addOnPrice.isApplicable) {
                cartPrice.discountPrice + addOnPrice.itemPrice
            } else cartPrice.discountPrice

            val totalPrice = basePrice - discountPrice

            val priceEntity = CartPriceEntity(
                orderId = orderId,
                basePrice = basePrice,
                discountPrice = discountPrice,
                totalPrice = totalPrice
            )

            cartPriceDao.updateCartPrice(priceEntity)
        }
    }

    private suspend fun removeAddOnItemPrice(orderId: Int, itemId: Int): Int {
        return withContext(ioDispatcher) {
            val addOnPrice = async(ioDispatcher) {
                cartDao.getAddOnPrice(itemId)
            }.await()

            val cartPrice = async(ioDispatcher) {
                cartPriceDao.getCartPriceByOrderId(orderId)
            }.await()

            val basePrice = cartPrice.basePrice - addOnPrice.itemPrice

            val discountPrice = if (!addOnPrice.isApplicable) {
                cartPrice.discountPrice - addOnPrice.itemPrice
            } else cartPrice.discountPrice

            val totalPrice = basePrice - discountPrice

            val priceEntity = CartPriceEntity(
                orderId = orderId,
                basePrice = basePrice,
                discountPrice = discountPrice,
                totalPrice = totalPrice
            )

            cartPriceDao.updateCartPrice(priceEntity)
        }
    }

    private suspend fun insertChargesPrice(orderId: Int, chargesId: Int): Int {
        return withContext(ioDispatcher) {
            val chargesPrice = async(ioDispatcher) {
                cartDao.getChargesPrice(chargesId)
            }.await()

            val cartPrice = async(ioDispatcher) {
                cartPriceDao.getCartPriceByOrderId(orderId)
            }.await()

            val basePrice = cartPrice.basePrice + chargesPrice.chargesPrice

            val discountPrice = cartPrice.discountPrice

            val totalPrice = basePrice - discountPrice

            val priceEntity = CartPriceEntity(
                orderId = orderId,
                basePrice = basePrice,
                discountPrice = discountPrice,
                totalPrice = totalPrice
            )

            cartPriceDao.updateCartPrice(priceEntity)
        }
    }

    private suspend fun removeChargesPrice(orderId: Int, chargesId: Int): Int {
        return withContext(ioDispatcher) {
            val chargesPrice = async(ioDispatcher) {
                cartDao.getChargesPrice(chargesId)
            }.await()

            val cartPrice = async(ioDispatcher) {
                cartPriceDao.getCartPriceByOrderId(orderId)
            }.await()

            val basePrice = cartPrice.basePrice - chargesPrice.chargesPrice

            val discountPrice = cartPrice.discountPrice

            val totalPrice = basePrice - discountPrice

            val priceEntity = CartPriceEntity(
                orderId = orderId,
                basePrice = basePrice,
                discountPrice = discountPrice,
                totalPrice = totalPrice
            )

            cartPriceDao.updateCartPrice(priceEntity)
        }
    }
}