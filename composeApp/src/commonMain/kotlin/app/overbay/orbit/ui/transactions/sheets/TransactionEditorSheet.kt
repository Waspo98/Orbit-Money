package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents
import app.overbay.orbit.ui.components.OrbitModalSheet

@Composable
fun TransactionEditorSheet(
    transaction: Transaction?,
    categories: List<Category>,
    accounts: List<Account>,
    onSave: (
        id: Long,
        merchant: String,
        amountCents: Cents,
        date: String,
        categoryId: Long?,
        notes: String?,
        isTransfer: Boolean,
        isIgnored: Boolean,
        mhaEligible: Boolean?,
        accountId: Long?
    ) -> Unit,
    onResetToOriginal: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onDismiss: () -> Unit,
    onCreateRule: ((Transaction) -> Unit)? = null,
    onMarkRecurring: ((Transaction) -> Unit)? = null,
    onImportCsv: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isInlinePane: Boolean = false
) {
    if (isInlinePane) {
        TransactionEditorContent(
            transaction = transaction,
            categories = categories,
            accounts = accounts,
            onSave = onSave,
            onResetToOriginal = onResetToOriginal,
            onDelete = onDelete,
            onSplit = onSplit,
            onDismiss = onDismiss,
            onCreateRule = onCreateRule,
            onMarkRecurring = onMarkRecurring,
            onImportCsv = onImportCsv,
            modifier = modifier
        )
    } else {
        OrbitModalSheet(
            onDismissRequest = onDismiss,
            modifier = modifier
        ) {
            TransactionEditorContent(
                transaction = transaction,
                categories = categories,
                accounts = accounts,
                onSave = onSave,
                onResetToOriginal = onResetToOriginal,
                onDelete = onDelete,
                onSplit = onSplit,
                onDismiss = onDismiss,
                onCreateRule = onCreateRule,
                onMarkRecurring = onMarkRecurring,
                onImportCsv = onImportCsv,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
