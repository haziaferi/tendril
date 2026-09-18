package com.tendril.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.tendril.app.data.page.Label
import com.tendril.app.domain.DatabaseSyncManager
import com.tendril.app.domain.PageContentRepository
import com.tendril.app.domain.PurgeRegistry
import com.tendril.app.domain.ResolveEntryUseCase
import com.tendril.app.domain.TemplateManager
import com.tendril.app.domain.ViewLockState
import com.tendril.app.sync.FakeBlockDao
import com.tendril.app.sync.FakePageCanvasDao
import com.tendril.app.sync.FakeCanvasNodeDao
import com.tendril.app.sync.FakeCanvasEdgeDao
import com.tendril.app.sync.FakeEntryCompletionDao
import com.tendril.app.sync.FakeEntryDao
import com.tendril.app.sync.FakeHabitDao
import com.tendril.app.sync.FakeLabelDao
import com.tendril.app.sync.FakePageDao
import com.tendril.app.sync.FakePageDatabaseDao
import com.tendril.app.sync.FakePageFtsDao
import com.tendril.app.sync.FakePageStore
import com.tendril.app.sync.FakePropertyDao
import com.tendril.app.sync.FakePropertyValueDao
import com.tendril.app.sync.FakePurgedRecordDao
import com.tendril.app.sync.RecordingEntryScheduleCoordinator
import com.tendril.app.ui.nav.DensityProfile
import com.tendril.app.ui.nav.LocalDensityProfile
import com.tendril.app.ui.pages.LabelFilterRow
import com.tendril.app.ui.pages.PagesViewModel
import com.tendril.app.ui.theme.Register
import com.tendril.app.ui.theme.TendrilTheme
import com.tendril.app.ui.theme.TendrilTypeface
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone's fix PR (P11, 2026-09-18) — the one check the stand-in lacked: a layout composed under
 * the Touch profile. `main` had crashed on every phone launch for two days (`LabelFilterRow`'s
 * bottom padding went negative under Touch whenever a label existed, `phone-catch-up.md`) and no
 * JVM test, audit rule or desktop walk could see it — a `Modifier.padding` throws at composition,
 * so composing the row here is the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhoneLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the label filter row composes under Touch with a label in the store`() {
        val store = FakePageStore()
        val pageDao = FakePageDao(store); val blockDao = FakeBlockDao(store); val labelDao = FakeLabelDao(store)
        val pageDatabaseDao = FakePageDatabaseDao(store); val propertyDao = FakePropertyDao(store)
        val entryDao = FakeEntryDao(); val coordinator = RecordingEntryScheduleCoordinator()
        val resolve = ResolveEntryUseCase(entryDao, FakeEntryCompletionDao(), coordinator)
        // Before the ViewModel: the fake dao's `observeAll` is a snapshot taken when it is called.
        runBlocking { labelDao.insert(Label(name = "errand")) }
        val viewModel = PagesViewModel(
            pageDao, pageDatabaseDao, propertyDao, FakePageFtsDao(store), labelDao,
            PurgeRegistry(FakePurgedRecordDao(), pageDao, entryDao, FakeHabitDao(), propertyDao, coordinator),
            DatabaseSyncManager(pageDao, pageDatabaseDao, FakePropertyValueDao(store), entryDao, FakeEntryCompletionDao(), resolve),
            TemplateManager(pageDao, blockDao, pageDatabaseDao, propertyDao, FakePageCanvasDao(store), FakeCanvasNodeDao(store), FakeCanvasEdgeDao(store)), ViewLockState(),
            PageContentRepository(pageDao, blockDao, FakePageFtsDao(store)), entryDao, resolve,
        )
        compose.setContent {
            TendrilTheme(register = Register.INK, dark = false, typeface = TendrilTypeface.INTER) {
                CompositionLocalProvider(LocalDensityProfile provides DensityProfile.TOUCH) {
                    LabelFilterRow(viewModel)
                }
            }
        }
        // The chip only exists once the labels flow has emitted; without it the row composes nothing
        // and the padding under test is never built — so the chip is the assertion.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("errand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("errand").assertIsDisplayed()
    }
}
