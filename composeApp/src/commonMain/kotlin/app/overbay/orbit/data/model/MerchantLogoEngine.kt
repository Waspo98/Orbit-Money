package app.overbay.orbit.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class LogoOverrideScope(val wireValue: String) {
    GLOBAL("global"),
    INSTITUTION("institution"),
    TRANSACTION("transaction");

    companion object {
        fun fromWire(raw: String?): LogoOverrideScope? =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class TransactionLogoScopeKeys(
    val merchantName: String,
    val slug: String,
    val isGeneric: Boolean,
    val defaultScope: LogoOverrideScope,
    val txnKey: String?,
    val scopedKey: String?,
    val globalSlugKey: String?,
    val globalDomainKey: String?,
    val explicitGlobalGenericKey: String?,
    val fallbackInstKey: String?,
    val counterpartyDomain: String?,
    val institutionLabel: String?
) {
    val globalTargetKey: String?
        get() = if (isGeneric) explicitGlobalGenericKey ?: txnKey else globalSlugKey ?: globalDomainKey ?: txnKey
}

data class LogoScopeOption(
    val scope: LogoOverrideScope,
    val label: String
)

data class LogoScopeSelectorContext(
    val defaultScope: LogoOverrideScope,
    val options: List<LogoScopeOption>
)

object MerchantLogoResolver {
    private val GENERIC_MERCHANTS = setOf(
        "unknown",
        "transfer",
        "payment",
        "online payment",
        "automatic payment",
        "deposit",
        "withdrawal",
        "check",
        "check paid",
        "atm",
        "atm withdrawal",
        "atm deposit",
        "cash withdrawal",
        "branch deposit",
        "mobile deposit",
        "check deposit",
        "remote deposit",
        "direct deposit",
        "ach deposit",
        "ach withdrawal",
        "ach transfer",
        "wire transfer",
        "electronic transfer",
        "online transfer",
        "internal transfer",
        "external transfer",
        "account transfer",
        "balance transfer",
        "autopay",
        "bill pay",
        "minimum payment",
        "credit card payment",
        "card payment",
        "loan payment",
        "mortgage payment",
        "principal payment",
        "escrow",
        "escrow payment",
        "interest",
        "interest payment",
        "interest credit",
        "interest earned",
        "interest income",
        "interest paid",
        "interest charge",
        "finance charge",
        "dividend",
        "dividends",
        "dividend payment",
        "dividend reinvestment",
        "capital gain",
        "capital gains",
        "fee",
        "annual fee",
        "monthly fee",
        "maintenance fee",
        "service fee",
        "overdraft fee",
        "nsf fee",
        "late fee",
        "wire fee",
        "atm fee",
        "foreign transaction fee",
        "account fee",
        "membership fee",
        "cash back",
        "cashback",
        "reward",
        "rewards",
        "reward credit",
        "statement credit",
        "credit",
        "refund",
        "fee refund",
        "fee waiver",
        "reimbursement",
        "adjustment",
        "balance adjustment",
        "opening balance",
        "starting balance"
    )

    private val GENERIC_EVENT_PATTERNS = listOf(
        Regex("^(savings|checking|hysa|apy|monthly|quarterly|bank|account)\\s+interest(\\s+(payment|credit|earned|income|paid|charge))?$", RegexOption.IGNORE_CASE),
        Regex("^(interest|dividend)\\s+(payments?|credits?|earned|income|paid|reinvestment|charges?)$", RegexOption.IGNORE_CASE),
        Regex("^(?:(?:bank|account|checking|savings|card|credit\\s+card|monthly|quarterly|annual|minimum\\s+balance)\\s+)*(?:annual|monthly|maintenance|service|overdraft|nsf|wire|atm|late|foreign\\s+transaction|returned\\s+item|account|membership)\\s+fees?$", RegexOption.IGNORE_CASE),
        Regex("^(atm|cash|check|mobile|remote|branch|ach|wire|direct)\\s+(deposits?|withdrawals?|transfers?|payments?)$", RegexOption.IGNORE_CASE)
    )

    private val COUNTERPARTY_TRANSFER_MERCHANTS = setOf(
        "transfer",
        "online transfer",
        "internal transfer",
        "external transfer",
        "account transfer",
        "balance transfer",
        "ach transfer",
        "wire transfer",
        "electronic transfer",
        "payment",
        "online payment",
        "automatic payment",
        "autopay",
        "bill pay",
        "minimum payment",
        "credit card payment",
        "card payment",
        "loan payment",
        "mortgage payment",
        "principal payment",
        "deposit",
        "direct deposit",
        "ach deposit",
        "ach withdrawal",
        "withdrawal",
        "check",
        "check paid"
    )

    private val COUNTERPARTY_REGEX = Regex(
        "^(ach|wire|direct|online|external)\\s+(deposits?|withdrawals?|transfers?|payments?)$",
        RegexOption.IGNORE_CASE
    )

    private val NON_ALPHANUM_SPACE_REGEX = Regex("[^a-z0-9]+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private fun normalizePhrase(value: String?): String =
        cleanMerchantNameForLogo(value)
            .lowercase()
            .replace("&", " and ")
            .replace(NON_ALPHANUM_SPACE_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    fun isInstitutionScopedMerchant(value: String?): Boolean {
        val normalized = normalizePhrase(value)
        if (normalized.isEmpty()) return false
        if (normalized in GENERIC_MERCHANTS) return true
        return GENERIC_EVENT_PATTERNS.any { it.matches(normalized) }
    }

    fun isCounterpartyTransferMerchant(value: String?): Boolean {
        val normalized = normalizePhrase(value)
        if (normalized.isEmpty()) return false
        if (normalized in COUNTERPARTY_TRANSFER_MERCHANTS) return true
        return COUNTERPARTY_REGEX.matches(normalized)
    }

    fun accountInstitutionScopeKey(account: Account?): String? {
        if (account == null) return null
        val existingKey = account.institutionLogo?.merchantKey?.takeIf { it.startsWith("inst:") }
        if (existingKey != null) return existingKey
        val domain = InstitutionLogoDefaults.resolveDomain(account.institution)
        if (domain != null) return "inst:$domain"
        val instSlug = account.institution?.let { merchantLogoKey(it) }?.takeIf { it.isNotBlank() }
        if (instSlug != null) return "inst:$instSlug"
        return if (account.id > 0L) "inst:acct-${account.id}" else null
    }

    fun resolveScopeKeys(
        transaction: Transaction,
        accountsById: Map<Long, Account>,
        transactionsById: Map<Long, Transaction>
    ): TransactionLogoScopeKeys {
        val effectiveDisplay = cleanMerchantNameForLogo(
            transaction.effectiveMerchant.ifBlank { transaction.originalMerchant }
        ).ifBlank { transaction.effectiveMerchant.ifBlank { transaction.originalMerchant } }
        val slug = merchantLogoKey(effectiveDisplay)
        val isGeneric = isInstitutionScopedMerchant(effectiveDisplay)
        val supportsCounterparty = isGeneric && isCounterpartyTransferMerchant(effectiveDisplay)

        val owningAccount = accountsById[transaction.accountId]
        val owningInstKey = accountInstitutionScopeKey(owningAccount)
        val owningDomain = InstitutionLogoDefaults.resolveDomain(owningAccount?.institution)

        val pairedAccount = transaction.transferPairId?.let { pairId ->
            transactionsById[pairId]?.let { accountsById[it.accountId] }
        }
        val pairedInstKey = accountInstitutionScopeKey(pairedAccount)?.takeIf { it != owningInstKey }

        val rawText = listOfNotNull(transaction.originalMerchant, transaction.originalDescription)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val counterpartyDomain = if (supportsCounterparty && rawText.isNotBlank()) {
            InstitutionLogoDefaults.resolveCounterpartyDomainInText(rawText, owningDomain)
        } else {
            null
        }
        val counterpartyInstKey = counterpartyDomain?.let { "inst:$it" }

        val resolvedInstKey = if (supportsCounterparty) {
            pairedInstKey ?: counterpartyInstKey ?: owningInstKey
        } else {
            owningInstKey
        }

        val txnKey = if (transaction.id > 0L) "txn:${transaction.id}" else null
        val scopedKey = if (slug.isNotBlank() && resolvedInstKey != null) "$slug@$resolvedInstKey" else null
        val explicitGlobalGenericKey = if (isGeneric && slug.isNotBlank()) "global:$slug" else null
        val globalSlugKey = if (!isGeneric && slug.isNotBlank()) slug else null

        var domain: String? = null
        if (!isGeneric) {
            domain = InstitutionLogoDefaults.extractDomain(transaction.effectiveMerchant)
            if (domain == null && transaction.editedMerchantSource == null) {
                val combined = listOfNotNull(
                    transaction.effectiveMerchant.takeIf { it.isNotBlank() },
                    transaction.originalMerchant.takeIf { it.isNotBlank() },
                    transaction.originalDescription?.takeIf { it.isNotBlank() }
                ).joinToString(" ")
                domain = InstitutionLogoDefaults.extractDomain(combined)
            }
        }
        val globalDomainKey = if (!isGeneric && domain != null) "domain-$domain" else null

        val institutionLabel = when {
            pairedInstKey != null -> pairedAccount?.institution?.takeIf { it.isNotBlank() } ?: pairedAccount?.name
            counterpartyDomain != null -> InstitutionLogoDefaults.displayNameForDomain(counterpartyDomain) ?: counterpartyDomain
            else -> owningAccount?.institution?.takeIf { it.isNotBlank() } ?: owningAccount?.name
        }

        return TransactionLogoScopeKeys(
            merchantName = effectiveDisplay,
            slug = slug,
            isGeneric = isGeneric,
            defaultScope = if (isGeneric && resolvedInstKey != null) LogoOverrideScope.INSTITUTION else LogoOverrideScope.GLOBAL,
            txnKey = txnKey,
            scopedKey = scopedKey,
            globalSlugKey = globalSlugKey,
            globalDomainKey = globalDomainKey,
            explicitGlobalGenericKey = explicitGlobalGenericKey,
            fallbackInstKey = if (isGeneric) resolvedInstKey else null,
            counterpartyDomain = counterpartyDomain,
            institutionLabel = institutionLabel
        )
    }

    fun defaultScopeForTransaction(transaction: Transaction): LogoOverrideScope {
        LogoOverrideScope.fromWire(transaction.merchantLogo?.scope)?.let { return it }
        val existingKey = transaction.merchantLogo?.merchantKey.orEmpty()
        if (existingKey.startsWith("txn:")) return LogoOverrideScope.TRANSACTION
        if ("@inst:" in existingKey) return LogoOverrideScope.INSTITUTION
        return if (isInstitutionScopedMerchant(transaction.effectiveMerchant)) {
            LogoOverrideScope.INSTITUTION
        } else {
            LogoOverrideScope.GLOBAL
        }
    }

    fun buildScopeSelectorContext(
        transaction: Transaction,
        accounts: List<Account>,
        transactions: List<Transaction>
    ): LogoScopeSelectorContext {
        val accountsById = accounts.associateBy { it.id }
        val txnsById = transactions.associateBy { it.id }
        val keys = resolveScopeKeys(transaction, accountsById, txnsById)
        val merchantLabel = keys.merchantName.ifBlank { "This Merchant" }
            .let { if (it.length > 22) "${it.take(20)}…" else it }
        val instLabel = keys.institutionLabel?.trim()?.takeIf { it.isNotBlank() }
            ?.let { if (it.length > 20) "${it.take(18)}…" else it }

        val options = buildList {
            add(LogoScopeOption(LogoOverrideScope.GLOBAL, "All \"$merchantLabel\""))
            if (keys.scopedKey != null && instLabel != null) {
                add(LogoScopeOption(LogoOverrideScope.INSTITUTION, "Only at $instLabel"))
            }
            add(LogoScopeOption(LogoOverrideScope.TRANSACTION, "Just This One"))
        }

        val preferred = defaultScopeForTransaction(transaction)
        val resolvedDefault = if (options.any { it.scope == preferred }) preferred else LogoOverrideScope.GLOBAL
        return LogoScopeSelectorContext(defaultScope = resolvedDefault, options = options)
    }

    fun resolveLocalLogo(
        transaction: Transaction,
        accountsById: Map<Long, Account>,
        transactionsById: Map<Long, Transaction>,
        overrides: Map<String, MerchantLogo>
    ): MerchantLogo? {
        val keys = resolveScopeKeys(transaction, accountsById, transactionsById)
        val isOverride = { logo: MerchantLogo? ->
            logo != null && (logo.status == "manual" || logo.status == "hidden")
        }

        keys.txnKey?.let { txnKey ->
            overrides[txnKey]?.takeIf(isOverride)?.let { return it.copy(scope = LogoOverrideScope.TRANSACTION.wireValue) }
        }
        keys.scopedKey?.let { scopedKey ->
            overrides[scopedKey]?.takeIf(isOverride)?.let { return it.copy(scope = LogoOverrideScope.INSTITUTION.wireValue) }
        }

        if (keys.isGeneric) {
            keys.explicitGlobalGenericKey?.let { gKey ->
                overrides[gKey]?.takeIf(isOverride)?.let { return it.copy(scope = LogoOverrideScope.GLOBAL.wireValue) }
            }
            val existing = transaction.merchantLogo
            if (existing != null &&
                (existing.status == "manual" || existing.status == "hidden") &&
                (existing.merchantKey == keys.txnKey || existing.merchantKey == keys.scopedKey || existing.merchantKey == keys.explicitGlobalGenericKey)
            ) {
                return existing
            }
            val instKey = keys.fallbackInstKey ?: return null
            val matchingAccount = accountsById.values.firstOrNull { accountInstitutionScopeKey(it) == instKey }
            val instLogo = matchingAccount?.institutionLogo
            if (instLogo != null) {
                return if (instLogo.status == "hidden" || instLogo.status == "failed") {
                    MerchantLogo(
                        merchantKey = keys.scopedKey ?: instLogo.merchantKey,
                        merchantName = keys.merchantName,
                        provider = instLogo.provider,
                        providerQuery = instLogo.providerQuery,
                        url = null,
                        status = instLogo.status,
                        scope = LogoOverrideScope.INSTITUTION.wireValue
                    )
                } else {
                    MerchantLogo(
                        merchantKey = keys.scopedKey ?: instLogo.merchantKey,
                        merchantName = keys.merchantName,
                        provider = instLogo.provider,
                        providerQuery = instLogo.providerQuery,
                        url = instLogo.url,
                        status = instLogo.status,
                        scope = LogoOverrideScope.INSTITUTION.wireValue
                    )
                }
            }
            val fallbackDomain = keys.counterpartyDomain
                ?: InstitutionLogoDefaults.resolveDomain(matchingAccount?.institution)
                ?: instKey.removePrefix("inst:").takeIf { "." in it }
            val fallbackUrl = fallbackDomain?.let { "https://www.google.com/s2/favicons?domain=$it&sz=128" }
            return if (fallbackUrl != null) {
                MerchantLogo(
                    merchantKey = keys.scopedKey ?: instKey,
                    merchantName = keys.merchantName,
                    provider = "logo_dev",
                    providerQuery = fallbackDomain,
                    url = fallbackUrl,
                    status = "candidate",
                    scope = LogoOverrideScope.INSTITUTION.wireValue
                )
            } else {
                null
            }
        }

        keys.globalSlugKey?.let { gKey ->
            overrides[gKey]?.takeIf(isOverride)?.let { return it.copy(scope = LogoOverrideScope.GLOBAL.wireValue) }
        }
        keys.globalDomainKey?.let { dKey ->
            overrides[dKey]?.takeIf(isOverride)?.let { return it.copy(scope = LogoOverrideScope.GLOBAL.wireValue) }
        }

        val existing = transaction.merchantLogo
        if (existing != null) {
            val matchesCurrentBrand = existing.merchantKey == keys.txnKey ||
                existing.merchantKey == keys.scopedKey ||
                existing.merchantKey == keys.globalSlugKey ||
                (keys.globalDomainKey != null && existing.merchantKey == keys.globalDomainKey)
            if (matchesCurrentBrand) return existing
        }
        return null
    }

    fun reconcileLocalTransactions(
        transactions: List<Transaction>,
        accounts: List<Account>,
        overrides: Map<String, MerchantLogo>
    ): List<Transaction> {
        if (transactions.isEmpty()) return transactions
        val accountsById = accounts.associateBy { it.id }
        val txnsById = transactions.associateBy { it.id }
        return transactions.map { txn ->
            val resolved = resolveLocalLogo(txn, accountsById, txnsById, overrides)
            if (resolved != txn.merchantLogo) txn.copy(merchantLogo = resolved) else txn
        }
    }
}
