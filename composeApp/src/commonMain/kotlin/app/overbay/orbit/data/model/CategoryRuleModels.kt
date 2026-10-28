package app.overbay.orbit.data.model

import app.overbay.orbit.domain.Cents
import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val name: String,
    val color: String = "#888888",
    val icon: String? = null,
    val isTransfer: Boolean = false,
    val isIncome: Boolean = false,
    val sortOrder: Int = 0,
    val mhaDefaultEligible: Boolean = false,
    val mhaDefaultIgnored: Boolean = false,
    val createdAt: String = ""
)

object CategoryEmojiMapper {
    val PRESET_EMOJIS: List<String> = listOf(
        "🚗", "👶", "💡", "💵", "💝", "💳", "🍽️", "🎓",
        "🎬", "👨‍👩‍👧", "💸", "🎉", "🎁", "🛒", "💪", "🏠",
        "💰", "🔄", "📈", "⚖️", "🏦", "🏥", "💆", "🐾",
        "💹", "🛍️", "💻", "🧾", "✈️", "❓", "💱", "💼"
    )

    private val LEGACY_ICON_TO_EMOJI: Map<String, String> = mapOf(
        "payments" to "💰",
        "shopping_cart" to "🛒",
        "restaurant" to "🍽️",
        "home" to "🏠",
        "bolt" to "💡",
        "directions_car" to "🚗",
        "medical_services" to "🏥",
        "volunteer_activism" to "💝",
        "movie" to "🎬",
        "swap_horiz" to "💳",
        "sync_alt" to "🔄",
        "help_outline" to "❓"
    )

    fun resolveEmoji(rawIcon: String?): String? {
        val trimmed = rawIcon?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return LEGACY_ICON_TO_EMOJI[trimmed] ?: trimmed
    }
}

val Category.displayEmoji: String?
    get() = CategoryEmojiMapper.resolveEmoji(icon)

val Category.labelWithEmoji: String
    get() = displayEmoji?.let { "$it $name" } ?: name

@Serializable
data class CategoryGroup(
    val name: String,
    val categories: List<Category> = emptyList()
)

@Serializable
enum class RuleConditionField {
    MERCHANT,
    AMOUNT,
    ACCOUNT,
    CATEGORY,
    NOTES,
    ORIGINAL_DESCRIPTION,
    ACCOUNT_ID,
    CATEGORY_ID
}

typealias RuleField = RuleConditionField

@Serializable
enum class RuleOperator {
    CONTAINS,
    EXACT,
    STARTS_WITH,
    ENDS_WITH,
    EQUALS,
    GREATER_THAN,
    LESS_THAN,
    BETWEEN,
    IS,
    IS_NOT,
    REGEX
}

@Serializable
data class RuleCondition(
    val field: RuleConditionField,
    val operator: RuleOperator,
    val value: String = "",
    val amountCentsValue: Cents? = null,
    val minAmountCentsValue: Cents? = null,
    val maxAmountCentsValue: Cents? = null,
    val targetId: Long? = null
)

@Serializable
enum class RuleActionType {
    SET_CATEGORY,
    RENAME_MERCHANT,
    MARK_TRANSFER,
    MARK_IGNORED,
    SET_MHA_ELIGIBLE,
    RENAME,
    CATEGORIZE
}

@Serializable
data class RuleAction(
    val type: RuleActionType,
    val value: String = "",
    val textValue: String? = null,
    val targetCategoryId: Long? = null,
    val booleanValue: Boolean? = null
)

@Serializable
data class TransactionRule(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val name: String,
    val conditions: List<RuleCondition> = emptyList(),
    val actions: List<RuleAction> = emptyList(),
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = ""
)
