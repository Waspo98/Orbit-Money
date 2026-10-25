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
    val editingCategory: Category? = null,
    val showEditorDialog: Boolean = false,
    val deleteConfirmCategoryId: Long? = null,
    val isLoading: Boolean = false,
    val totalCategoriesCount: Int = 0
)
