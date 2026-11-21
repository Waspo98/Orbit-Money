package app.overbay.orbit.ui.dashboard

import app.overbay.orbit.data.model.AccountType
import app.overbay.orbit.data.model.BudgetPaceStatus
import app.overbay.orbit.data.model.CategoryEmojiMapper
import app.overbay.orbit.data.model.DashboardWidgetConfig
import app.overbay.orbit.data.model.OrbitPreferences
import app.overbay.orbit.data.model.OrbitWorkspaceSnapshot
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.data.model.UpcomingKind
import app.overbay.orbit.data.model.UpcomingStatus
import app.overbay.orbit.data.model.displayEmoji
import app.overbay.orbit.data.model.resolvedEmoji
import app.overbay.orbit.domain.BudgetEngine
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.domain.LocalDateUtils
import app.overbay.orbit.domain.MoneyUtils
import app.overbay.orbit.domain.NetWorthEngine
import app.overbay.orbit.domain.RetirementCalculatorEngine
import app.overbay.orbit.domain.ReviewQueueEngine
import app.overbay.orbit.domain.UpcomingEngine
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object DashboardDataTransform {

    fun buildDashboardUiState(
        snapshot: OrbitWorkspaceSnapshot,
        selectedMonth: String,
        currentMonth: String,
        isRefreshing: Boolean,
        isCustomizing: Boolean,
        syncError: String?,
        hiddenBiggestIds: Set<Long>,
        focusedGoalId: Long?,
        retirementAge: Int,
        retirementPreset: String,
        categorizeReview: CategorizeReviewState,
        recentOverlay: RecentTransactionOverlayState = RecentTransactionOverlayState()
    ): DashboardUiState {
        val today = LocalDateUtils.todayLocalDate()
        val todayIso = LocalDateUtils.formatIsoDate(today)
        val todayPlus14 = LocalDateUtils.formatIsoDate(LocalDateUtils.addDays(today, 14))

        val now = Clock.System.now()
        val hour = now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        val greeting = when {
            hour < 12 -> "🌅 Good morning"
            hour < 17 -> "☀️ Good afternoon"
            else -> "🌙 Good evening"
        }
        val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val dateLabel = "$dayName, ${LocalDateUtils.monthName(today.monthNumber)} ${today.dayOfMonth}"

        val (year, monthNum) = LocalDateUtils.parseYearMonth(selectedMonth) ?: Pair(today.year, today.monthNumber)
        val totalDaysInMonth = LocalDateUtils.daysInMonth(year, monthNum)
        val dayOfMonth = if (selectedMonth == currentMonth) today.dayOfMonth.coerceIn(1, totalDaysInMonth) else totalDaysInMonth
        val daysRemaining = (totalDaysInMonth - dayOfMonth).coerceAtLeast(0)

        val categoryById = snapshot.categories.associateBy { it.id }
        val accountById = snapshot.accounts.associateBy { it.id }

        val priorMonth = LocalDateUtils.addMonthsToYearMonth(selectedMonth, -1)
        val currentYearStr = selectedMonth.take(4)
        val scan = DashboardTransactionAggregator.scan(
            transactions = snapshot.transactions,
            accountById = accountById,
            selectedMonth = selectedMonth,
            priorMonth = priorMonth,
            currentYearStr = currentYearStr,
            hiddenBiggestIds = hiddenBiggestIds,
            categoryById = categoryById
        )
        val monthTxns = scan.monthTxns
        val incomeCents = scan.incomeCents
        val expenseCents = scan.expenseCents
        val priorIncome = scan.priorIncomeCents
        val priorExpense = scan.priorExpenseCents
        val mhaSpending = scan.mhaSpendingCents
        val mhaTxnCount = scan.mhaTransactionCount
        val topRecent = scan.topRecent

        val netCents = incomeCents - expenseCents
        val savingsRate = if (incomeCents > 0L) (netCents.toDouble() / incomeCents.toDouble()) * 100.0 else 0.0
        val priorNet = priorIncome - priorExpense
        val netDeltaPct = if (priorNet != 0L) {
            ((netCents - priorNet).toDouble() / abs(priorNet).toDouble()) * 100.0
        } else null

        val cashflowState = CashflowHeroState(
            selectedMonth = selectedMonth,
            monthLabel = LocalDateUtils.formatMonthLabel(selectedMonth),
            isCurrentMonth = selectedMonth == currentMonth,
            canGoNextMonth = selectedMonth < currentMonth,
            dayOfMonth = dayOfMonth,
            totalDaysInMonth = totalDaysInMonth,
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            netCents = netCents,
            savingsRatePercent = savingsRate,
            priorMonthNetCents = priorNet,
            netDeltaPercent = netDeltaPct
        )

        // 2. Budget Pulse (pass pre-filtered monthTxns instead of scanning all transactions again)
        val budgetOverview = BudgetEngine.calculateMonthlyOverview(
            month = selectedMonth,
            categories = snapshot.categories,
            budgets = snapshot.budgets,
            transactions = monthTxns,
            todayDate = todayIso
        )
        val percentUsed = if (budgetOverview.totalBudgetedCents > 0L) {
            ((budgetOverview.totalSpentCents.toDouble() / budgetOverview.totalBudgetedCents.toDouble()) * 100.0).roundToInt()
        } else 0
        val isOverBudget = budgetOverview.totalBudgetedCents > 0L && budgetOverview.totalSpentCents > budgetOverview.totalBudgetedCents
        val overBudgetCents = if (isOverBudget) budgetOverview.totalSpentCents - budgetOverview.totalBudgetedCents else 0L

        val budgetPulseState = BudgetPulseState(
            totalBudgetedCents = budgetOverview.totalBudgetedCents,
            totalSpentCents = budgetOverview.totalSpentCents,
            remainingCents = budgetOverview.totalRemainingCents,
            utilizationRatio = budgetOverview.utilizationRatio,
            percentUsed = percentUsed,
            dailySafeSpendCents = budgetOverview.dailySafeSpend.dailyCents,
            dailySafeSpendDetail = budgetOverview.dailySafeSpend.detail,
            dailySafeSpendTone = budgetOverview.dailySafeSpend.tone,
            daysRemaining = daysRemaining,
            isOverBudget = isOverBudget,
            overBudgetCents = overBudgetCents,
            paceStatus = if (budgetOverview.totalBudgetedCents <= 0L) BudgetPaceStatus.NO_BUDGET
            else if (isOverBudget) BudgetPaceStatus.OVER_BUDGET
            else if (budgetOverview.utilizationRatio >= 0.85) BudgetPaceStatus.WATCH
            else BudgetPaceStatus.ON_TRACK,
            hasBudgets = budgetOverview.totalBudgetedCents > 0L,
            topBudgets = budgetOverview.budgeted.take(3),
            attentionCategories = budgetOverview.budgeted.filter { it.spentCents > it.amountCents || it.utilizationRatio >= 0.85 }
        )

        // 3. Top Spending (Top 7 categories)
        val topSpendingCategories = scan.categorySpendingMap.entries
            .sortedByDescending { it.value }
            .take(7)
            .map { entry ->
                val cat = categoryById[entry.key]
                val spent = entry.value
                val pct = if (expenseCents > 0L) (spent.toDouble() / expenseCents.toDouble()) * 100.0 else 0.0
                TopSpendingCategoryItem(
                    categoryId = entry.key,
                    categoryName = cat?.name ?: if (entry.key == 0L) "Uncategorized" else "Category #${entry.key}",
                    categoryColor = cat?.color,
                    categoryIcon = cat?.displayEmoji ?: if (entry.key == 0L) "❓" else null,
                    spentCents = spent,
                    percentOfTotal = pct
                )
            }
        val topSpendingState = TopSpendingState(
            totalSpentCents = expenseCents,
            categories = topSpendingCategories
        )

        // 4. Biggest Monthly Transactions
        val expenseTxns = scan.topBiggestExpenses.map { t ->
            val cat = t.effectiveCategoryId?.let { categoryById[it] }
            val acct = accountById[t.accountId]
            val dateDisplay = LocalDateUtils.parseIsoDate(t.effectiveDate)?.let {
                LocalDateUtils.formatRelativeOrDisplayDate(it)
            } ?: t.effectiveDate
            BiggestTransactionItem(
                id = t.id,
                merchant = t.effectiveMerchant,
                amountCents = t.effectiveAmountCents,
                formattedAmount = MoneyUtils.formatCents(t.effectiveAmountCents, signed = true),
                dateDisplay = dateDisplay,
                categoryName = cat?.name ?: t.categoryName,
                categoryColor = cat?.color ?: t.categoryColor,
                categoryIcon = cat?.displayEmoji ?: CategoryEmojiMapper.resolveEmoji(t.categoryIcon),
                accountName = acct?.name ?: t.accountName
            )
        }
        val biggestTransactionsState = BiggestTransactionsState(
            transactions = expenseTxns,
            hiddenCount = hiddenBiggestIds.size
        )

        // 5. Subscriptions / Recurring (from active recurring expenses in Upcoming)
        val activeRecurringExpenses = snapshot.upcomingItems
            .filter { it.status == UpcomingStatus.ACTIVE && !it.direction.equals("income", ignoreCase = true) }
            .sortedBy { it.nextDate }
        val estimatedMonthlyTotal = activeRecurringExpenses.sumOf { item ->
            (item.amountCents * UpcomingEngine.monthlyFactor(item.frequencyType, item.frequencyInterval)).roundToLong()
        }
        val subscriptionsList = activeRecurringExpenses
            .take(5)
            .map { item ->
                val cat = item.categoryId?.let { categoryById[it] }
                val nextFormatted = LocalDateUtils.parseIsoDate(item.nextDate)?.let {
                    LocalDateUtils.formatShortDate(it)
                } ?: item.nextDate
                SubscriptionItem(
                    id = item.id,
                    name = item.name,
                    merchant = item.merchant,
                    amountCents = item.amountCents,
                    isExpense = !item.direction.equals("income", ignoreCase = true),
                    frequencyLabel = item.frequency.name.lowercase().replaceFirstChar { it.uppercase() },
                    categoryName = cat?.name,
                    categoryColor = cat?.color,
                    categoryIcon = cat?.displayEmoji,
                    nextDateDisplay = nextFormatted
                )
            }
        val subscriptionsState = SubscriptionsState(
            items = subscriptionsList,
            totalActiveCount = activeRecurringExpenses.size,
            estimatedMonthlyTotalCents = estimatedMonthlyTotal
        )

        // 6. Categorize Recent Review Queue Count
        val reviewQueueCount = ReviewQueueEngine.countReviewQueue(
            transactions = snapshot.transactions,
            categories = snapshot.categories,
            excludedIds = snapshot.preferences.handledReviewTransactionIds.toSet()
        )

        // 7. Month Comparison Table
        val monthComparisonRows = listOf(
            MonthComparisonRow(
                metric = "Spending",
                currentCents = expenseCents,
                previousCents = priorExpense,
                deltaCents = expenseCents - priorExpense,
                isPositiveImprovement = expenseCents <= priorExpense
            ),
            MonthComparisonRow(
                metric = "Income",
                currentCents = incomeCents,
                previousCents = priorIncome,
                deltaCents = incomeCents - priorIncome,
                isPositiveImprovement = incomeCents >= priorIncome
            ),
            MonthComparisonRow(
                metric = "Net",
                currentCents = netCents,
                previousCents = priorNet,
                deltaCents = netCents - priorNet,
                isPositiveImprovement = netCents >= priorNet
            )
        )
        val monthComparisonState = MonthComparisonState(
            currentMonthLabel = LocalDateUtils.formatMonthLabel(selectedMonth),
            previousMonthLabel = LocalDateUtils.formatMonthLabel(priorMonth),
            rows = monthComparisonRows
        )

        // 8. Goals Progress
        val activeGoals = snapshot.goals.filter { !it.isCompleted }
        val completedGoals = snapshot.goals.filter { it.isCompleted }
        val totalSaved = snapshot.goals.sumOf { it.currentAmountCents }
        val totalTarget = snapshot.goals.sumOf { it.targetAmountCents }
        val overallRatio = if (totalTarget > 0L) (totalSaved.toDouble() / totalTarget.toDouble()).coerceIn(0.0, 1.0) else 0.0

        val dashboardGoals = snapshot.goals.sortedBy { it.sortOrder }.map { g ->
            val parsedTarget = LocalDateUtils.parseIsoDate(g.targetDate)
            val targetDateDisplay = parsedTarget?.let { LocalDateUtils.formatDisplayDate(it) } ?: g.targetDate
            val daysUntilTarget = parsedTarget?.let { LocalDateUtils.daysBetween(today, it) }
            val monthlyPace = if (!g.isCompleted && g.remainingAmountCents > 0L && daysUntilTarget != null && daysUntilTarget > 0) {
                val months = (daysUntilTarget / 30.4375).coerceAtLeast(1.0)
                (g.remainingAmountCents.toDouble() / months).roundToLong()
            } else null

            DashboardGoalItem(
                id = g.id,
                name = g.name,
                currentAmountCents = g.currentAmountCents,
                targetAmountCents = g.targetAmountCents,
                progressRatio = g.progressRatio,
                remainingAmountCents = g.remainingAmountCents,
                isCompleted = g.isCompleted,
                targetDate = g.targetDate,
                targetDateDisplay = targetDateDisplay,
                monthlyPaceCents = monthlyPace,
                icon = g.resolvedEmoji
            )
        }
        val goalsState = GoalsProgressState(
            activeGoalsCount = activeGoals.size,
            completedGoalsCount = completedGoals.size,
            totalSavedCents = totalSaved,
            totalTargetCents = totalTarget,
            overallProgressRatio = overallRatio,
            goals = dashboardGoals.take(4)
        )

        // 9. Goal Focus
        val selectedGoal = dashboardGoals.find { it.id == focusedGoalId } ?: dashboardGoals.firstOrNull()
        val goalFocusState = GoalFocusState(
            focusedGoal = selectedGoal,
            availableGoals = dashboardGoals
        )

        // 10. Accounts Overview (Ledger)
        var cashCents: Cents = 0L
        var investmentsCents: Cents = 0L
        var creditOwedCents: Cents = 0L
        var loansOwedCents: Cents = 0L
        var realEstateEquityCents: Cents = 0L
        var otherCents: Cents = 0L
        var totalAssets: Cents = 0L
        var totalLiabilities: Cents = 0L

        val activeAccounts = snapshot.accounts.filter { !it.isArchived }
        val propertyUnits = app.overbay.orbit.domain.RealEstatePropertyEngine.buildPropertyUnits(activeAccounts)
        val realEstateMemberIds = app.overbay.orbit.domain.RealEstatePropertyEngine.realEstateMemberAccountIds(activeAccounts)
        for (unit in propertyUnits) {
            val eq = unit.netEquityCents
            realEstateEquityCents += eq
            if (eq >= 0L) totalAssets += eq else totalLiabilities += abs(eq)
        }
        for (acct in activeAccounts) {
            if (acct.id in realEstateMemberIds) continue
            val bal = acct.currentBalanceCents
            when (acct.type) {
                AccountType.CHECKING, AccountType.SAVINGS, AccountType.CASH -> {
                    cashCents += bal
                    if (bal >= 0) totalAssets += bal else totalLiabilities += abs(bal)
                }
                AccountType.INVESTMENT -> {
                    investmentsCents += bal
                    if (bal >= 0) totalAssets += bal else totalLiabilities += abs(bal)
                }
                AccountType.CREDIT -> {
                    creditOwedCents += abs(bal)
                    totalLiabilities += abs(bal)
                }
                AccountType.LOAN, AccountType.MORTGAGE -> {
                    loansOwedCents += abs(bal)
                    totalLiabilities += abs(bal)
                }
                AccountType.OTHER -> {
                    otherCents += bal
                    if (bal >= 0) totalAssets += bal else totalLiabilities += abs(bal)
                }
            }
        }
        val netWorthSnap = NetWorthEngine.computeCurrentSnapshot(snapshot.accounts)
        val accountsState = AccountsOverviewState(
            totalNetWorthCents = netWorthSnap.netWorthCents,
            totalAssetsCents = totalAssets,
            totalLiabilitiesCents = totalLiabilities,
            cashCents = cashCents,
            investmentsCents = investmentsCents,
            creditCardsOwedCents = creditOwedCents,
            loansOwedCents = loansOwedCents,
            realEstateEquityCents = realEstateEquityCents,
            otherCents = otherCents,
            accountCount = snapshot.accounts.count { !it.isArchived }
        )

        // 11. Retirement Snapshot
        val annualReturn = when (retirementPreset) {
            "conservative" -> 0.05
            "aggressive" -> 0.085
            else -> 0.07 // balanced
        }
        val inflation = when (retirementPreset) {
            "conservative" -> 0.03
            "aggressive" -> 0.0225
            else -> 0.025
        }
        val realReturn = RetirementCalculatorEngine.computeRealReturn(annualReturn, inflation)
        val yearsToRetire = (retirementAge - 35).coerceAtLeast(1) // assume current age 35 if not set
        val monthlyContributionEst = 500_00L // estimated $500 monthly
        val projectedNestEgg = RetirementCalculatorEngine.futureValue(investmentsCents, monthlyContributionEst, realReturn, yearsToRetire * 12)

        val retirementState = RetirementSnapshotState(
            currentBalanceCents = investmentsCents,
            monthlyContributionCents = monthlyContributionEst,
            projectedAtRetirementCents = projectedNestEgg,
            targetAge = retirementAge,
            presetKey = retirementPreset
        )

        // 12. MHA Summary
        val mhaSavings = (mhaSpending * 0.27).toLong() // 27% estimated income + SE tax savings
        val mhaState = MhaSummaryState(
            eligibleSpendingCents = mhaSpending,
            estimatedTaxSavingsCents = mhaSavings,
            ytdTransactionCount = mhaTxnCount
        )

        // 13. Mortgage Snapshot
        val primaryPropertyUnit = propertyUnits.firstOrNull()
        val mortgageAccount = primaryPropertyUnit?.anchorAccount
            ?: activeAccounts.find { it.type == AccountType.MORTGAGE || it.name.contains("mortgage", ignoreCase = true) }
        val mortgageBalance = primaryPropertyUnit?.totalDebtCents
            ?: mortgageAccount?.currentBalanceCents?.let { abs(it) }
            ?: loansOwedCents
        val homeValue = primaryPropertyUnit?.estimatedValueCents
            ?: mortgageAccount?.estimatedValueCents
            ?: (mortgageBalance + realEstateEquityCents)
        val estimatedEquity = (homeValue - mortgageBalance).coerceAtLeast(0L)
        val equityPct = if (homeValue > 0L) (estimatedEquity.toDouble() / homeValue.toDouble()) * 100.0 else 0.0
        val mortgageState = MortgageSnapshotState(
            hasMortgage = primaryPropertyUnit != null || mortgageAccount != null || mortgageBalance > 0L,
            mortgageAccountId = mortgageAccount?.id,
            estimatedHomeValueCents = homeValue,
            mortgageBalanceCents = mortgageBalance,
            estimatedEquityCents = estimatedEquity,
            equityPercent = equityPct
        )

        // 14. Upcoming Bills (within 14 days)
        val activeUpcoming = snapshot.upcomingItems.filter { it.status == UpcomingStatus.ACTIVE }
        val upcomingOccurrences = mutableListOf<UpcomingBillItem>()
        for (item in activeUpcoming) {
            val dates = UpcomingEngine.generateDates(item, todayIso, todayPlus14)
            val effectiveDates = if (dates.isNotEmpty()) dates else listOf(item.nextDate).filter { it in todayIso..todayPlus14 }
            val cat = item.categoryId?.let { categoryById[it] }
            val isExp = item.direction.lowercase() != "income"

            for (date in effectiveDates) {
                val days = LocalDateUtils.daysBetween(todayIso, date) ?: 0
                val dueLabel = when {
                    days == 0 -> "Today"
                    days == 1 -> "Tomorrow"
                    days < 0 -> "${-days}d overdue"
                    else -> "In $days days"
                }
                upcomingOccurrences.add(
                    UpcomingBillItem(
                        upcomingItemId = item.id,
                        name = item.name,
                        merchant = item.merchant,
                        amountCents = item.amountCents,
                        isExpense = isExp,
                        dueDateIso = date,
                        dueLabel = dueLabel,
                        daysUntilDue = days,
                        isDueSoon = days in 0..3,
                        isOverdue = days < 0,
                        categoryName = cat?.name,
                        categoryColor = cat?.color,
                        categoryIcon = cat?.displayEmoji
                    )
                )
            }
        }
        val top3Bills = upcomingOccurrences.sortedBy { it.dueDateIso }.take(3)
        val upcomingState = UpcomingBillsState(
            upcomingCount = upcomingOccurrences.size,
            totalUpcomingOutflowCents = top3Bills.filter { it.isExpense }.sumOf { it.amountCents },
            totalUpcomingInflowCents = top3Bills.filter { !it.isExpense }.sumOf { it.amountCents },
            items = top3Bills
        )

        // 15. Recent Transactions (last 10)
        val recentList = topRecent.map { txn ->
            val cat = txn.effectiveCategoryId?.let { categoryById[it] }
            val acct = accountById[txn.accountId]
            val dateDisplay = LocalDateUtils.parseIsoDate(txn.effectiveDate)?.let {
                LocalDateUtils.formatRecentCompactDate(it, today)
            } ?: txn.effectiveDate

            val hasEdits = txn.editedDate != null || txn.editedMerchant != null || txn.editedAmountCents != null ||
                txn.editedCategoryIdSource != null || txn.editedCategoryId != null ||
                txn.editedIsTransfer != null || txn.editedIsIgnored != null ||
                (snapshot.preferences.mhaTrackerEnabled && txn.editedMhaEligible != null)

            DashboardRecentTransactionItem(
                id = txn.id,
                transaction = txn,
                category = cat,
                merchant = txn.effectiveMerchant,
                isIncome = txn.isIncome,
                formattedAmount = MoneyUtils.formatCents(txn.effectiveAmountCents, signed = true),
                dateDisplay = dateDisplay,
                categoryId = txn.effectiveCategoryId,
                categoryName = cat?.name ?: txn.categoryName ?: "Uncategorized",
                categoryColor = cat?.color ?: txn.categoryColor,
                categoryIcon = cat?.displayEmoji ?: CategoryEmojiMapper.resolveEmoji(txn.categoryIcon),
                accountName = acct?.name ?: txn.accountName,
                isPending = txn.isPending,
                isTransfer = txn.effectiveIsTransfer,
                isIgnored = txn.effectiveIsIgnored,
                isMhaEligible = txn.effectiveMhaEligible ?: false,
                hasEdits = hasEdits
            )
        }
        val pickerTxn = recentOverlay.categoryPickerTransactionId?.let { targetId ->
            snapshot.transactions.find { it.id == targetId }
        }
        val expandedId = recentOverlay.expandedTransactionId?.takeIf { id ->
            recentList.any { it.id == id }
        }
        val recentState = RecentTransactionsState(
            transactions = recentList,
            expandedTransactionId = expandedId,
            categoryPickerTransaction = pickerTxn,
            pendingConfirm = recentOverlay.pendingConfirm,
            actionErrorMessage = recentOverlay.actionErrorMessage
        )

        // 16. Cards Display & Customization
        val rawWidgets = snapshot.preferences.dashboardWidgets.ifEmpty {
            OrbitPreferences.defaultDashboardWidgets()
        }
        val cardConfigs = DashboardCardType.entries.map { type ->
            val existing = rawWidgets.find {
                it.widgetId.equals(type.widgetId, ignoreCase = true) ||
                DashboardCardType.fromWidgetId(it.widgetId) == type
            }
            DashboardCardConfigItem(
                type = type,
                title = existing?.title ?: type.defaultTitle,
                description = type.defaultDescription,
                isVisible = existing?.isVisible ?: true,
                sortOrder = existing?.sortOrder ?: type.defaultSortOrder,
                wideSpan = (existing?.wideSpan ?: type.defaultWideSpan).let { if (it == 2) 2 else 1 }
            )
        }.sortedBy { it.sortOrder }

        val visibleCards = cardConfigs
            .filter { it.isVisible }
            .map { it.type }
            .filter { type ->
                if (type == DashboardCardType.MHA_SUMMARY) snapshot.preferences.mhaTrackerEnabled else true
            }

        return DashboardUiState(
            isLoading = false,
            isRefreshing = isRefreshing,
            dateLabel = dateLabel,
            greeting = greeting,
            userName = "Household",
            dayOfMonth = dayOfMonth,
            totalDaysInMonth = totalDaysInMonth,
            daysRemaining = daysRemaining,
            selectedMonth = selectedMonth,
            mhaEnabled = snapshot.preferences.mhaTrackerEnabled,
            isEmptyWorkspace = snapshot.accounts.isEmpty() && snapshot.transactions.isEmpty(),
            availableCategories = snapshot.categories,
            isReviewModalOpen = categorizeReview.isOpen,
            categorizeReview = if (categorizeReview.isOpen) {
                categorizeReview.copy(
                    categories = snapshot.categories,
                    accounts = snapshot.accounts,
                    allTransactions = snapshot.transactions
                )
            } else {
                categorizeReview
            },
            cashflow = cashflowState,
            budgetPulse = budgetPulseState,
            topSpending = topSpendingState,
            biggestTransactions = biggestTransactionsState,
            subscriptions = subscriptionsState,
            reviewQueueCount = reviewQueueCount,
            monthComparison = monthComparisonState,
            goals = goalsState,
            goalFocus = goalFocusState,
            retirement = retirementState,
            mha = mhaState,
            upcoming = upcomingState,
            mortgage = mortgageState,
            recentTransactions = recentState,
            accounts = accountsState,
            visibleCards = visibleCards,
            customization = DashboardCustomizationState(
                isCustomizing = isCustomizing,
                cardConfigs = cardConfigs
            ),
            syncErrorMessage = syncError
        )
    }
}
