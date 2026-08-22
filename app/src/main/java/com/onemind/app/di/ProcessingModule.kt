package com.onemind.app.di

import com.onemind.app.data.ai.ProviderImageDescriber
import com.onemind.app.data.ocr.MlKitTextRecognizer
import com.onemind.app.domain.processing.ImageDescriber
import com.onemind.app.domain.processing.ProcessingStage
import com.onemind.app.domain.processing.TextRecognizer
import com.onemind.app.domain.processing.stages.OcrStage
import com.onemind.app.domain.processing.stages.VisionStage
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Wires the Processing Pipeline's stages.
 *
 * `@Multibinds` lets the stage set be legitimately empty, which is how it
 * started life. Each stage is added with one `@Binds @IntoSet` line and the
 * pipeline never changes. Execution order comes from `StageId`, not from the
 * order of these declarations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessingModule {

    @Multibinds
    abstract fun processingStages(): Set<ProcessingStage>

    @Binds
    @IntoSet
    abstract fun bindOcrStage(stage: OcrStage): ProcessingStage

    @Binds
    @IntoSet
    abstract fun bindVisionStage(stage: VisionStage): ProcessingStage

    @Binds
    abstract fun bindTextRecognizer(impl: MlKitTextRecognizer): TextRecognizer

    @Binds
    abstract fun bindImageDescriber(impl: ProviderImageDescriber): ImageDescriber
}
