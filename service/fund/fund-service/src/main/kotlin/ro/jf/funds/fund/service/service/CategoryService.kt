package ro.jf.funds.fund.service.service

import ro.jf.funds.fund.api.model.CreateCategoryTO
import ro.jf.funds.fund.service.domain.FundServiceException
import ro.jf.funds.fund.service.domain.Category
import ro.jf.funds.fund.service.domain.RecordFilter
import ro.jf.funds.fund.service.persistence.CategoryRepository
import ro.jf.funds.fund.service.persistence.RecordRepository
import java.util.*

private val CATEGORY_PATTERN = Regex("^[a-zA-Z0-9_]+$")

class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val recordRepository: RecordRepository,
) {
    suspend fun listCategories(userId: UUID): List<Category> {
        return categoryRepository.list(userId)
    }

    suspend fun createCategory(userId: UUID, request: CreateCategoryTO): Category {
        require(request.name.isNotBlank()) { "Category name must not be blank" }
        require(request.name.matches(CATEGORY_PATTERN)) { "Category name must contain only letters, numbers or underscore" }
        val existing = categoryRepository.findByName(userId, request.name)
        if (existing != null) {
            throw FundServiceException.CategoryNameAlreadyExists(request.name)
        }
        return categoryRepository.save(userId, request)
    }

    suspend fun deleteCategory(userId: UUID, categoryId: UUID) {
        val category = categoryRepository.findById(userId, categoryId)
            ?: throw FundServiceException.CategoryNotFound(categoryId)
        val records = recordRepository.list(userId, RecordFilter(category = category.name))
        if (records.items.isNotEmpty()) {
            throw FundServiceException.CategoryHasRecords(categoryId)
        }
        categoryRepository.deleteById(userId, categoryId)
    }
}
