/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.flac;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DecoderAudioRenderer;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.extractor.FlacStreamMetadata;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FlacConstants;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Decodes and renders audio using the native Flac decoder. */
public final class LibflacAudioRenderer extends DecoderAudioRenderer {

  private static final String TAG = "LibflacAudioRenderer";
  private static final int NUM_BUFFERS = 16;

  private @MonotonicNonNull FlacStreamMetadata streamMetadata;

  public LibflacAudioRenderer() {
    this(/* eventHandler= */ null, /* eventListener= */ null);
  }

  /**
   * Creates an instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioProcessors Optional {@link AudioProcessor}s that will process audio before output.
   */
  public LibflacAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioProcessor... audioProcessors) {
    super(eventHandler, eventListener, audioProcessors);
  }

  /**
   * Creates an instance.
   *
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param audioSink The sink to which audio will be output.
   */
  public LibflacAudioRenderer(
      @Nullable Handler eventHandler,
      @Nullable AudioRendererEventListener eventListener,
      AudioSink audioSink) {
    super(
        eventHandler,
        eventListener,
        audioSink);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  @FormatSupport
  protected int supportsFormatInternal(Format format) {
    if (!FlacLibrary.isAvailable()
        || !MimeTypes.AUDIO_FLAC.equalsIgnoreCase(format.sampleMimeType)) {
      return FORMAT_UNSUPPORTED_TYPE;
    }
    // Compute the format that the FLAC decoder will output.
    Format outputFormat;
    if (format.initializationData.isEmpty()) {
      // The initialization data might not be set if the format was obtained from a manifest (e.g.
      // for DASH playbacks) rather than directly from the media. In this case we assume
      // ENCODING_PCM_16BIT. If the actual encoding is different then playback will still succeed as
      // long as the AudioSink supports it, which will always be true when using DefaultAudioSink.
      outputFormat =
          Util.getPcmFormat(C.ENCODING_PCM_16BIT, format.channelCount, format.sampleRate);
    } else {
      int streamMetadataOffset =
          FlacConstants.STREAM_MARKER_SIZE + FlacConstants.METADATA_BLOCK_HEADER_SIZE;
      FlacStreamMetadata streamMetadata =
          new FlacStreamMetadata(format.initializationData.get(0), streamMetadataOffset);
      outputFormat = getOutputFormat(streamMetadata);
    }
    if (!sinkSupportsFormat(outputFormat)) {
      return FORMAT_UNSUPPORTED_SUBTYPE;
    } else if (format.exoMediaCryptoType != null) {
      return FORMAT_UNSUPPORTED_DRM;
    } else {
      return FORMAT_HANDLED;
    }
  }

  @Override
  protected FlacDecoder createDecoder(Format format, @Nullable ExoMediaCrypto mediaCrypto)
      throws FlacDecoderException {
    TraceUtil.beginSection("createFlacDecoder");
    FlacDecoder decoder =
        new FlacDecoder(NUM_BUFFERS, NUM_BUFFERS, format.maxInputSize, format.initializationData);
    streamMetadata = decoder.getStreamMetadata();
    TraceUtil.endSection();
    return decoder;
  }

  @Override
  protected Format getOutputFormat() {
    return getOutputFormat(Assertions.checkNotNull(streamMetadata));
  }

  private static Format getOutputFormat(FlacStreamMetadata streamMetadata) {
    return Util.getPcmFormat(
        Util.getPcmEncoding(streamMetadata.bitsPerSample),
        streamMetadata.channels,
        streamMetadata.sampleRate);
  }
}
