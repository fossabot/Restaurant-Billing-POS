package com.niyaj.poposroom.features.cart.presentation.dine_in

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.niyaj.poposroom.R
import com.niyaj.poposroom.features.cart.presentation.components.CartFooterPlaceOrder
import com.niyaj.poposroom.features.cart.presentation.components.CartItems
import com.niyaj.poposroom.features.common.components.ItemNotAvailable
import com.niyaj.poposroom.features.common.components.LoadingIndicator
import com.niyaj.poposroom.features.common.utils.UiEvent
import com.niyaj.poposroom.features.common.utils.isScrollingUp
import com.niyaj.poposroom.features.destinations.AddEditCartOrderScreenDestination
import com.niyaj.poposroom.features.destinations.CartOrderScreenDestination
import com.niyaj.poposroom.features.destinations.MainFeedScreenDestination
import com.ramcosta.composedestinations.navigation.navigate
import kotlinx.coroutines.flow.collectLatest

@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun DineInScreen(
    navController: NavController,
    viewModel: DineInViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val lazyListState = rememberLazyListState()
    val dineInOrders = viewModel.state.collectAsStateWithLifecycle().value.items
    val isLoading = viewModel.state.collectAsStateWithLifecycle().value.isLoading

    val countTotalDineInItems = dineInOrders.size
    val selectedDineInOrder = viewModel.selectedItems.toList()
    val countSelectedDineInItem = selectedDineInOrder.size

    val addOnItems = viewModel.addOnItems.collectAsStateWithLifecycle().value

    LaunchedEffect(key1 = true) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.OnSuccess -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.successMessage,
                        actionLabel = "View",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigate(CartOrderScreenDestination())
                    }
                }

                is UiEvent.OnError -> {
                    snackbarHostState.showSnackbar(
                        message = event.errorMessage,
                        duration = SnackbarDuration.Short
                    )
                }

                is UiEvent.IsLoading -> {}
            }
        }
    }


    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = dineInOrders.isNotEmpty() && lazyListState.isScrollingUp(),
                label = "BottomBar",
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { fullHeight ->
                        fullHeight / 4
                    }
                ),
                exit = fadeOut() + slideOutVertically(
                    targetOffsetY = { fullHeight ->
                        fullHeight / 4
                    }
                )
            ) {
                CartFooterPlaceOrder(
                    countTotalItems = countTotalDineInItems,
                    countSelectedItem = countSelectedDineInItem,
                    showPrintBtn = false,
                    onClickSelectAll = {
                        viewModel.onEvent(DineInEvent.SelectAllDineInOrder)
                    },
                    onClickPlaceAllOrder = {
                        viewModel.onEvent(DineInEvent.PlaceAllDineInOrder)
                    },
                    onClickPrintAllOrder = {}
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        if (isLoading) {
            LoadingIndicator()
        } else if (countTotalDineInItems == 0) {
            ItemNotAvailable(
                text = "Dine in order not available",
                buttonText = "Add Item To Cart",
                image = painterResource(R.drawable.emptycart),
                onClick = {
                    navController.navigate(MainFeedScreenDestination())
                }
            )
        } else {
            CartItems(
                listState = lazyListState,
                cartItems = dineInOrders,
                selectedCartItems = selectedDineInOrder,
                addOnItems = addOnItems,
                onSelectCartOrder = {
                    viewModel.onEvent(DineInEvent.SelectDineInOrder(it))
                },
                onClickEditOrder = {
                    navController.navigate(AddEditCartOrderScreenDestination(it))
                },
                onClickViewOrder = {
//            navController.navigate(OrderDetailsScreenDestination(it))
                },
                onClickDecreaseQty = { cartOrderId, productId ->
                    viewModel.onEvent(DineInEvent.DecreaseQuantity(cartOrderId, productId))
                },
                onClickIncreaseQty = { cartOrderId, productId ->
                    viewModel.onEvent(DineInEvent.IncreaseQuantity(cartOrderId, productId))
                },
                onClickAddOnItem = { addOnItemId, cartOrderId ->
                    viewModel.onEvent(DineInEvent.UpdateAddOnItemInCart(addOnItemId, cartOrderId))
                },
                onClickPlaceOrder = {
                    viewModel.onEvent(DineInEvent.PlaceDineInOrder(it))
                },
            )
        }
    }
}