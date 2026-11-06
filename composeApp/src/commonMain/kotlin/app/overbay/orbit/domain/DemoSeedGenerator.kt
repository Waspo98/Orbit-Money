package app.overbay.orbit.domain

import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.CategoryBudget
import app.overbay.orbit.data.model.FilingStatus
import app.overbay.orbit.data.model.Goal
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.MhaTaxProfile
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.RecurrenceFrequency
import app.overbay.orbit.data.model.RuleAction
import app.overbay.orbit.data.model.RuleActionType
import app.overbay.orbit.data.model.RuleCondition
import app.overbay.orbit.data.model.RuleConditionField
import app.overbay.orbit.data.model.RuleOperator
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.TransactionRule
import app.overbay.orbit.data.model.UpcomingDirection
import app.overbay.orbit.data.model.UpcomingItem
import app.overbay.orbit.data.model.UpcomingKind

object DemoSeedGenerator {

    fun generateDemoSnapshot(todayDate: String = LocalDateUtils.todayIsoDate()): OrbitWorkspaceSnapshot {
        val currentYm = todayDate.take(7).takeIf { it.length == 7 && it.contains("-") } ?: "2026-04"
        val nextYm = LocalDateUtils.addMonthsToYearMonth(currentYm, 1)
        val prevYm = LocalDateUtils.addMonthsToYearMonth(currentYm, -1)
        val accounts = listOf(
            Account(id = 1L, name = "Demo Everyday Checking", type = AccountType.CHECKING, institution = "Orbit Demo Bank", accountNumberLast4 = "1024", currentBalanceCents = 4286_52L, estimatedValueCents = null, sortOrder = 0),
            Account(id = 2L, name = "Demo Rainy Day Savings", type = AccountType.SAVINGS, institution = "Orbit Demo Bank", accountNumberLast4 = "2048", currentBalanceCents = 12840_11L, estimatedValueCents = null, sortOrder = 10),
            Account(id = 3L, name = "Demo Cash Wallet", type = AccountType.CASH, institution = "Orbit Demo Bank", accountNumberLast4 = null, currentBalanceCents = 240_00L, estimatedValueCents = null, sortOrder = 20),
            Account(id = 4L, name = "Demo Rewards Card", type = AccountType.CREDIT, institution = "Northstar Card", accountNumberLast4 = "4096", currentBalanceCents = -842_37L, estimatedValueCents = null, sortOrder = 30),
            Account(id = 5L, name = "Demo Travel Card", type = AccountType.CREDIT, institution = "Summit Visa", accountNumberLast4 = "7788", currentBalanceCents = -319_84L, estimatedValueCents = null, sortOrder = 40),
            Account(id = 6L, name = "Demo Brokerage", type = AccountType.INVESTMENT, institution = "Northstar Investments", accountNumberLast4 = "6677", currentBalanceCents = 28450_72L, estimatedValueCents = null, sortOrder = 50),
            Account(id = 7L, name = "Demo Auto Loan", type = AccountType.LOAN, institution = "Orbit Demo Bank", accountNumberLast4 = "5150", currentBalanceCents = -14620_35L, estimatedValueCents = null, sortOrder = 60),
            Account(id = 8L, name = "Demo Home Mortgage", type = AccountType.MORTGAGE, institution = "Orbit Demo Bank", accountNumberLast4 = "3030", currentBalanceCents = -286400_00L, estimatedValueCents = 365000_00L, sortOrder = 70),
            Account(id = 9L, name = "Demo Other Asset", type = AccountType.OTHER, institution = "Orbit Demo Bank", accountNumberLast4 = "9090", currentBalanceCents = 1250_00L, estimatedValueCents = null, sortOrder = 80)
        )

        val categories = listOf(
            Category(id = 1L, name = "Income", color = "#10B981", icon = "💰", isIncome = true, isTransfer = false),
            Category(id = 2L, name = "Groceries", color = "#3B82F6", icon = "🛒", isIncome = false, isTransfer = false),
            Category(id = 3L, name = "Dining & Drinks", color = "#F59E0B", icon = "🍽️", isIncome = false, isTransfer = false),
            Category(id = 4L, name = "Housing & Mortgage", color = "#6366F1", icon = "🏠", isIncome = false, isTransfer = false, mhaDefaultEligible = true),
            Category(id = 5L, name = "Bills & Utilities", color = "#06B6D4", icon = "💡", isIncome = false, isTransfer = false, mhaDefaultEligible = true),
            Category(id = 6L, name = "Transportation", color = "#EC4899", icon = "🚗", isIncome = false, isTransfer = false),
            Category(id = 7L, name = "Healthcare", color = "#EF4444", icon = "🏥", isIncome = false, isTransfer = false),
            Category(id = 8L, name = "Giving & Tithe", color = "#8B5CF6", icon = "💝", isIncome = false, isTransfer = false),
            Category(id = 9L, name = "Entertainment", color = "#14B8A6", icon = "🎬", isIncome = false, isTransfer = false),
            Category(id = 10L, name = "Credit Card Payment", color = "#64748B", icon = "💳", isIncome = false, isTransfer = true),
            Category(id = 11L, name = "Internal Transfer", color = "#64748B", icon = "🔄", isIncome = false, isTransfer = true)
        )

        val budgets = listOf(
            CategoryBudget(id = 1L, categoryId = 2L, amountCents = 650_00L), // Groceries
            CategoryBudget(id = 2L, categoryId = 3L, amountCents = 250_00L), // Dining
            CategoryBudget(id = 3L, categoryId = 4L, amountCents = 1850_00L), // Housing
            CategoryBudget(id = 4L, categoryId = 5L, amountCents = 320_00L), // Utilities
            CategoryBudget(id = 5L, categoryId = 6L, amountCents = 300_00L), // Transport
            CategoryBudget(id = 6L, categoryId = 8L, amountCents = 800_00L)  // Tithe
        )

        val householdMembers = listOf(
            HouseholdMember(
                id = 1L,
                name = "Jordan Brooks",
                role = "adult",
                grossIncomeAnnualCents = 72_000_00L,
                netPayPerPeriodCents = 2_125_00L,
                employeeContributionPercent = 8.0,
                healthPremiumPerMonthCents = 380_00L,
                hsaContributionAnnualCents = 2400_00L
            ),
            HouseholdMember(
                id = 2L,
                name = "Taylor Brooks",
                role = "adult",
                grossIncomeAnnualCents = 110_000_00L,
                netPayPerPeriodCents = 2_950_00L,
                employeeContributionPercent = 6.0,
                healthPremiumPerMonthCents = 0L,
                hsaContributionAnnualCents = 1200_00L
            )
        )

        val goals = listOf(
            Goal(
                id = 1L,
                name = "Emergency Fund",
                icon = "🛡️",
                targetAmountCents = 20_000_00L,
                currentAmountCents = 12_840_11L,
                sortOrder = 0
            ),
            Goal(
                id = 2L,
                name = "Summer Vacation",
                icon = "✈️",
                targetAmountCents = 5_000_00L,
                currentAmountCents = 1_850_00L,
                targetDate = "$nextYm-15",
                sortOrder = 10
            )
        )

        val mhaTaxProfile = MhaTaxProfile(
            mode = "household",
            simpleFederalRatePercent = 22.0,
            simpleStateRatePercent = 4.95,
            filingStatus = FilingStatus.MARRIED_JOINT,
            state = "IL",
            deductionMode = "standard"
        )

        val rules = listOf(
            TransactionRule(
                id = 1L,
                name = "Auto-Categorize Paycheck",
                conditions = listOf(
                    RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, "payroll")
                ),
                actions = listOf(
                    RuleAction(RuleActionType.CATEGORIZE, targetCategoryId = 1L)
                ),
                priority = 10,
                enabled = true
            ),
            TransactionRule(
                id = 2L,
                name = "Clean ComEd Electric",
                conditions = listOf(
                    RuleCondition(RuleConditionField.MERCHANT, RuleOperator.CONTAINS, "comed")
                ),
                actions = listOf(
                    RuleAction(RuleActionType.RENAME, textValue = "Commonwealth Edison"),
                    RuleAction(RuleActionType.CATEGORIZE, targetCategoryId = 5L),
                    RuleAction(RuleActionType.SET_MHA_ELIGIBLE, booleanValue = true)
                ),
                priority = 5,
                enabled = true
            )
        )

        val upcomingItems = listOf(
            UpcomingItem(
                id = 1L,
                name = "Home Mortgage",
                merchant = "Orbit Mortgage Bank",
                amountCents = 1845_60L,
                direction = UpcomingDirection.EXPENSE,
                kind = UpcomingKind.BILL,
                frequency = RecurrenceFrequency.MONTHLY,
                nextDate = "$nextYm-01",
                categoryId = 4L,
                accountId = 1L
            ),
            UpcomingItem(
                id = 2L,
                name = "Electric Utility",
                merchant = "Commonwealth Edison",
                amountCents = 142_30L,
                direction = UpcomingDirection.EXPENSE,
                kind = UpcomingKind.BILL,
                frequency = RecurrenceFrequency.MONTHLY,
                nextDate = "$nextYm-08",
                categoryId = 5L,
                accountId = 1L
            ),
            UpcomingItem(
                id = 3L,
                name = "Grace Harbor Payroll",
                merchant = "Grace Harbor Payroll",
                amountCents = 2125_00L,
                direction = UpcomingDirection.INCOME,
                kind = UpcomingKind.INCOME,
                frequency = RecurrenceFrequency.BIWEEKLY,
                nextDate = "$currentYm-24",
                categoryId = 1L,
                accountId = 1L
            )
        )

        // Seed representative transactions with transfers and provenance
        val transactions = listOf(
            Transaction(
                id = 101L,
                accountId = 1L,
                originalDate = "$currentYm-10",
                originalMerchant = "Grace Harbor Payroll",
                originalAmountCents = 2125_00L,
                originalCategoryId = 1L,
                source = "seed"
            ),
            Transaction(
                id = 102L,
                accountId = 1L,
                originalDate = "$currentYm-01",
                originalMerchant = "Orbit Mortgage Bank",
                originalAmountCents = -1845_60L,
                originalCategoryId = 4L,
                source = "seed"
            ),
            Transaction(
                id = 103L,
                accountId = 4L,
                originalDate = "$currentYm-05",
                originalMerchant = "Trader Joe's",
                originalAmountCents = -86_40L,
                originalCategoryId = 2L,
                source = "seed"
            ),
            Transaction(
                id = 104L,
                accountId = 4L,
                originalDate = "$currentYm-07",
                originalMerchant = "Whole Foods Market",
                originalAmountCents = -112_50L,
                originalCategoryId = 2L,
                source = "seed"
            ),
            Transaction(
                id = 105L,
                accountId = 1L,
                originalDate = "$currentYm-09",
                originalMerchant = "Northstar Card Payment",
                originalAmountCents = -500_00L,
                originalCategoryId = 10L,
                isTransfer = true,
                transferPairId = 106L,
                source = "seed"
            ),
            Transaction(
                id = 106L,
                accountId = 4L,
                originalDate = "$currentYm-09",
                originalMerchant = "Payment Received - Thank You",
                originalAmountCents = 500_00L,
                originalCategoryId = 10L,
                isTransfer = true,
                transferPairId = 105L,
                source = "seed"
            ),
            Transaction(
                id = 107L,
                accountId = 1L,
                originalDate = "$prevYm-15",
                originalMerchant = "Grace Harbor Payroll",
                originalAmountCents = 2125_00L,
                originalCategoryId = 1L,
                source = "seed"
            ),
            Transaction(
                id = 108L,
                accountId = 4L,
                originalDate = "$prevYm-18",
                originalMerchant = "Costco Wholesale",
                originalAmountCents = -245_80L,
                originalCategoryId = 2L,
                source = "seed"
            )
        )

        return OrbitWorkspaceSnapshot(
            accounts = accounts,
            categories = categories,
            budgets = budgets,
            goals = goals,
            members = householdMembers,
            mhaTaxProfile = mhaTaxProfile,
            rules = rules,
            upcomingItems = upcomingItems,
            transactions = transactions
        )
    }
}
