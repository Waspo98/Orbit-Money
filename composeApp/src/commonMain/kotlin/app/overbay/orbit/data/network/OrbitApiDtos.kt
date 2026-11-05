package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.AmountStrategy
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.CreditCardProfile
import app.overbay.orbit.data.model.EditSource
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.MerchantLogo
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RecurrenceRule
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingSource
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.domain.MoneyUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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
    @SerialName("credit_card_profile") val creditCardProfile: CreditCardProfileDto? = null,
    @SerialName("transaction_count") val transactionCount: Int = 0
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
        creditCardProfile = creditCardProfile?.toDomain(),
        transactionCount = transactionCount
    )
}

@Serializable
data class CreditCardProfileDto(
    val id: Long? = null,
    @SerialName("account_id") val accountId: Long = 0L,
    @SerialName("card_name") val cardName: String? = null,
    @SerialName("issuer_name") val issuerName: String? = null,
    val network: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("annual_fee") val annualFee: Double? = null,
    @SerialName("annual_fee_post_date") val annualFeePostDate: String? = null,
    @SerialName("credit_limit") val creditLimit: Double? = null,
    @SerialName("statement_closing_day") val statementClosingDay: Int? = null,
    @SerialName("payment_due_day") val paymentDueDay: Int? = null,
    @SerialName("authorized_users") val authorizedUsers: List<String> = emptyList(),
    @SerialName("reward_categories") val rewardCategories: List<RewardCategoryWireDto> = emptyList(),
    val benefits: List<CardBenefitWireDto> = emptyList(),
    val notes: String? = null
) {
    fun toDomain(): CreditCardProfile = CreditCardProfile(
        id = id ?: 0L,
        accountId = accountId,
        cardName = cardName,
        issuerName = issuerName,
        network = network,
        imageUrl = imageUrl,
        annualFeeCents = annualFee?.let { MoneyUtils.dollarsToCents(it) },
        annualFeePostDate = annualFeePostDate,
        creditLimitCents = creditLimit?.let { MoneyUtils.dollarsToCents(it) },
        statementClosingDay = statementClosingDay,
        paymentDueDay = paymentDueDay,
        authorizedUsers = authorizedUsers,
        rewardCategories = rewardCategories.map { it.toDomain() },
        benefits = benefits.map { it.toDomain() },
        notes = notes
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
        val sources = listOfNotNull(
            editedMerchantSource,
            editedCategoryIdSource,
            editedIsTransferSource,
            editedIsIgnoredSource,
            editedMhaEligibleSource
        )
        val editSrc = when {
            sources.any { it.equals("user", ignoreCase = true) } -> EditSource.USER
            sources.any { it.startsWith("rule", ignoreCase = true) } -> EditSource.RULE
            sources.any { it.startsWith("system", ignoreCase = true) } -> EditSource.SYSTEM
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
    @SerialName("mha_default_ignored") @Serializable(with = FlexibleBooleanSerializer::class) val mhaDefaultIgnored: Boolean = false,
    @SerialName("transaction_count") val transactionCount: Int = 0,
    @SerialName("budget_count") val budgetCount: Int = 0,
    @SerialName("rule_count") val ruleCount: Int = 0
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
        mhaDefaultIgnored = mhaDefaultIgnored,
        transactionCount = transactionCount,
        budgetCount = budgetCount,
        ruleCount = ruleCount
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
    @SerialName("days_remaining") val dailyRemaining: Int = 0
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
    @SerialName("kind") val kind: String = "custom",
    @SerialName("icon") val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val notes: String? = null,
    @SerialName("monthly_pace") val monthlyPace: Double = 0.0,
    val eta: GoalEtaWireDto? = null,
    val history: List<GoalHistoryPointWireDto> = emptyList(),
    val allocations: List<GoalAllocationWireDto> = emptyList()
) {
    fun toDomain(): Goal = Goal(
        id = id,
        name = name,
        targetAmountCents = MoneyUtils.dollarsToCents(targetAmount),
        currentAmountCents = MoneyUtils.dollarsToCents(currentAmount),
        targetDate = targetDate,
        linkedAccountId = linkedAccountId,
        kind = kind,
        icon = icon,
        sortOrder = sortOrder,
        allocations = allocations.map { it.toDomain() },
        notes = notes,
        monthlyPaceCents = MoneyUtils.dollarsToCents(monthlyPace),
        eta = eta?.toDomain(),
        history = history.map { it.toDomain() }
    )
}

@Serializable
data class RuleConditionDto(
    val field: String = "merchant",
    val operator: String = "contains",
    val value: JsonElement = JsonPrimitive("")
) {
    constructor(field: String = "merchant", operator: String = "contains", value: String) : this(field, operator, JsonPrimitive(value))
    fun toDomain(): RuleCondition = RuleWireMapper.conditionToDomain(this)
}

@Serializable
data class RuleActionDto(
    val type: String = "categorize",
    val value: JsonElement = JsonPrimitive("")
) {
    constructor(type: String = "categorize", value: String) : this(type, JsonPrimitive(value))
    fun toDomain(): RuleAction = RuleWireMapper.actionToDomain(this)
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
    val name: String = "",
    val merchant: String? = null,
    val kind: String = "bill",
    val amount: Double = 0.0,
    val direction: String = "expense",
    @SerialName("frequency_type") val frequencyType: String = "monthly",
    @SerialName("frequency_interval") val frequencyInterval: Int = 1,
    @SerialName("frequency_unit") val frequencyUnit: String = "months",
    @SerialName("recurrence_rule") val recurrenceRule: JsonElement? = null,
    @SerialName("amount_strategy") val amountStrategy: String = "fixed",
    @SerialName("amount_lookback_months") val amountLookbackMonths: Int = 6,
    @SerialName("next_date") val nextDate: String = "",
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("account_id") val accountId: Long? = null,
    val source: String = "manual",
    @SerialName("source_transaction_id") val sourceTransactionId: Long? = null,
    val status: String = "active",
    val notes: String? = null
) {
    fun toDomain(): UpcomingItem {
        val freq = when (frequencyType.lowercase()) {
            "weekly" -> RecurrenceFrequency.WEEKLY
            "biweekly" -> RecurrenceFrequency.BIWEEKLY
            "semimonthly" -> RecurrenceFrequency.SEMIMONTHLY
            "bimonthly" -> RecurrenceFrequency.BIMONTHLY
            "quarterly" -> RecurrenceFrequency.QUARTERLY
            "yearly" -> RecurrenceFrequency.YEARLY
            "custom" -> RecurrenceFrequency.CUSTOM
            else -> RecurrenceFrequency.MONTHLY
        }
        val k = when (kind.lowercase()) {
            "subscription" -> UpcomingKind.SUBSCRIPTION
            "giving" -> UpcomingKind.GIVING
            "income" -> UpcomingKind.INCOME
            else -> UpcomingKind.BILL
        }
        val src = when (source.lowercase()) {
            "transaction" -> UpcomingSource.TRANSACTION
            "suggestion" -> UpcomingSource.SUGGESTION
            else -> UpcomingSource.MANUAL
        }
        val strat = when (amountStrategy.lowercase()) {
            "history_average" -> AmountStrategy.HISTORY_AVERAGE
            else -> AmountStrategy.FIXED
        }
        val st = if (status.equals("paused", ignoreCase = true)) UpcomingStatus.PAUSED else UpcomingStatus.ACTIVE
        val rule = parseRecurrenceRuleJson(recurrenceRule)

        return UpcomingItem(
            id = id,
            name = name.ifBlank { merchant ?: "" },
            kind = k,
            source = src,
            merchant = merchant,
            amountCents = MoneyUtils.dollarsToCents(amount),
            direction = direction,
            frequencyType = freq,
            frequencyInterval = frequencyInterval,
            frequencyUnit = frequencyUnit,
            nextDate = nextDate,
            categoryId = categoryId,
            accountId = accountId,
            sourceTransactionId = sourceTransactionId,
            status = st,
            notes = notes,
            recurrenceRule = rule,
            amountStrategy = strat,
            amountLookbackMonths = amountLookbackMonths
        )
    }
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

