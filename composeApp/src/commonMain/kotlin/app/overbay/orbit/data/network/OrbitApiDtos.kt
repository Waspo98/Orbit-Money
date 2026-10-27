package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.MoneyUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: Long = 0L,
    val name: String,
    val type: String,
    val institution: String? = null,
    @SerialName("account_number_last4") val accountNumberLast4: String? = null,
    val balance: Double? = null,
    @SerialName("current_balance") val currentBalance: Double? = null,
    @SerialName("estimated_value") val estimatedValue: Double? = null,
    @SerialName("is_manual") @Serializable(with = FlexibleBooleanSerializer::class) val isManual: Boolean = false,
    @SerialName("is_archived") @Serializable(with = FlexibleBooleanSerializer::class) val isArchived: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("mha_default_eligible") @Serializable(with = FlexibleBooleanSerializer::class) val mhaDefaultEligible: Boolean = false,
    @SerialName("simplefin_account_id") val simplefinAccountId: String? = null,
    @SerialName("credit_card_profile") val creditCardProfile: CreditCardProfileDto? = null
) {
    fun toDomain(): Account = Account(
        id = id,
        name = name,
        type = AccountType.fromRaw(type),
        institution = institution,
        accountNumberLast4 = accountNumberLast4,
        currentBalanceCents = MoneyUtils.dollarsToCents(currentBalance ?: balance ?: 0.0),
        estimatedValueCents = estimatedValue?.let { MoneyUtils.dollarsToCents(it) },
        isManual = isManual,
        isArchived = isArchived,
        sortOrder = sortOrder,
        mhaDefaultEligible = mhaDefaultEligible,
        simplefinAccountId = simplefinAccountId,
        creditCardProfile = creditCardProfile?.toDomain()
    )
}

@Serializable
data class CreditCardProfileDto(
    val id: Long? = null,
    @SerialName("account_id") val accountId: Long = 0L,
    @SerialName("annual_fee") val annualFee: Double? = null,
    @SerialName("annual_fee_post_date") val annualFeePostDate: String? = null,
    @SerialName("credit_limit") val creditLimit: Double? = null,
    @SerialName("statement_closing_day") val statementClosingDay: Int? = null,
    @SerialName("payment_due_day") val paymentDueDay: Int? = null,
    @SerialName("authorized_users") val authorizedUsers: List<String> = emptyList()
) {
    fun toDomain(): CreditCardProfile = CreditCardProfile(
        id = id ?: 0L,
        accountId = accountId,
        annualFeeCents = annualFee?.let { MoneyUtils.dollarsToCents(it) },
        annualFeePostDate = annualFeePostDate,
        creditLimitCents = creditLimit?.let { MoneyUtils.dollarsToCents(it) },
        statementClosingDay = statementClosingDay,
        paymentDueDay = paymentDueDay,
        authorizedUsers = authorizedUsers
    )
}

@Serializable
data class TransactionDto(
    val id: Long = 0L,
    @SerialName("account_id") val accountId: Long,
    @SerialName("account_name") val accountName: String? = null,
    val date: String,
    @SerialName("original_date") val originalDate: String? = null,
    @SerialName("edited_date") val editedDate: String? = null,
    val amount: Double,
    @SerialName("original_amount") val originalAmount: Double? = null,
    @SerialName("edited_amount") val editedAmount: Double? = null,
    val merchant: String? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("category_color") val categoryColor: String? = null,
    @SerialName("category_icon") val categoryIcon: String? = null,
    @SerialName("is_transfer") @Serializable(with = FlexibleBooleanSerializer::class) val isTransfer: Boolean = false,
    @SerialName("is_ignored") @Serializable(with = FlexibleBooleanSerializer::class) val isIgnored: Boolean = false,
    @SerialName("original_merchant") val originalMerchant: String? = null,
    @SerialName("original_description") val originalDescription: String? = null,
    @SerialName("original_category_id") val originalCategoryId: Long? = null,
    @SerialName("original_is_transfer") @Serializable(with = FlexibleNullableBooleanSerializer::class) val originalIsTransfer: Boolean? = null,
    @SerialName("original_is_ignored") @Serializable(with = FlexibleNullableBooleanSerializer::class) val originalIsIgnored: Boolean? = null,
    @SerialName("edited_merchant") val editedMerchant: String? = null,
    @SerialName("edited_merchant_source") val editedMerchantSource: String? = null,
    @SerialName("edited_category_id") val editedCategoryId: Long? = null,
    @SerialName("edited_category_id_source") val editedCategoryIdSource: String? = null,
    @SerialName("edited_is_transfer") @Serializable(with = FlexibleNullableBooleanSerializer::class) val editedIsTransfer: Boolean? = null,
    @SerialName("edited_is_transfer_source") val editedIsTransferSource: String? = null,
    @SerialName("edited_is_ignored") @Serializable(with = FlexibleNullableBooleanSerializer::class) val editedIsIgnored: Boolean? = null,
    @SerialName("edited_is_ignored_source") val editedIsIgnoredSource: String? = null,
    @SerialName("mha_eligible") @Serializable(with = FlexibleNullableBooleanSerializer::class) val mhaEligible: Boolean? = null,
    @SerialName("edited_mha_eligible") @Serializable(with = FlexibleNullableBooleanSerializer::class) val editedMhaEligible: Boolean? = null,
    @SerialName("edited_mha_eligible_source") val editedMhaEligibleSource: String? = null,
    val notes: String? = null,
    @SerialName("transfer_pair_id") val transferPairId: Long? = null,
    @SerialName("is_pending") @Serializable(with = FlexibleBooleanSerializer::class) val isPending: Boolean = false,
    @SerialName("external_id") val externalId: String? = null,
    val source: String = "manual",
    @SerialName("merchant_logo") val merchantLogo: MerchantLogoDto? = null
) {
    fun toDomain(): Transaction {
        val origMerchant = originalMerchant ?: merchant ?: ""
        val origAmountCents = MoneyUtils.dollarsToCents(originalAmount ?: amount)
        val editSrc = when {
            editedMerchantSource?.lowercase() == "user" || editedCategoryIdSource?.lowercase() == "user" -> EditSource.USER
            editedMerchantSource?.startsWith("rule", ignoreCase = true) == true || editedCategoryIdSource?.startsWith("rule", ignoreCase = true) == true -> EditSource.RULE
            editedMerchantSource?.startsWith("system", ignoreCase = true) == true || editedCategoryIdSource?.startsWith("system", ignoreCase = true) == true -> EditSource.SYSTEM
            else -> EditSource.NONE
        }

        return Transaction(
            id = id,
            accountId = accountId,
            accountName = accountName ?: "",
            originalDate = originalDate ?: date,
            originalMerchant = origMerchant,
            originalDescription = originalDescription,
            originalAmountCents = origAmountCents,
            originalCategoryId = originalCategoryId ?: categoryId,
            categoryName = categoryName,
            categoryColor = categoryColor,
            categoryIcon = categoryIcon,
            isTransfer = originalIsTransfer ?: isTransfer,
            isIgnored = originalIsIgnored ?: isIgnored,
            transferPairId = transferPairId,
            isPending = isPending,
            notes = notes,
            editedDate = editedDate,
            editedMerchant = editedMerchant ?: if (editedMerchantSource != null) merchant else null,
            editedMerchantSource = editedMerchantSource,
            editedAmountCents = editedAmount?.let { MoneyUtils.dollarsToCents(it) },
            editedCategoryId = editedCategoryId ?: if (editedCategoryIdSource != null) categoryId else null,
            editedCategoryIdSource = editedCategoryIdSource,
            editedIsTransfer = editedIsTransfer ?: if (editedIsTransferSource != null) isTransfer else null,
            editedIsTransferSource = editedIsTransferSource,
            editedIsIgnored = editedIsIgnored ?: if (editedIsIgnoredSource != null) isIgnored else null,
            editedIsIgnoredSource = editedIsIgnoredSource,
            editedMhaEligible = editedMhaEligible ?: mhaEligible,
            editedMhaEligibleSource = editedMhaEligibleSource,
            externalId = externalId,
            editSource = editSrc,
            merchantLogo = merchantLogo?.toDomain(),
            source = source
        )
    }
}

@Serializable
data class TransactionListResponseDto(
    val items: List<TransactionDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 50,
    @SerialName("totalPages") val totalPages: Int = 1,
    @SerialName("grandTotal") val grandTotal: Int = 0,
    @SerialName("monthlyIncome") val monthlyIncome: Double = 0.0,
    @SerialName("monthlyExpenses") val monthlyExpenses: Double = 0.0,
    @SerialName("monthlyNet") val monthlyNet: Double = 0.0
)

@Serializable
data class CategoryDto(
    val id: Long = 0L,
    val name: String,
    val color: String = "#888888",
    val icon: String? = null,
    @SerialName("is_transfer") @Serializable(with = FlexibleBooleanSerializer::class) val isTransfer: Boolean = false,
    @SerialName("is_income") @Serializable(with = FlexibleBooleanSerializer::class) val isIncome: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("mha_default_eligible") @Serializable(with = FlexibleBooleanSerializer::class) val mhaDefaultEligible: Boolean = false,
    @SerialName("mha_default_ignored") @Serializable(with = FlexibleBooleanSerializer::class) val mhaDefaultIgnored: Boolean = false
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isTransfer = isTransfer,
        isIncome = isIncome,
        sortOrder = sortOrder,
        mhaDefaultEligible = mhaDefaultEligible,
        mhaDefaultIgnored = mhaDefaultIgnored
    )
}

@Serializable
data class CategoryListResponseDto(
    val items: List<CategoryDto> = emptyList()
)

@Serializable
data class BudgetDto(
    val id: Long = 0L,
    @SerialName("category_id") val categoryId: Long,
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("category_color") val categoryColor: String = "#888888",
    @SerialName("category_icon") val categoryIcon: String? = null,
    val amount: Double = 0.0,
    val spent: Double = 0.0,
    @Serializable(with = FlexibleBooleanSerializer::class) val rollover: Boolean = false
) {
    fun toDomain(): CategoryBudget = CategoryBudget(
        id = id,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryColor = categoryColor,
        categoryIcon = categoryIcon,
        amountCents = MoneyUtils.dollarsToCents(amount),
        rollover = rollover,
        spentCents = MoneyUtils.dollarsToCents(spent)
    )
}

@Serializable
data class BudgetOverviewItemDto(
    @SerialName("budget_id") val budgetId: Long? = null,
    val category: CategoryDto? = null,
    val amount: Double? = null,
    @SerialName("budget_amount") val budgetAmount: Double? = null,
    val spent: Double = 0.0,
    @Serializable(with = FlexibleNullableBooleanSerializer::class) val rollover: Boolean? = false
) {
    fun toDomain(): CategoryBudget = CategoryBudget(
        id = budgetId ?: category?.id ?: 0L,
        categoryId = category?.id ?: 0L,
        categoryName = category?.name ?: "",
        categoryColor = category?.color ?: "#888888",
        categoryIcon = category?.icon,
        amountCents = MoneyUtils.dollarsToCents(amount ?: budgetAmount ?: 0.0),
        rollover = rollover ?: false,
        spentCents = MoneyUtils.dollarsToCents(spent)
    )
}

@Serializable
data class MonthlyBudgetOverviewDto(
    val month: String = "",
    val budgets: List<BudgetDto> = emptyList(),
    val budgeted: List<BudgetOverviewItemDto> = emptyList(),
    @SerialName("total_budgeted") val totalBudgeted: Double = 0.0,
    @SerialName("total_spent") val totalSpent: Double = 0.0,
    @SerialName("daily_safe_spend") val dailySafeSpend: Double = 0.0,
    @SerialName("days_remaining") val daysRemaining: Int = 0
) {
    fun toDomainList(): List<CategoryBudget> =
        if (budgets.isNotEmpty()) budgets.map { it.toDomain() }
        else budgeted.map { it.toDomain() }
}

@Serializable
data class GoalDto(
    val id: Long = 0L,
    val name: String,
    @SerialName("target_amount") val targetAmount: Double = 0.0,
    @SerialName("current_amount") val currentAmount: Double = 0.0,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("linked_account_id") val linkedAccountId: Long? = null,
    @SerialName("sort_order") val sortOrder: Int = 0
) {
    fun toDomain(): Goal = Goal(
        id = id,
        name = name,
        targetAmountCents = MoneyUtils.dollarsToCents(targetAmount),
        currentAmountCents = MoneyUtils.dollarsToCents(currentAmount),
        targetDate = targetDate,
        linkedAccountId = linkedAccountId,
        sortOrder = sortOrder
    )
}

@Serializable
data class RuleConditionDto(
    val field: String = "merchant",
    val operator: String = "contains",
    @Serializable(with = FlexibleStringSerializer::class) val value: String = ""
) {
    fun toDomain(): RuleCondition = RuleCondition(
        field = when (field.lowercase()) {
            "merchant" -> RuleConditionField.MERCHANT
            "amount" -> RuleConditionField.AMOUNT
            "account", "account_id" -> RuleConditionField.ACCOUNT_ID
            "notes" -> RuleConditionField.NOTES
            "original_description" -> RuleConditionField.ORIGINAL_DESCRIPTION
            else -> RuleConditionField.CATEGORY_ID
        },
        operator = when (operator.lowercase()) {
            "exact", "equals" -> RuleOperator.EXACT
            "starts_with" -> RuleOperator.STARTS_WITH
            "ends_with" -> RuleOperator.ENDS_WITH
            "greater_than" -> RuleOperator.GREATER_THAN
            "less_than" -> RuleOperator.LESS_THAN
            "regex" -> RuleOperator.REGEX
            "between" -> RuleOperator.BETWEEN
            "is" -> RuleOperator.IS
            "is_not" -> RuleOperator.IS_NOT
            else -> RuleOperator.CONTAINS
        },
        value = value
    )
}

@Serializable
data class RuleActionDto(
    val type: String = "categorize",
    @Serializable(with = FlexibleStringSerializer::class) val value: String = ""
) {
    fun toDomain(): RuleAction = RuleAction(
        type = when (type.lowercase()) {
            "categorize", "set_category" -> RuleActionType.SET_CATEGORY
            "rename_merchant", "rename" -> RuleActionType.RENAME_MERCHANT
            "mark_transfer" -> RuleActionType.MARK_TRANSFER
            "mark_ignored" -> RuleActionType.MARK_IGNORED
            "set_mha_eligible" -> RuleActionType.SET_MHA_ELIGIBLE
            else -> RuleActionType.SET_CATEGORY
        },
        value = value
    )
}

@Serializable
data class RuleDto(
    val id: Long = 0L,
    val name: String,
    val conditions: List<RuleConditionDto> = emptyList(),
    val actions: List<RuleActionDto> = emptyList(),
    val priority: Int = 0,
    @Serializable(with = FlexibleBooleanSerializer::class) val enabled: Boolean = true
) {
    fun toDomain(): TransactionRule = TransactionRule(
        id = id,
        name = name,
        conditions = conditions.map { it.toDomain() },
        actions = actions.map { it.toDomain() },
        priority = priority,
        enabled = enabled
    )
}

@Serializable
data class UpcomingItemDto(
    val id: Long = 0L,
    val name: String,
    val amount: Double = 0.0,
    val direction: String = "expense",
    @SerialName("frequency_type") val frequencyType: String = "monthly",
    @SerialName("next_date") val nextDate: String,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("account_id") val accountId: Long? = null,
    val status: String = "active"
) {
    fun toDomain(): UpcomingItem = UpcomingItem(
        id = id,
        name = name,
        amountCents = MoneyUtils.dollarsToCents(amount),
        direction = direction,
        frequencyType = when (frequencyType.lowercase()) {
            "weekly" -> RecurrenceFrequency.WEEKLY
            "biweekly" -> RecurrenceFrequency.BIWEEKLY
            "semimonthly" -> RecurrenceFrequency.SEMIMONTHLY
            "yearly" -> RecurrenceFrequency.YEARLY
            else -> RecurrenceFrequency.MONTHLY
        },
        nextDate = nextDate,
        categoryId = categoryId,
        accountId = accountId,
        status = if (status.lowercase() == "paused") UpcomingStatus.PAUSED else UpcomingStatus.ACTIVE
    )
}

@Serializable
data class AuthMeResponseDto(
    val authenticated: Boolean = false,
    val mode: String? = null,
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val authProvider: String? = null,
    val user: UserDto? = null,
    val household: HouseholdDto? = null
)

@Serializable
data class UserDto(
    val id: Long = 0L,
    val email: String? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("is_local_admin") val isLocalAdmin: Int = 0
)

@Serializable
data class HouseholdDto(
    val id: Long = 1L,
    val name: String = "",
    val role: String = "member"
)

@Serializable
data class SimpleFinSyncResultDto(
    val success: Boolean = true,
    val status: String? = null,
    @SerialName("transactions_inserted") val transactionsInserted: Int = 0,
    @SerialName("accounts_unmatched") val accountsUnmatched: Int = 0
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String
)

@Serializable
data class AccountListResponseDto(
    val items: List<AccountDto> = emptyList()
)

@Serializable
data class AccountMutationResponseDto(
    val success: Boolean = true,
    val account: AccountDto? = null
)

@Serializable
data class TransactionMutationResponseDto(
    val success: Boolean = true,
    val transaction: TransactionDto? = null
)

@Serializable
data class CategoryMutationResponseDto(
    val success: Boolean = true,
    val category: CategoryDto? = null
)

@Serializable
data class IdMutationResponseDto(
    val success: Boolean = true,
    val id: Long = 0L
)

@Serializable
data class GoalsListResponseDto(
    val goals: List<GoalDto> = emptyList()
)

@Serializable
data class UpcomingListResponseDto(
    val items: List<UpcomingItemDto> = emptyList()
)

@Serializable
data class BudgetUpsertRequestDto(
    @SerialName("category_id") val categoryId: Long,
    val month: String = "",
    val amount: Double,
    val rollover: Boolean = false
)

@Serializable
data class ResetTransactionRequestDto(
    val fields: List<String> = listOf("merchant", "category_id", "is_transfer", "is_ignored", "mha_eligible")
)

@Serializable
data class MerchantLogoDto(
    @SerialName("merchant_key") val merchantKey: String = "",
    @SerialName("merchant_name") val merchantName: String = "",
    val provider: String = "logo_dev",
    @SerialName("provider_query") val providerQuery: String = "",
    val url: String? = null,
    val status: String = "candidate"
) {
    fun toDomain(): MerchantLogo? =
        if (merchantKey.isBlank()) null
        else MerchantLogo(merchantKey, merchantName, provider, providerQuery, url, status)
}

@Serializable
data class MerchantLogoCandidateDto(
    val name: String = "",
    val domain: String = "",
    @SerialName("logo_url") val logoUrl: String? = null
)

@Serializable
data class MerchantLogoReportRequestDto(
    @SerialName("merchant_key") val merchantKey: String,
    val status: String
)

@Serializable
data class MerchantLogoEnsureRequestDto(
    @SerialName("transaction_id") val transactionId: Long
)

@Serializable
data class MerchantLogoOverrideRequestDto(
    @SerialName("merchant_key") val merchantKey: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("use_category_icon") val useCategoryIcon: Boolean = false
)

@Serializable
data class MerchantLogoSearchRequestDto(
    @SerialName("transaction_id") val transactionId: Long,
    val query: String
)

@Serializable
data class MerchantLogoMutationResponseDto(
    val success: Boolean = true,
    @SerialName("merchant_logo") val merchantLogo: MerchantLogoDto? = null
)

@Serializable
data class MerchantLogoSearchResponseDto(
    val success: Boolean = true,
    val configured: Boolean = true,
    @SerialName("merchant_logo") val merchantLogo: MerchantLogoDto? = null,
    val candidates: List<MerchantLogoCandidateDto> = emptyList()
)
