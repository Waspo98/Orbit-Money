package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.domain.MoneyUtils
import kotlin.math.abs
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Deserializes SQLite integer booleans (0/1), strings ("0"/"1"/"true"/"false"), and JSON booleans (true/false).
 */
object FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return false
        val primitive = element as? JsonPrimitive ?: return false
        primitive.booleanOrNull?.let { return it }
        primitive.intOrNull?.let { return it != 0 }
        return when (primitive.content.trim().lowercase()) {
            "true", "1", "yes" -> true
            else -> false
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}

object FlexibleNullableBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleNullableBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        primitive.booleanOrNull?.let { return it }
        primitive.intOrNull?.let { return it != 0 }
        return when (primitive.content.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) {
            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            encoder.encodeNull()
        } else {
            encoder.encodeBoolean(value)
        }
    }
}

/**
 * Deserializes string, number, boolean, null, or array values (e.g. rule condition "between" [min, max]) into String.
 */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> ""
            is JsonPrimitive -> element.content
            is JsonArray -> element.joinToString(",") { (it as? JsonPrimitive)?.content ?: it.toString() }
            else -> element.toString()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

object ServerUrlNormalizer {
    fun normalize(rawUrl: String): String {
        var url = rawUrl.trim().trimEnd('/')
        if (url.endsWith("/api", ignoreCase = true)) {
            url = url.dropLast(4).trimEnd('/')
        }
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            val lower = url.lowercase()
            val isLocal = lower.startsWith("localhost") ||
                lower.startsWith("127.0.0.1") ||
                lower.startsWith("10.") ||
                lower.startsWith("192.168.") ||
                Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\.").containsMatchIn(lower)
            url = if (isLocal) "http://$url" else "https://$url"
        }
        return url
    }
}

@Serializable
data class AuthConfigResponseDto(
    val authProvider: String = "local",
    @Serializable(with = FlexibleBooleanSerializer::class) val localEnabled: Boolean = true,
    @Serializable(with = FlexibleBooleanSerializer::class) val oidcEnabled: Boolean = false,
    val oidcLoginUrl: String? = null,
    val oidcLoginLabel: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class) val sampleDataEnabled: Boolean = false
)

@Serializable
data class OidcNativeExchangeRequestDto(
    val code: String
)

@Serializable
data class SampleLoginRequestDto(
    val deviceId: String
)

@Serializable
data class HealthResponseDto(
    val status: String = ""
)

@Serializable
data class RuleListResponseDto(
    val items: List<RuleDto> = emptyList(),
    val total: Int = 0
)

@Serializable
data class HouseholdMemberDto(
    val id: Long = 0L,
    @SerialName("household_id") val householdId: Long = 1L,
    val name: String = "",
    val role: String = "adult",
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("employment_status") val employmentStatus: String = "employed",
    val employer: String? = null,
    @SerialName("job_title") val jobTitle: String? = null,
    @SerialName("gross_income_annual") val grossIncomeAnnual: Double = 0.0,
    @SerialName("net_pay_per_period") val netPayPerPeriod: Double = 0.0,
    @SerialName("pay_frequency") val payFrequency: String = "biweekly",
    @SerialName("pay_periods_per_year") val payPeriodsPerYear: Double = 26.0,
    @SerialName("retirement_account_type") val retirementAccountType: String? = null,
    @SerialName("employee_contribution_percent") val employeeContributionPercent: Double? = 0.0,
    @SerialName("employee_contribution_annual") val employeeContributionAnnual: Double = 0.0,
    @SerialName("employer_match_percent") val employerMatchPercent: Double = 0.0,
    @SerialName("employer_match_limit_percent") val employerMatchLimitPercent: Double = 0.0,
    @SerialName("employer_match_annual_cap") val employerMatchAnnualCap: Double = 0.0,
    @SerialName("health_premium_per_month") val healthPremiumPerMonth: Double = 0.0,
    @SerialName("hsa_contribution_annual") val hsaContributionAnnual: Double = 0.0,
    @SerialName("dependent_care_fsa_annual") val dependentCareFsaAnnual: Double = 0.0,
    @SerialName("other_benefits_annual") val otherBenefitsAnnual: Double = 0.0,
    val notes: String? = null
) {
    fun toDomain(): HouseholdMember = HouseholdMember(
        id = id,
        householdId = householdId,
        name = name,
        role = role,
        birthDate = birthDate,
        employmentStatus = employmentStatus,
        employer = employer,
        jobTitle = jobTitle,
        grossIncomeAnnualCents = MoneyUtils.dollarsToCents(grossIncomeAnnual),
        netPayPerPeriodCents = MoneyUtils.dollarsToCents(netPayPerPeriod),
        payFrequency = payFrequency,
        payPeriodsPerYear = payPeriodsPerYear,
        retirementAccountType = retirementAccountType,
        employeeContributionPercent = employeeContributionPercent,
        employeeContributionAnnualCents = MoneyUtils.dollarsToCents(employeeContributionAnnual),
        employerMatchPercent = employerMatchPercent,
        employerMatchLimitPercent = employerMatchLimitPercent,
        employerMatchAnnualCapCents = MoneyUtils.dollarsToCents(employerMatchAnnualCap),
        healthPremiumPerMonthCents = MoneyUtils.dollarsToCents(healthPremiumPerMonth),
        hsaContributionAnnualCents = MoneyUtils.dollarsToCents(hsaContributionAnnual),
        dependentCareFsaAnnualCents = MoneyUtils.dollarsToCents(dependentCareFsaAnnual),
        otherBenefitsAnnualCents = MoneyUtils.dollarsToCents(otherBenefitsAnnual),
        notes = notes
    )
}

@Serializable
data class HouseholdResponseDto(
    val members: List<HouseholdMemberDto> = emptyList()
)

@Serializable
data class MhaTaxProfileDto(
    val mode: String = "simple",
    val simpleFederalRate: Double = 22.0,
    val simpleStateRate: Double = 4.95,
    val filingStatus: String = "married_joint",
    val state: String = "IL",
    val deductionMode: String = "standard",
    val itemizedDeduction: Double = 0.0,
    val taxableIncomeOverride: Double? = null,
    val stateRateOverride: Double? = null
) {
    fun toDomain(): MhaTaxProfile = MhaTaxProfile(
        mode = mode,
        simpleFederalRatePercent = simpleFederalRate,
        simpleStateRatePercent = simpleStateRate,
        filingStatus = FilingStatus.fromStorage(filingStatus),
        state = state,
        deductionMode = deductionMode,
        itemizedDeductionCents = MoneyUtils.dollarsToCents(itemizedDeduction),
        taxableIncomeOverrideCents = taxableIncomeOverride?.let { MoneyUtils.dollarsToCents(it) },
        stateRateOverridePercent = stateRateOverride
    )
}

@Serializable
data class MhaOverviewResponseDto(
    @Serializable(with = FlexibleBooleanSerializer::class) val enabled: Boolean = false,
    val taxProfile: MhaTaxProfileDto? = null
)

object RuleWireMapper {
    fun conditionToDomain(dto: RuleConditionDto): RuleCondition {
        val resolvedField = when (dto.field.lowercase()) {
            "merchant" -> RuleConditionField.MERCHANT
            "amount" -> RuleConditionField.AMOUNT
            "account", "account_id" -> RuleConditionField.ACCOUNT_ID
            "notes" -> RuleConditionField.NOTES
            "original_description" -> RuleConditionField.ORIGINAL_DESCRIPTION
            else -> RuleConditionField.CATEGORY_ID
        }
        val resolvedOp = when (dto.operator.lowercase()) {
            "exact", "equals" -> RuleOperator.EQUALS
            "starts_with" -> RuleOperator.STARTS_WITH
            "ends_with" -> RuleOperator.ENDS_WITH
            "greater_than" -> RuleOperator.GREATER_THAN
            "less_than" -> RuleOperator.LESS_THAN
            "regex" -> RuleOperator.REGEX
            "between" -> RuleOperator.BETWEEN
            "is" -> RuleOperator.IS
            "is_not" -> RuleOperator.IS_NOT
            else -> RuleOperator.CONTAINS
        }
        val strValue = jsonElementToRuleString(dto.value)
        val isIdField = resolvedField == RuleConditionField.ACCOUNT_ID || resolvedField == RuleConditionField.CATEGORY_ID
        val rangeParts = if (resolvedField == RuleConditionField.AMOUNT && resolvedOp == RuleOperator.BETWEEN && dto.value is JsonArray && dto.value.size == 2) {
            val a = abs(MoneyUtils.dollarsToCents((dto.value[0] as? JsonPrimitive)?.content))
            val b = abs(MoneyUtils.dollarsToCents((dto.value[1] as? JsonPrimitive)?.content))
            minOf(a, b) to maxOf(a, b)
        } else null
        return RuleCondition(
            field = resolvedField,
            operator = resolvedOp,
            value = strValue,
            amountCentsValue = if (resolvedField == RuleConditionField.AMOUNT && resolvedOp != RuleOperator.BETWEEN && strValue.isNotBlank()) abs(MoneyUtils.dollarsToCents(strValue)) else null,
            minAmountCentsValue = rangeParts?.first,
            maxAmountCentsValue = rangeParts?.second,
            targetId = if (isIdField) strValue.toLongOrNull() else null
        )
    }

    fun actionToDomain(dto: RuleActionDto): RuleAction {
        val resolvedType = when (dto.type.lowercase()) {
            "categorize", "set_category" -> RuleActionType.CATEGORIZE
            "rename_merchant", "rename" -> RuleActionType.RENAME
            "mark_transfer" -> RuleActionType.MARK_TRANSFER
            "mark_ignored" -> RuleActionType.MARK_IGNORED
            "set_mha_eligible" -> RuleActionType.SET_MHA_ELIGIBLE
            else -> RuleActionType.CATEGORIZE
        }
        val strValue = jsonElementToRuleString(dto.value)
        val isBoolFlag = resolvedType in setOf(RuleActionType.MARK_TRANSFER, RuleActionType.MARK_IGNORED, RuleActionType.SET_MHA_ELIGIBLE)
        return RuleAction(
            type = resolvedType,
            value = strValue,
            textValue = if (resolvedType == RuleActionType.RENAME) strValue else null,
            targetCategoryId = if (resolvedType == RuleActionType.CATEGORIZE) strValue.toLongOrNull() else null,
            booleanValue = if (isBoolFlag) (strValue.isEmpty() || strValue.lowercase() in setOf("1", "true")) else null
        )
    }

    fun toDto(rule: TransactionRule): RuleDto = RuleDto(
        id = rule.id,
        name = rule.name,
        conditions = rule.conditions.map(::conditionToDto),
        actions = rule.actions.map(::actionToDto),
        priority = rule.priority,
        enabled = rule.enabled
    )

    private fun conditionToDto(cond: RuleCondition): RuleConditionDto {
        val wireField = when (cond.field) {
            RuleConditionField.MERCHANT -> "merchant"
            RuleConditionField.ORIGINAL_DESCRIPTION -> "original_description"
            RuleConditionField.AMOUNT -> "amount"
            RuleConditionField.ACCOUNT, RuleConditionField.ACCOUNT_ID -> "account_id"
            RuleConditionField.CATEGORY, RuleConditionField.CATEGORY_ID -> "category_id"
            RuleConditionField.NOTES -> "notes"
        }
        val wireOperator = when (cond.operator) {
            RuleOperator.EXACT, RuleOperator.EQUALS -> "equals"
            RuleOperator.CONTAINS -> "contains"
            RuleOperator.STARTS_WITH -> "starts_with"
            RuleOperator.ENDS_WITH -> "ends_with"
            RuleOperator.GREATER_THAN -> "greater_than"
            RuleOperator.LESS_THAN -> "less_than"
            RuleOperator.BETWEEN -> "between"
            RuleOperator.IS -> "is"
            RuleOperator.IS_NOT -> "is_not"
            RuleOperator.REGEX -> "regex"
        }
        val wireValue: JsonElement = when {
            cond.field == RuleConditionField.AMOUNT && cond.operator == RuleOperator.BETWEEN -> {
                val parts = cond.value.split(",").map { it.trim() }
                val minCents = cond.minAmountCentsValue ?: parts.getOrNull(0)?.let { abs(MoneyUtils.dollarsToCents(it)) } ?: 0L
                val maxCents = cond.maxAmountCentsValue ?: parts.getOrNull(1)?.let { abs(MoneyUtils.dollarsToCents(it)) } ?: minCents
                val lo = minOf(minCents, maxCents)
                val hi = maxOf(minCents, maxCents)
                JsonArray(listOf(JsonPrimitive(MoneyUtils.centsToDollars(lo)), JsonPrimitive(MoneyUtils.centsToDollars(hi))))
            }
            cond.field == RuleConditionField.AMOUNT -> {
                val cents = cond.amountCentsValue ?: abs(MoneyUtils.dollarsToCents(cond.value))
                JsonPrimitive(MoneyUtils.centsToDollars(cents))
            }
            cond.field in setOf(RuleConditionField.ACCOUNT, RuleConditionField.ACCOUNT_ID, RuleConditionField.CATEGORY, RuleConditionField.CATEGORY_ID) -> {
                val id = cond.targetId ?: cond.value.toLongOrNull()
                if (id != null) JsonPrimitive(id) else JsonPrimitive(cond.value)
            }
            else -> JsonPrimitive(cond.value)
        }
        return RuleConditionDto(field = wireField, operator = wireOperator, value = wireValue)
    }

    private fun actionToDto(act: RuleAction): RuleActionDto {
        val wireType = when (act.type) {
            RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> "categorize"
            RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> "rename"
            RuleActionType.MARK_TRANSFER -> "mark_transfer"
            RuleActionType.MARK_IGNORED -> "mark_ignored"
            RuleActionType.SET_MHA_ELIGIBLE -> "set_mha_eligible"
        }
        val wireValue: JsonElement = when (act.type) {
            RuleActionType.CATEGORIZE, RuleActionType.SET_CATEGORY -> {
                val catId = act.targetCategoryId ?: act.value.toLongOrNull()
                if (catId != null) JsonPrimitive(catId) else JsonPrimitive(act.value)
            }
            RuleActionType.RENAME, RuleActionType.RENAME_MERCHANT -> {
                JsonPrimitive(act.textValue ?: act.value)
            }
            RuleActionType.MARK_TRANSFER, RuleActionType.MARK_IGNORED -> {
                JsonPrimitive(act.booleanValue ?: true)
            }
            RuleActionType.SET_MHA_ELIGIBLE -> {
                val boolVal = act.booleanValue ?: (act.value.isEmpty() || act.value.lowercase() in setOf("1", "true"))
                JsonPrimitive(boolVal)
            }
        }
        return RuleActionDto(type = wireType, value = wireValue)
    }

    private fun jsonElementToRuleString(element: JsonElement): String = when (element) {
        is JsonNull -> ""
        is JsonPrimitive -> element.content
        is JsonArray -> element.joinToString(",") { (it as? JsonPrimitive)?.content ?: it.toString() }
        else -> element.toString()
    }
}
