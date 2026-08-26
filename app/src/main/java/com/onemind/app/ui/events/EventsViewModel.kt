package com.onemind.app.ui.events

import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.repository.EventRepository
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val events: EventRepository,
    private val memories: MemoryRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        expireOverdueEvents()
        observeEvents()
    }

    private fun expireOverdueEvents() {
        viewModelScope.launch {
            events.expireOverdue(Instant.now(clock))
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            events.observeUpcoming().collect { upcoming ->
                val cards = hydrate(upcoming)
                _uiState.update { it.copy(upcomingEvents = cards, isLoading = false) }
            }
        }
        viewModelScope.launch {
            events.observeExpired().collect { expired ->
                val cards = hydrate(expired)
                _uiState.update { it.copy(expiredEvents = cards) }
            }
        }
    }

    /**
     * Attach the Memory data a card shows: a location, and up to three categories.
     *
     * Two batched queries per emission, not two per card. Both are keyed on
     * `memoryId`, and one Memory can own several events, so the ids are deduplicated
     * before either call.
     */
    private suspend fun hydrate(list: List<DetectedEvent>): List<EventCardUi> {
        if (list.isEmpty()) return emptyList()

        val memoryIds = list.map { it.memoryId }.distinct()
        val entities = memories.getEntitiesByMemoryIds(memoryIds)
        val categories = memories.getMemoriesByIds(memoryIds)
            .associate { it.id to it.derived.categories }

        return EventCardAssembly.assemble(list, entities, categories)
    }

    /** The user declined this event. Reversible from the same card. */
    fun reject(eventId: Long) {
        viewModelScope.launch { events.reject(eventId) }
    }

    /** Take back a rejection. */
    fun undoReject(eventId: Long) {
        viewModelScope.launch { events.undoReject(eventId) }
    }

    /**
     * Record that the calendar app's add-event screen was opened for this event.
     *
     * Called by the screen *after* the intent is dispatched, because whether it could
     * be dispatched at all is something only the screen — which holds the `Context` —
     * can know. What the user then did in the calendar app is not observable, so this
     * records the export, not a confirmed calendar entry.
     */
    fun markAddedToCalendar(eventId: Long) {
        viewModelScope.launch { events.markAddedToCalendar(eventId) }
    }

    /**
     * Export an event to the user's calendar app via ACTION_INSERT.
     *
     * This does not require any permission — it launches the calendar app's own
     * "add event" screen, and the user confirms or cancels. That is the right UX:
     * oneMind detected the event, but the user decides whether to commit it to
     * their actual calendar and set their own reminders there.
     *
     * Returns the `Intent` rather than launching it, which is what keeps a `Context`
     * out of this class.
     */
    fun exportToCalendar(event: DetectedEvent): Intent {
        val begin = event.eventTime.toEpochMilli()
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.eventTitle)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + DEFAULT_DURATION.toMillis())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private companion object {
        /** What to suggest when the content gave us a start but no end. */
        val DEFAULT_DURATION: Duration = Duration.ofHours(1)
    }
}

data class EventsUiState(
    val upcomingEvents: List<EventCardUi> = emptyList(),
    val expiredEvents: List<EventCardUi> = emptyList(),
    val isLoading: Boolean = true
)
