package com.niyaj.poposroom.features.cart.data.repository

import com.niyaj.poposroom.features.addon_item.domain.model.AddOnItem
import com.niyaj.poposroom.features.cart.data.dao.CartDao
import com.niyaj.poposroom.features.cart.domain.model.CartEntity
import com.niyaj.poposroom.features.cart.domain.model.CartItem
import com.niyaj.poposroom.features.cart.domain.model.CartProductItem
import com.niyaj.poposroom.features.cart.domain.model.OrderPrice
import com.niyaj.poposroom.features.cart.domain.model.OrderWithCart
import com.niyaj.poposroom.features.cart.domain.repository.CartRepository
import com.niyaj.poposroom.features.cart_order.data.dao.CartOrderDao
import com.niyaj.poposroom.features.cart_order.domain.model.CartAddOnItems
import com.niyaj.poposroom.features.cart_order.domain.model.CartCharges
import com.niyaj.poposroom.features.cart_order.domain.utils.OrderType
import com.niyaj.poposroom.features.common.utils.Dispatcher
import com.niyaj.poposroom.features.common.utils.PoposDispatchers
import com.niyaj.poposroom.features.common.utils.Resource
import com.niyaj.poposroom.features.common.utils.toTimeSpan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

class CartRepositoryImpl(
    private val cartDao: CartDao,
    private val cartOrderDao: CartOrderDao,
    @Dispatcher(PoposDispatchers.IO)
    private val ioDispatcher: CoroutineDispatcher
) : CartRepository {

    override fun getAllAddOnItems(): Flow<List<AddOnItem>> {
        return cartDao.getAllAddOnItems()
    }

    override suspend fun getAllCartOrders(): Flow<List<OrderWithCart>> {
        return withContext(ioDispatcher) {
            cartDao.getAllCartOrders()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getAllDineInOrder(): Flow<List<CartItem>> {
        return withContext(ioDispatcher) {
            cartDao.getAllOrders(OrderType.DineIn).mapLatest { list ->
                val data = list.filter { it.cartItems.isNotEmpty() }

                mapCartOrderToCartItem(data)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getAllDineOutOrder(): Flow<List<CartItem>> {
        return withContext(ioDispatcher) {
            cartDao.getAllOrders(OrderType.DineOut).mapLatest { list ->
                val data = list.filter { it.cartItems.isNotEmpty() }

                mapCartOrderToCartItem(data)
            }
        }
    }

    override suspend fun addProductToCart(orderId: Int, productId: Int): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val cart = cartDao.getCartOrderById(orderId, productId)

                if (cart != null) {
                    val qty = cart.quantity + 1

                    val result = cartDao.updateQuantity(orderId, productId, qty)

                    Resource.Success(result > 0)

                } else {
                    val newCartEntity = CartEntity(
                        orderId = orderId,
                        productId = productId,
                        quantity = 1
                    )

                    val result = cartDao.addOrRemoveCartProduct(newCartEntity)

                    Resource.Success(result > 0)
                }
            }
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun removeProductFromCart(orderId: Int, productId: Int): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val cart = cartDao.getCartOrderById(orderId, productId)

                if (cart != null) {
                    if (cart.quantity == 1) {
                        val result = cartDao.deleteProductFromCart(orderId, productId)

                        Resource.Success(result > 0)
                    } else {
                        val qty = cart.quantity - 1

                        val result = cartDao.updateQuantity(orderId, productId, qty)

                        Resource.Success(result > 0)
                    }
                } else {
                    Resource.Error("Unable to find cart order")
                }
            }
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun placeOrder(orderId: Int): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val result = cartOrderDao.placeOrder(orderId)

                Resource.Success(result > 0)
            }
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun placeAllOrder(orderIds: List<Int>): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val result = cartOrderDao.placeAllOrder(orderIds)

                Resource.Success(result > 0)
            }
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun updateAddOnItem(orderId: Int, itemId: Int): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val item = cartOrderDao.getCartAddOnItemById(orderId, itemId)

                val result = if (item != null) {
                    cartOrderDao.deleteCartAddOnItem(orderId, itemId)
                } else {
                    cartOrderDao.insertCartAddOnItem(CartAddOnItems(orderId, itemId)).toInt()
                }

                Resource.Success(result > 0)
            }
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun updateCharges(orderId: Int, chargesId: Int): Resource<Boolean> {
        return try {
            withContext(ioDispatcher) {
                val item = cartOrderDao.getCartChargesById(orderId, chargesId)

                val result = if (item != null) {
                    cartOrderDao.deleteCartCharges(orderId, chargesId)
                } else {
                    cartOrderDao.insertCartCharge(CartCharges(orderId, chargesId)).toInt()
                }

                Resource.Success(result > 0)
            }
        } catch (e: Exception) {
            Resource.Error(e.message)
        }
    }

    override suspend fun deleteProductFromCart(orderId: Int, productId: Int): Resource<Boolean> {
        return try {
            val result = cartDao.deleteProductFromCart(orderId, productId)

            Resource.Success(result > 0)
        } catch (e: Exception) {
            Resource.Error("Unable to delete cart")
        }
    }

    override suspend fun getProductQuantity(orderId: Int, productId: Int): Flow<Int> {
        return withContext(ioDispatcher) {
            cartDao.getProductQuantity(orderId, productId).distinctUntilChanged()
        }
    }

    private suspend fun countTotalPrice(
        orderId: Int,
        included: Boolean,
        orderType: OrderType
    ): OrderPrice {
        return coroutineScope {
            var totalPrice = 0
            var discountPrice = 0

            val addOnPrice = async(ioDispatcher) {
                val result = getAddOnTotalPrice(orderId)
                totalPrice += result.first
                discountPrice += result.second
            }

            val charges = async(ioDispatcher) {
                totalPrice += getChargesPrice(orderId)
            }

            val chargesPrice = async(ioDispatcher) {
                totalPrice += if (included) {
                    getChargesPrice(orderType)
                } else 0
            }

            val productPrice = async(ioDispatcher) {
                totalPrice += getProductPrice(orderId)
            }

            addOnPrice.await()
            charges.await()
            chargesPrice.await()
            productPrice.await()

            OrderPrice(totalPrice, discountPrice)
        }
    }

    private suspend fun getAddOnTotalPrice(orderId: Int): Pair<Int, Int> {
        return coroutineScope {
            var totalPrice = 0
            var discountPrice = 0

            cartOrderDao.getCartAddOnItems(orderId).map { list ->
                cartDao.getAddOnPrice(list).forEach {
                    if (it.isApplicable) {
                        totalPrice += it.itemPrice
                    } else {
                        discountPrice += it.itemPrice
                    }
                }
            }

            Pair(totalPrice, discountPrice)
        }
    }

    private suspend fun getChargesPrice(orderId: Int): Int {
        var totalPrice = 0

        withContext(ioDispatcher) {
            cartOrderDao.getCartCharges(orderId).map { list ->
                cartDao.getChargesPrice(list).forEach {
                    totalPrice += it.chargesPrice
                }
            }.collect()
        }

        return totalPrice
    }

    private suspend fun getChargesPrice(orderType: OrderType): Int {
        var totalPrice = 0

        withContext(ioDispatcher) {
            cartDao.getAllCharges().map { list ->
                list.forEach {
                    if (it.isApplicable && orderType == OrderType.DineOut) {
                        totalPrice += it.chargesPrice
                    }
                }
            }.collect()
        }

        return totalPrice
    }

    private suspend fun getProductPrice(orderId: Int): Int {
        var totalPrice = 0

        withContext(ioDispatcher) {
            cartDao.getCartProductsByOrderId(orderId).map { list ->
                list.cartItems.forEach { it ->
                    cartDao.getProductPriceAndQuantity(list.cartOrder.orderId, it.productId)
                        .map {
                            totalPrice += it.productPrice.times(it.quantity)
                        }.collect()
                }
            }.collect()
        }

        return totalPrice
    }

    private suspend fun mapCartOrderToCartItem(cartOrders: List<OrderWithCart>): List<CartItem> {
        return coroutineScope {
            cartOrders.map { order ->
                var totalPrice = 0
                var discountPrice = 0

                val addOnItems = async {
                    cartOrderDao.getCartAddOnItems(order.cartOrder.orderId)
                }

                val charges = async(ioDispatcher) {
                    cartOrderDao.getCartCharges(order.cartOrder.orderId)
                }

                val cartProducts = async(ioDispatcher) {
                    order.cartItems.map { cartItem ->
                        val product = cartDao.getProductById(cartItem.productId)

                        totalPrice += product.productPrice.times(cartItem.quantity)

                        CartProductItem(
                            productId = product.productId,
                            productName = product.productName,
                            productPrice = product.productPrice,
                            productQuantity = cartItem.quantity
                        )
                    }
                }

                val address = async(ioDispatcher) {
                    if (order.cartOrder.orderType != OrderType.DineIn) {
                        cartDao.getAddressById(order.cartOrder.addressId).shortName
                    } else null
                }

                val customer = async(ioDispatcher) {
                    if (order.cartOrder.orderType != OrderType.DineIn) {
                        cartDao.getCustomerById(order.cartOrder.customerId).customerPhone
                    } else null
                }

                CartItem(
                    orderId = order.cartOrder.orderId,
                    orderType = order.cartOrder.orderType,
                    cartProducts = cartProducts.await(),
                    addOnItems = addOnItems.await().distinctUntilChanged(),
                    charges = charges.await().distinctUntilChanged(),
                    customerPhone = customer.await(),
                    customerAddress = address.await(),
                    updatedAt = (order.cartOrder.updatedAt ?: order.cartOrder.createdAt).toTimeSpan,
                    orderPrice = OrderPrice(totalPrice, discountPrice)
                )
            }
        }
    }

    private suspend fun mapCartToCartItem(cartOrders: List<OrderWithCart>): List<CartItem> {
        return cartOrders.map { order ->
            val addOnItems = coroutineScope {
                async {
                    cartOrderDao.getCartAddOnItems(order.cartOrder.orderId)
                }
            }

            val charges = coroutineScope {
                async(ioDispatcher) {
                    cartOrderDao.getCartCharges(order.cartOrder.orderId)
                }
            }

            val cartProducts = coroutineScope {
                async(ioDispatcher) {
                    order.cartItems.map { cartItem ->
                        val product = cartDao.getProductById(cartItem.productId)
                        CartProductItem(
                            productId = product.productId,
                            productName = product.productName,
                            productPrice = product.productPrice,
                            productQuantity = cartItem.quantity
                        )
                    }
                }
            }

            val address = coroutineScope {
                async(ioDispatcher) {
                    if (order.cartOrder.orderType != OrderType.DineIn) {
                        cartDao.getAddressById(order.cartOrder.addressId).shortName
                    } else null
                }
            }

            val customer = coroutineScope {
                async(ioDispatcher) {
                    if (order.cartOrder.orderType != OrderType.DineIn) {
                        cartDao.getCustomerById(order.cartOrder.customerId).customerPhone
                    } else null
                }
            }

            CartItem(
                orderId = order.cartOrder.orderId,
                orderType = order.cartOrder.orderType,
                cartProducts = cartProducts.await(),
                addOnItems = addOnItems.await().distinctUntilChanged(),
                charges = charges.await().distinctUntilChanged(),
                customerPhone = customer.await(),
                customerAddress = address.await(),
                updatedAt = (order.cartOrder.updatedAt ?: order.cartOrder.createdAt).toTimeSpan,
                orderPrice = countTotalPrice(
                    order.cartOrder.orderId,
                    order.cartOrder.doesChargesIncluded,
                    order.cartOrder.orderType
                )
            )
        }
    }
}