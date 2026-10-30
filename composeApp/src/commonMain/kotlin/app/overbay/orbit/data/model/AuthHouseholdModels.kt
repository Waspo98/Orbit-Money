package app.overbay.orbit.data.model

import app.overbay.orbit.domain.Cents
import kotlinx.serialization.Serializable

@Serializable
enum class StorageMode {
    LOCAL,
    SELF_HOSTED
}

@Serializable
enum class HouseholdRole {
    OWNER,
    ADMIN,
    MEMBER
}

@Serializable
data class UserProfile(
    val id: Long = 0L,
    val oidcSub: String? = null,
    val username: String? = null,
    val email: String? = null,
    val displayName: String = "",
    val isLocalAdmin: Boolean = false
)

@Serializable
data class HouseholdInfo(
    val id: Long = 1L,
    val name: String = "My Household",
    val defaultCurrency: String = "USD",
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class HouseholdMembership(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val userId: Long,
    val role: HouseholdRole = HouseholdRole.OWNER,
    val createdAt: String = ""
)

@Serializable
data class HouseholdShare(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val invitedEmail: String,
    val role: HouseholdRole = HouseholdRole.MEMBER,
    val accessLevel: String = "write",
    val acceptedAt: String? = null,
    val revokedAt: String? = null,
    val createdAt: String = ""
)

@Serializable
data class HouseholdMember(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val name: String,
    val role: String = "adult",
    val birthDate: String? = null, // YYYY-MM-DD
    val employmentStatus: String = "employed",
    val employer: String? = null,
    val jobTitle: String? = null,
    val grossIncomeAnnualCents: Cents = 0L,
    val netPayPerPeriodCents: Cents = 0L,
    val payFrequency: String = "biweekly",
    val payPeriodsPerYear: Double = 26.0,
    val retirementAccountType: String? = null,
    val employeeContributionPercent: Double? = 0.0,
    val employeeContributionAnnualCents: Cents = 0L,
    val employerMatchPercent: Double = 0.0,
    val employerMatchLimitPercent: Double = 0.0,
    val employerMatchAnnualCapCents: Cents = 0L,
    val healthPremiumPerMonthCents: Cents = 0L,
    val hsaContributionAnnualCents: Cents = 0L,
    val dependentCareFsaAnnualCents: Cents = 0L,
    val otherBenefitsAnnualCents: Cents = 0L,
    val notes: String? = null,
    val retirementAccounts: List<HouseholdRetirementAccount> = emptyList(),
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class HouseholdIncomeRecord(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val memberId: Long,
    val effectiveDate: String, // YYYY-MM-DD
    val grossIncomeAnnualCents: Cents = 0L,
    val netPayPerPeriodCents: Cents = 0L,
    val payFrequency: String = "biweekly",
    val payPeriodsPerYear: Double = 26.0,
    val employeeContributionPercent: Double = 0.0,
    val employeeContributionAnnualCents: Cents = 0L,
    val employerMatchPercent: Double = 0.0,
    val employerMatchLimitPercent: Double = 0.0,
    val employerMatchAnnualCapCents: Cents = 0L,
    val healthPremiumPerMonthCents: Cents = 0L,
    val hsaContributionAnnualCents: Cents = 0L,
    val dependentCareFsaAnnualCents: Cents = 0L,
    val otherBenefitsAnnualCents: Cents = 0L,
    val source: String = "manual",
    val notes: String? = null
)

@Serializable
data class HouseholdRetirementAccount(
    val id: Long = 0L,
    val householdId: Long = 1L,
    val memberId: Long,
    val accountId: Long,
    val accountKind: String = "other",
    val createdAt: String = "",
    val updatedAt: String = ""
)
