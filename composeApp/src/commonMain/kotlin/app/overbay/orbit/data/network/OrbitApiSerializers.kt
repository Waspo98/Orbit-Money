package app.overbay.orbit.data.network

import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.domain.MoneyUtils
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
