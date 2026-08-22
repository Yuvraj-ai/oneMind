package com.onemind.app.capture

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.onemind.app.data.processing.ProcessingScheduler
import com.onemind.app.data.storage.ImageFileStorage
import com.onemind.app.domain.model.*
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives content from Android's Share menu and persists it as a Memory.
 *
 * The Activity lifecycle is:
 * 1. Parse the intent into content blocks.
 * 2. Copy shared images to app-private storage (content URIs expire after this).
 * 3. Persist the Memory.
 * 4. Transition to SAVED and enqueue for processing.
 * 5. Show confirmation notification + brief toast.
 * 6. Finish.
 *
 * No UI beyond a toast — the user stays in the app they shared from.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var parser: ShareIntentParser
    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var imageFileStorage: ImageFileStorage
    @Inject lateinit var processingScheduler: ProcessingScheduler
    @Inject lateinit var notifier: CaptureNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent ?: run { finish(); return }

        lifecycleScope.launch {
            val blocks = parser.parse(intent)
            if (blocks.isEmpty()) {
                notifier.notifyMessage("Nothing to save")
                finish()
                return@launch
            }

            // Promote temp images to permanent storage. Replace content with
            // the canonical path that ImageFileStorage produces.
            val finalBlocks = blocks.mapIndexed { index, block ->
                if (block.type == ContentType.IMAGE) {
                    try {
                        val (canonical, thumbnail) = imageFileStorage.saveImage(block.content)
                        block.copy(
                            content = canonical,
                            thumbnailPath = thumbnail,
                            position = index
                        )
                    } catch (e: Exception) {
                        // Image copy failed — skip this block rather than crash.
                        null
                    }
                } else {
                    block.copy(position = index)
                }
            }.filterNotNull()

            if (finalBlocks.isEmpty()) {
                notifier.notifyMessage("Could not read shared content")
                finish()
                return@launch
            }

            val sourcePackage = parser.detectSource(intent) ?: callingPackage

            val memory = Memory(
                sourceType = SourceType.SHARE,
                processingState = ProcessingState.DRAFT,
                contentBlocks = finalBlocks,
                sourcePackage = sourcePackage
            )

            val memoryId = memoryRepository.createMemory(memory)
            memoryRepository.transitionState(memoryId, ProcessingState.SAVED)
            processingScheduler.enqueue(memoryId)

            // Notification with preview text.
            val preview = finalBlocks
                .firstOrNull { it.type == ContentType.TEXT || it.type == ContentType.URL }
                ?.content

            notifier.notify(memoryId = memoryId, previewText = preview)

            Toast.makeText(this@ShareReceiverActivity, "Saved to Memory", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
