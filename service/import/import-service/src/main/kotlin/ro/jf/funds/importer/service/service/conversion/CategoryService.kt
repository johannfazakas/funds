package ro.jf.funds.importer.service.service.conversion

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.CategoryTO
import ro.jf.funds.fund.sdk.CategorySdk
import ro.jf.funds.importer.service.domain.Store

class CategoryService(
    private val categorySdk: CategorySdk,
) {
    suspend fun getCategoryStore(userId: Uuid): Store<String, CategoryTO> = categorySdk
        .listCategories(userId)
        .associateBy { it.name }
        .let { Store(it) }
}
