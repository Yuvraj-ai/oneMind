package com.onemind.app

import com.onemind.app.domain.model.*
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.processing.stages.EventDetectionStage
import com.onemind.app.domain.repository.EventRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The stage shipped in `3f6f0a8` with no tests, because it took a Room DAO and
 * read `Instant.now()` inline — nothing here was reachable from the JVM. These
 * tests exist as much to pin the seam as the behaviour.
 */
class EventDetectionStageTest {

    private lateinit var events: EventRepository
    private lateinit var stage: EventDetectionStage

    private val saved = slot<List<DetectedEvent>>()

    // Fixed, so "future" and "past" mean the same thing whenever this runs.
    private val now: Instant = Instant.parse("2026-08-24T12:00:00Z")
    private val tomorrow: Instant = Instant.parse("2026-08-25T12:00:00Z")
    private val yesterday: Instant = Instant.parse("2026-08-23T12:00:00Z")
    private val lastWeek: Instant = Instant.parse("2026-08-17T12:00:00Z")

    @Before
    fun setup() {
        events = mockk(relaxed = true)
        stage = EventDetectionStage(events, Clock.fixed(now, ZoneOffset.UTC))
        coEvery { events.replaceEventsForMemory(any(), capture(saved)) } just Runs
    }

    private fun memory(vararg dates: ExtractedDate) = Memory(
        id = 1L,
        createdAt = now,
        contentBlocks = listOf(
            ContentBlock(id = 1L, memoryId = 1L, type = ContentType.TEXT, content = "AI Summit tickets")
        ),
        derived = DerivedData(
            dates = dates.toList(),
            summary = MemorySummary(memoryId = 1L, summaryText = "About the summit", title = "AI Summit 2026")
        )
    )

    private fun date(
        rawText: String = "September 15",
        parsedInstant: Instant? = tomorrow,
        isEventTime: Boolean = true
    ) = ExtractedDate(
        memoryId = 1L,
        rawText = rawText,
        parsedInstant = parsedInstant,
        isEventTime = isEventTime
    )

    @Test
    fun `a future event time becomes a detected event`() = runTest {
        val result = stage.process(memory(date()))

        assertEquals(StageResult.Success, result)
        assertEquals(tomorrow, saved.captured.single().eventTime)
        assertEquals(1L, saved.captured.single().memoryId)
    }

    @Test
    fun `a date the content is not about is not an event`() = runTest {
        val result = stage.process(memory(date(isEventTime = false)))

        assertEquals(StageResult.Empty, result)
    }

    @Test
    fun `a date that could not be resolved is not an event`() = runTest {
        val result = stage.process(memory(date(parsedInstant = null)))

        assertEquals(StageResult.Empty, result)
    }

    @Test
    fun `a date already past when the memory was saved is not an event`() = runTest {
        val result = stage.process(memory(date(parsedInstant = lastWeek)))

        assertEquals(StageResult.Empty, result)
    }

    @Test
    fun `a date later than the memory but already past now is not an event`() = runTest {
        // Saved a week ago describing something that has since happened. Being
        // ahead of createdAt is not enough; it has to still be ahead of now.
        // This is the assertion that needs the clock to be injectable.
        val stale = memory(date(parsedInstant = yesterday)).copy(createdAt = lastWeek)

        assertEquals(StageResult.Empty, stage.process(stale))
    }

    @Test
    fun `several future dates in one memory each become an event`() = runTest {
        val result = stage.process(
            memory(
                date(rawText = "September 15", parsedInstant = tomorrow),
                date(rawText = "September 16", parsedInstant = tomorrow.plusSeconds(86_400))
            )
        )

        assertEquals(StageResult.Success, result)
        assertEquals(2, saved.captured.size)
    }

    @Test
    fun `a memory with no events still clears whatever was there before`() = runTest {
        // Otherwise editing the date out of a Memory leaves the old event in the
        // Events tab, pointing at content that no longer mentions it.
        val result = stage.process(memory(date(parsedInstant = lastWeek)))

        assertEquals(StageResult.Empty, result)
        coVerify { events.replaceEventsForMemory(1L, emptyList()) }
    }

    @Test
    fun `events are written in one replace, so reprocessing cannot double up`() = runTest {
        stage.process(memory(date()))

        coVerify(exactly = 1) { events.replaceEventsForMemory(1L, any()) }
    }

    @Test
    fun `the stage keeps its place in the pipeline`() {
        assertEquals(StageId.EVENT_DETECTION, stage.id)
    }
}
