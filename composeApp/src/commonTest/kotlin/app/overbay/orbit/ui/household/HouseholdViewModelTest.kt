package app.overbay.orbit.ui.household

import app.overbay.orbit.data.OrbitRepository
import app.overbay.orbit.data.model.HouseholdMember
import app.overbay.orbit.data.model.HouseholdRole
import app.overbay.orbit.util.PlatformStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var testScope = CoroutineScope(SupervisorJob() + testDispatcher)
    private lateinit var storage: PlatformStorage
    private lateinit var repository: OrbitRepository
    private lateinit var viewModel: HouseholdViewModel

    @BeforeTest
    fun setUp() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        storage = PlatformStorage()
        storage.clear()
        repository = OrbitRepository(storage)
        repository.initialize()
        viewModel = HouseholdViewModel(repository, testScope)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
        storage.clear()
    }

    @Test
    fun testInitialState_loadsHouseholdAndMembers() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals("My Household", state.householdInfo.name)
        assertFalse(state.showInviteDialog)
        assertFalse(state.showMemberEditor)
    }

    @Test
    fun testUpdateHouseholdName_persistsAndUpdatesState() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.updateHouseholdName("The Overbay Family")
        testScheduler.advanceUntilIdle()

        assertEquals("The Overbay Family", viewModel.uiState.value.householdInfo.name)
        assertEquals("The Overbay Family", repository.snapshot.value.household.name)
    }

    @Test
    fun testSaveMember_addsAndUpdatesMember() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val member = HouseholdMember(
            name = "Alex",
            role = "adult",
            employer = "Acme Corp",
            grossIncomeAnnualCents = 120_000_00L
        )

        viewModel.saveMember(member)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.members.size)
        assertEquals("Alex", state.members.first().name)
        assertEquals(120_000_00L, state.totalHouseholdGrossIncomeCents)
    }

    @Test
    fun testDeleteMember_removesMember() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val member = HouseholdMember(
            id = 10L,
            name = "Sam",
            role = "child",
            grossIncomeAnnualCents = 0L
        )
        repository.saveHouseholdMember(member)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.members.size)

        viewModel.deleteMember(10L)
        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.members.size)
    }

    @Test
    fun testInviteMember_recordsShareInvite() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.inviteMember("spouse@example.com", HouseholdRole.ADMIN)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.shares.size)
        assertEquals("spouse@example.com", state.shares.first().invitedEmail)
        assertEquals(HouseholdRole.ADMIN, state.shares.first().role)
    }

    @Test
    fun testRevokeInvite_removesShare() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        viewModel.inviteMember("advisor@example.com", HouseholdRole.MEMBER)
        testScheduler.advanceUntilIdle()

        val shareId = viewModel.uiState.value.shares.first().id
        viewModel.revokeInvite(shareId)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.shares.isEmpty())
    }

    @Test
    fun testGrossIncomeSum_aggregatesAllEarners() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val m1 = HouseholdMember(id = 1L, name = "Earner 1", grossIncomeAnnualCents = 85_000_00L)
        val m2 = HouseholdMember(id = 2L, name = "Earner 2", grossIncomeAnnualCents = 65_000_00L)
        repository.saveHouseholdMember(m1)
        repository.saveHouseholdMember(m2)
        testScheduler.advanceUntilIdle()

        assertEquals(150_000_00L, viewModel.uiState.value.totalHouseholdGrossIncomeCents)
    }
}
