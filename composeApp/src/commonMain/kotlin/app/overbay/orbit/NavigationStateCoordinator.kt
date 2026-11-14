package app.overbay.orbit

import app.overbay.orbit.ui.transactions.TransactionsNavSnapshot
import app.overbay.orbit.ui.transactions.TransactionsViewModel

internal data class AccountsNavSnapshot(
    val selectedAccountId: Long?
)

/**
 * Coordinates forward navigation, ViewModel deep-link setup, and per-entry ViewModel state
 * restoration on system Back so shared tab ViewModels never leak deep-link filters across frames.
 */
internal class OrbitNavigationCoordinator(
    val backStackController: OrbitBackStackController,
    val viewModels: OrbitViewModelHolder
) {
    fun applyInitialKey(initialKey: OrbitNavKey) {
        applyDestinationViewModels(initialKey)
    }

    fun navigate(destination: OrbitNavKey, fromTab: Boolean = false) {
        val leavingEntry = backStackController.currentEntry
        val samePrimaryTab = destination.primaryTabIndex != null &&
            destination.primaryTabIndex == leavingEntry.key.primaryTabIndex

        if (samePrimaryTab) {
            if (destination != leavingEntry.key) {
                backStackController.clearEntryState(leavingEntry.id)
            }
        } else {
            snapshotEntry(leavingEntry)
        }

        applyDestinationViewModels(destination)
        backStackController.navigate(destination, fromTab = fromTab)
    }

    fun popBack(): Boolean {
        if (!backStackController.popBack()) return false
        restoreEntry(backStackController.currentEntry)
        return true
    }

    private fun snapshotEntry(entry: OrbitBackStackEntry) {
        when (entry.key) {
            is OrbitNavKey.Transactions -> backStackController.saveEntryState(
                entry.id,
                viewModels.transactionsViewModel.captureNavSnapshot()
            )
            is OrbitNavKey.Accounts, is OrbitNavKey.CardPerks -> backStackController.saveEntryState(
                entry.id,
                AccountsNavSnapshot(viewModels.accountsViewModel.currentSelectedAccountId)
            )
            else -> Unit
        }
    }

    private fun restoreEntry(entry: OrbitBackStackEntry) {
        val saved = backStackController.entryStateFor(entry.id)
        when (val key = entry.key) {
            is OrbitNavKey.Transactions -> when (saved) {
                is TransactionsNavSnapshot -> viewModels.transactionsViewModel.restoreNavSnapshot(saved)
                else -> applyDestinationViewModels(key)
            }
            is OrbitNavKey.Accounts, is OrbitNavKey.CardPerks -> when (saved) {
                is AccountsNavSnapshot -> {
                    if (saved.selectedAccountId != null) {
                        viewModels.accountsViewModel.openAccountDeepLink(saved.selectedAccountId)
                    } else {
                        viewModels.accountsViewModel.dismissInspectorPane()
                    }
                }
                else -> applyDestinationViewModels(key)
            }
            else -> Unit
        }
    }

    private fun applyDestinationViewModels(destination: OrbitNavKey) {
        when (destination) {
            is OrbitNavKey.Transactions -> applyTransactionsDestination(destination, viewModels.transactionsViewModel)
            is OrbitNavKey.Accounts -> {
                if (destination.accountId != null) {
                    viewModels.accountsViewModel.openAccountDeepLink(destination.accountId)
                } else {
                    viewModels.accountsViewModel.dismissInspectorPane()
                }
            }
            is OrbitNavKey.CardPerks -> {
                if (destination.accountId != null) {
                    viewModels.accountsViewModel.openAccountDeepLink(destination.accountId)
                } else {
                    viewModels.accountsViewModel.dismissInspectorPane()
                }
            }
            OrbitNavKey.Settings -> viewModels.settingsViewModel.setSubpage(null)
            else -> Unit
        }
    }

    private fun applyTransactionsDestination(
        destination: OrbitNavKey.Transactions,
        txVm: TransactionsViewModel
    ) {
        if (destination.hasDeepLinkParams) {
            txVm.applyDeepLink(destination)
        } else {
            txVm.setSearchActive(false)
            txVm.clearFilters()
            txVm.dismissInspectorPane()
        }
    }
}
