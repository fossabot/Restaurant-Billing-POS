package com.niyaj.poposroom.features.main_feed.domain.repository

import com.niyaj.poposroom.features.category.domain.model.Category
import com.niyaj.poposroom.features.main_feed.domain.model.ProductWithFlowQuantity
import com.niyaj.poposroom.features.selected.domain.model.Selected
import kotlinx.coroutines.flow.Flow

interface MainFeedRepository {

    fun getAllCategory(): Flow<List<Category>>

    suspend fun getAllProduct(searchText: String, selectedCategory: Int = 0): Flow<List<ProductWithFlowQuantity>>

    fun getSelectedOrder(): Flow<Selected?>
}