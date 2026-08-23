package com.onemind.app.ui.events

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.entity.DetectedEventEntity
import com.onemind.app.domain.model.EventStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val eventDao: EventDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        expireOverdueEvents()
        observeEvents()
    }

    private fun expireOverdueEvents() {
        viewModelScope.launch {
            eventDao.expireOverdue(Instant.now().toEpochMilli())
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            eventDao.observeUpcoming().collect { upcoming ->
                _uiState.update { it.copy(upcomingEvents = upcoming, isLoading = false) }
            }
        }
        viewModelScope.launch {
            eventDao.observeExpired().collect { expired ->
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
    fun exportToCalendar(event: DetectedEventEntity): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.eventTitle)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.eventTime)
            // Default duration: 1 hour.
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.eventTime + 3600_000L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}

data class EventsUiState(
    val upcomingEvents: List<DetectedEventEntity> = emptyList(),
    val expiredEvents: List<DetectedEventEntity> = emptyList(),
    val isLoading: Boolean = true
)
