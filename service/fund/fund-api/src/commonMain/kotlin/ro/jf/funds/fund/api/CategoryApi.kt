package ro.jf.funds.fund.api

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.CreateCategoryTO
import ro.jf.funds.fund.api.model.CategoryTO

interface CategoryApi {
    suspend fun listCategories(userId: Uuid): List<CategoryTO>
    suspend fun createCategory(userId: Uuid, request: CreateCategoryTO): CategoryTO
    suspend fun deleteCategoryById(userId: Uuid, categoryId: Uuid)
}
