package com.onemind.app.ui.events

import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.repository.EventRepository
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
                _uiState.update { it.copy(upcomingEvents = upcoming, isLoading = false) }
            }
        }
        viewModelScope.launch {
            events.observeExpired().collect { expired ->
                _uiState.update { it.copy(expiredEvents = expired) }
            }
        }
    }

    /**
     * Export an event to the user's calendar app via ACTION_INSERT.
     *
     * This does not require any permission — it launches the calendar app's own
     * "add event" screen, and the user confirms or cancels. That is the right UX:
     * oneMind detected the event, but the user decides whether to commit it to
     * their actual calendar and set their own reminders there.
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
    val upcomingEvents: List<DetectedEvent> = emptyList(),
    val expiredEvents: List<DetectedEvent> = emptyList(),
    val isLoading: Boolean = true
)
