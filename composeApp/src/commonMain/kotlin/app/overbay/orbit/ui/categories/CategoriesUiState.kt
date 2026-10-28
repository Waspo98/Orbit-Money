package app.overbay.orbit.ui.categories

import app.overbay.orbit.data.model.Category
import app.overbay.orbit.domain.Cents

data class CategorySummary(
    val category: Category,
    val transactionCount: Int = 0,
    val totalAmountCents: Cents = 0L
)

data class CategoryGroupDisplay(
    val title: String,
    val items: List<CategorySummary>,
    val totalCents: Cents
)

data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val groups: List<CategoryGroupDisplay> = emptyList(),
    val inspectingCategoryId: Long? = null,
    val editingCategory: Category? = null,
    val showEditorDialog: Boolean = false,
    val deleteConfirmCategoryId: Long? = null,
    val isReorderMode: Boolean = false,
    val isLoading: Boolean = false,
    val totalCategoriesCount: Int = 0
) {
    val inspectingSummary: CategorySummary?
        get() = groups.asSequence()
            .flatMap { it.items.asSequence() }
            .find { it.category.id == inspectingCategoryId }
    val inspectingGroupTitle: String?
        get() = groups.find { grp -> grp.items.any { it.category.id == inspectingCategoryId } }?.title
}
