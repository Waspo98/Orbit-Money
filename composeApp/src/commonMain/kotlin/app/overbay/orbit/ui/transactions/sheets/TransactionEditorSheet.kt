package app.overbay.orbit.ui.transactions.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.overbay.orbit.data.model.Account
import app.overbay.orbit.data.model.Category
import app.overbay.orbit.data.model.Transaction
import app.overbay.orbit.domain.Cents

@OptIn(ExperimentalMaterial3Api::class)
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
        mhaEligible: Boolean?
    ) -> Unit,
    onResetToOriginal: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onDismiss: () -> Unit,
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
            modifier = modifier
        )
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }
}
