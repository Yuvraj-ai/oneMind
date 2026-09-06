package com.onemind.app

import com.onemind.app.data.processing.ProcessingScheduler
import com.onemind.app.data.storage.ImageFileStorage
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.repository.MemoryRepository
import com.onemind.app.ui.composer.ComposerViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * That leaving the composer hands the Memory to the enrichment pipeline.
 *
 * This is the bug in #50, and it is a race rather than a missing call.
 * `ComposerScreen.handleBack` invokes `onLeaveComposer()` — which *launches* the commit and
 * returns — then immediately calls `onNavigateBack()`. The pop clears the
 * `ComposerViewModel`, `viewModelScope` is cancelled, and the launched coroutine dies partway
 * through. `enqueue` sat behind four suspension points, so it was the statement that
 * essentially never ran: Memories were written, usually reached `SAVED`, and were never
 * enriched, indexed or searchable. Share, clipboard and screenshot capture were unaffected,
 * because those paths enqueue from a scope nothing cancels.
 *
 * The tests model that shape directly: the ViewModel is held in a real [ViewModelStore], and
 * `store.clear()` after `onLeaveComposer()` *is* the pop — it calls `ViewModel.clear()`, which
 * cancels `viewModelScope` for real rather than by proxy. Work that still completes afterwards
 * is work that survives leaving the screen.
 *
 * An earlier version of this test tried to model the cancellation by setting `Dispatchers.Main`
 * to a `TestDispatcher` that was never advanced. That does not work, and quietly: `runTest`
 * adopts the scheduler of whatever `TestDispatcher` is installed as `Main`, so advancing the
 * application scope advanced `viewModelScope` too and the test passed against the bug. It was
 * only caught by reverting the fix and watching the test stay green.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerCommitTest {

    private val repository = mockk<MemoryRepository>(relaxed = true)
    private val imageFileStorage = mockk<ImageFileStorage>(relaxed = true)
    private val scheduler = mockk<ProcessingScheduler>(relaxed = true)

    /** `viewModelScope` dispatches on `Main.immediate`, so it has to be a test dispatcher. */
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The ViewModel, held the way the framework holds it — so it can be cleared. */
    private class Host(private val vm: ComposerViewModel) {
        val store = ViewModelStore()

        val viewModel: ComposerViewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            }
        )[ComposerViewModel::class.java]

        /** What `onNavigateBack()` amounts to: the entry is popped, the ViewModel cleared. */
        fun pop() = store.clear()
    }

    private fun host(appScope: TestScope) = Host(
        ComposerViewModel(
            memoryRepository = repository,
            imageFileStorage = imageFileStorage,
            processingScheduler = scheduler,
            appScope = appScope,
            context = mockk(relaxed = true)
        )
    )

    private fun stubNewMemory(id: Long) {
        val draft = Memory(id = id, processingState = ProcessingState.DRAFT)
        coEvery { repository.createMemory(any()) } returns id
        coEvery { repository.getMemoryById(id) } returnsMany listOf(
            draft,
            draft.copy(processingState = ProcessingState.SAVED)
        )
    }

    @Test
    fun leavingTheComposerEnqueuesEnrichmentEvenThoughTheScreenIsGone() = runTest {
        stubNewMemory(7L)
        val host = host(TestScope(testScheduler))
        host.viewModel.onTextChanged("Booked the dentist for Thursday at 3pm")

        host.viewModel.onLeaveComposer()
        host.pop()

        advanceUntilIdle()

        verify(exactly = 1) { scheduler.enqueue(7L) }
    }

    @Test
    fun theMemoryIsCommittedOutOfDraftOnTheWayOut() = runTest {
        stubNewMemory(9L)
        val host = host(TestScope(testScheduler))
        host.viewModel.onTextChanged("The cafe wifi password is hunter2")

        host.viewModel.onLeaveComposer()
        host.pop()
        advanceUntilIdle()

        // DRAFT -> SAVED is what makes the Memory eligible at all, and it used to land only
        // because it sits earlier in the coroutine than the enqueue did.
        coVerify(exactly = 1) { repository.transitionState(9L, ProcessingState.SAVED) }
        verify(exactly = 1) { scheduler.enqueue(9L) }
    }

    @Test
    fun anEmptyComposerEnqueuesNothing() = runTest {
        val host = host(TestScope(testScheduler))

        host.viewModel.onLeaveComposer()
        host.pop()
        advanceUntilIdle()

        // Nothing typed and nothing attached is not a Memory. Enqueuing here would hand the
        // pipeline an id for something that was never created.
        verify(exactly = 0) { scheduler.enqueue(any()) }
    }
}
