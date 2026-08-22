package com.onemind.app.capture

import android.content.ClipboardManager
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.onemind.app.data.processing.ProcessingScheduler
import com.onemind.app.domain.model.*
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile that saves the current clipboard content as a Memory.
 *
 * One tap from the notification shade, no app launch needed. The user explicitly
 * chooses when to save — this never registers a ClipboardManager listener, and
 * only reads the clipboard on the tap event.
 *
 * Lifecycle:
 * 1. User taps tile.
 * 2. Read ClipboardManager primary clip.
 * 3. If empty → notify "Nothing to save", done.
 * 4. Parse clip into ContentBlock (text, HTML→text, URI→URL block).
 * 5. Persist Memory (source: CLIPBOARD).
 * 6. Transition to SAVED, enqueue for processing.
 * 7. Post confirmation notification.
 *
 * The tile is always ACTIVE — capture is always available.
 */
@AndroidEntryPoint
class ClipboardTileService : TileService() {

    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var processingScheduler: ProcessingScheduler
    @Inject lateinit var notifier: CaptureNotifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Save Clipboard"
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val block = ClipboardParser.parse(clipboard?.primaryClip, this) ?: run {
            notifier.notifyMessage("Nothing to save")
            return
        }

        serviceScope.launch {
            val memory = Memory(
                sourceType = SourceType.CLIPBOARD,
                processingState = ProcessingState.DRAFT,
                contentBlocks = listOf(block)
            )

            val memoryId = memoryRepository.createMemory(memory)
            memoryRepository.transitionState(memoryId, ProcessingState.SAVED)
            processingScheduler.enqueue(memoryId)

            val preview = if (block.type != ContentType.IMAGE) block.content else null
            notifier.notify(memoryId = memoryId, previewText = preview)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
