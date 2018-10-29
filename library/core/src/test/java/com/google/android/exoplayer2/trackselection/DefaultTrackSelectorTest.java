/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import static com.google.android.exoplayer2.RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES;
import static com.google.android.exoplayer2.RendererCapabilities.FORMAT_HANDLED;
import static com.google.android.exoplayer2.RendererCapabilities.FORMAT_UNSUPPORTED_SUBTYPE;
import static com.google.android.exoplayer2.RendererConfiguration.DEFAULT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.os.Parcel;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.RendererConfiguration;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.Parameters;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelector.InvalidationListener;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit tests for {@link DefaultTrackSelector}.
 */
@RunWith(RobolectricTestRunner.class)
public final class DefaultTrackSelectorTest {

  private static final RendererCapabilities ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO);
  private static final RendererCapabilities ALL_TEXT_FORMAT_SUPPORTED_RENDERER_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_TEXT);
  private static final RendererCapabilities ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO, FORMAT_EXCEEDS_CAPABILITIES);

  private static final RendererCapabilities VIDEO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_VIDEO);
  private static final RendererCapabilities AUDIO_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_AUDIO);
  private static final RendererCapabilities NO_SAMPLE_CAPABILITIES =
      new FakeRendererCapabilities(C.TRACK_TYPE_NONE);
  private static final RendererCapabilities[] RENDERER_CAPABILITIES =
      new RendererCapabilities[] {VIDEO_CAPABILITIES, AUDIO_CAPABILITIES};
  private static final RendererCapabilities[] RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER =
      new RendererCapabilities[] {VIDEO_CAPABILITIES, NO_SAMPLE_CAPABILITIES};

  private static final Format VIDEO_FORMAT = buildVideoFormat("video");
  private static final Format AUDIO_FORMAT = buildAudioFormat("audio");
  private static final TrackGroup VIDEO_TRACK_GROUP = new TrackGroup(VIDEO_FORMAT);
  private static final TrackGroup AUDIO_TRACK_GROUP = new TrackGroup(AUDIO_FORMAT);
  private static final TrackGroupArray TRACK_GROUPS =
      new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP);
  private static final TrackSelection[] TRACK_SELECTIONS =
      new TrackSelection[] {
        new FixedTrackSelection(VIDEO_TRACK_GROUP, 0), new FixedTrackSelection(AUDIO_TRACK_GROUP, 0)
      };
  private static final TrackSelection[] TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER =
      new TrackSelection[] {new FixedTrackSelection(VIDEO_TRACK_GROUP, 0), null};

  private static final Timeline TIMELINE = new FakeTimeline(/* windowCount= */ 1);

  private static MediaPeriodId periodId;

  @Mock private InvalidationListener invalidationListener;
  @Mock private BandwidthMeter bandwidthMeter;

  private DefaultTrackSelector trackSelector;

  @BeforeClass
  public static void setUpBeforeClass() {
    periodId = new MediaPeriodId(TIMELINE.getUidOfPeriod(/* periodIndex= */ 0));
  }

  @Before
  public void setUp() {
    initMocks(this);
    when(bandwidthMeter.getBitrateEstimate()).thenReturn(1000000L);
    trackSelector = new DefaultTrackSelector();
    trackSelector.init(invalidationListener, bandwidthMeter);
  }

  /** Tests {@link Parameters} {@link android.os.Parcelable} implementation. */
  @Test
  public void testParametersParcelable() {
    SparseArray<Map<TrackGroupArray, SelectionOverride>> selectionOverrides = new SparseArray<>();
    Map<TrackGroupArray, SelectionOverride> videoOverrides = new HashMap<>();
    videoOverrides.put(new TrackGroupArray(VIDEO_TRACK_GROUP), new SelectionOverride(0, 1));
    selectionOverrides.put(2, videoOverrides);

    SparseBooleanArray rendererDisabledFlags = new SparseBooleanArray();
    rendererDisabledFlags.put(3, true);

    Parameters parametersToParcel =
        new Parameters(
            // Video
            /* maxVideoWidth= */ 0,
            /* maxVideoHeight= */ 1,
            /* maxVideoFrameRate= */ 2,
            /* maxVideoBitrate= */ 3,
            /* exceedVideoConstraintsIfNecessary= */ false,
            /* viewportWidth= */ 4,
            /* viewportHeight= */ 5,
            /* viewportOrientationMayChange= */ true,
            // Audio
            /* preferredAudioLanguage= */ "en",
            // Text
            /* preferredTextLanguage= */ "de",
            /* selectUndeterminedTextLanguage= */ false,
            /* disabledTextTrackSelectionFlags= */ 6,
            // General
            /* forceLowestBitrate= */ true,
            /* forceHighestSupportedBitrate= */ false,
            /* allowMixedMimeAdaptiveness= */ true,
            /* allowNonSeamlessAdaptiveness= */ false,
            /* exceedRendererCapabilitiesIfNecessary= */ true,
            /* tunnelingAudioSessionId= */ C.AUDIO_SESSION_ID_UNSET,
            // Overrides
            selectionOverrides,
            rendererDisabledFlags);

    Parcel parcel = Parcel.obtain();
    parametersToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    Parameters parametersFromParcel = Parameters.CREATOR.createFromParcel(parcel);
    assertThat(parametersFromParcel).isEqualTo(parametersToParcel);

    parcel.recycle();
  }

  /** Tests {@link SelectionOverride}'s {@link android.os.Parcelable} implementation. */
  @Test
  public void testSelectionOverrideParcelable() {
    int[] tracks = new int[] {2, 3};
    SelectionOverride selectionOverrideToParcel =
        new SelectionOverride(/* groupIndex= */ 1, tracks);

    Parcel parcel = Parcel.obtain();
    selectionOverrideToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    SelectionOverride selectionOverrideFromParcel =
        SelectionOverride.CREATOR.createFromParcel(parcel);
    assertThat(selectionOverrideFromParcel).isEqualTo(selectionOverrideToParcel);

    parcel.recycle();
  }

  /** Tests that a null override clears a track selection. */
  @Test
  public void testSelectTracksWithNullOverride() throws ExoPlaybackException {
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null));
    TrackSelectorResult result =
        trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS, periodId, TIMELINE);
    assertSelections(result, new TrackSelection[] {null, TRACK_SELECTIONS[1]});
    assertThat(result.rendererConfigurations)
        .isEqualTo(new RendererConfiguration[] {null, DEFAULT});
  }

  /** Tests that a null override can be cleared. */
  @Test
  public void testSelectTracksWithClearedNullOverride() throws ExoPlaybackException {
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null)
            .clearSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP)));
    TrackSelectorResult result =
        trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS, periodId, TIMELINE);
    assertSelections(result, TRACK_SELECTIONS);
    assertThat(result.rendererConfigurations)
        .isEqualTo(new RendererConfiguration[] {DEFAULT, DEFAULT});
  }

  /** Tests that an override is not applied for a different set of available track groups. */
  @Test
  public void testSelectTracksWithNullOverrideForDifferentTracks() throws ExoPlaybackException {
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setSelectionOverride(0, new TrackGroupArray(VIDEO_TRACK_GROUP), null));
    TrackSelectorResult result =
        trackSelector.selectTracks(
            RENDERER_CAPABILITIES,
            new TrackGroupArray(VIDEO_TRACK_GROUP, AUDIO_TRACK_GROUP, VIDEO_TRACK_GROUP),
            periodId,
            TIMELINE);
    assertSelections(result, TRACK_SELECTIONS);
    assertThat(result.rendererConfigurations)
        .isEqualTo(new RendererConfiguration[] {DEFAULT, DEFAULT});
  }

  /** Tests disabling a renderer. */
  @Test
  public void testSelectTracksWithDisabledRenderer() throws ExoPlaybackException {
    trackSelector.setParameters(Parameters.DEFAULT.buildUpon().setRendererDisabled(1, true));
    TrackSelectorResult result =
        trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS, periodId, TIMELINE);
    assertSelections(result, new TrackSelection[] {TRACK_SELECTIONS[0], null});
    assertThat(new RendererConfiguration[] {DEFAULT, null})
        .isEqualTo(result.rendererConfigurations);
  }

  /** Tests that a disabled renderer can be enabled again. */
  @Test
  public void testSelectTracksWithClearedDisabledRenderer() throws ExoPlaybackException {
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setRendererDisabled(1, true)
            .setRendererDisabled(1, false));
    TrackSelectorResult result =
        trackSelector.selectTracks(RENDERER_CAPABILITIES, TRACK_GROUPS, periodId, TIMELINE);
    assertSelections(result, TRACK_SELECTIONS);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /** Tests a no-sample renderer is enabled without a track selection by default. */
  @Test
  public void testSelectTracksWithNoSampleRenderer() throws ExoPlaybackException {
    TrackSelectorResult result =
        trackSelector.selectTracks(
            RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER, TRACK_GROUPS, periodId, TIMELINE);
    assertSelections(result, TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER);
    assertThat(new RendererConfiguration[] {DEFAULT, DEFAULT})
        .isEqualTo(result.rendererConfigurations);
  }

  /** Tests disabling a no-sample renderer. */
  @Test
  public void testSelectTracksWithDisabledNoSampleRenderer() throws ExoPlaybackException {
    trackSelector.setParameters(Parameters.DEFAULT.buildUpon().setRendererDisabled(1, true));
    TrackSelectorResult result =
        trackSelector.selectTracks(
            RENDERER_CAPABILITIES_WITH_NO_SAMPLE_RENDERER, TRACK_GROUPS, periodId, TIMELINE);
    assertSelections(result, TRACK_SELECTIONS_WITH_NO_SAMPLE_RENDERER);
    assertThat(new RendererConfiguration[] {DEFAULT, null})
        .isEqualTo(result.rendererConfigurations);
  }

  /**
   * Tests that track selector will not call
   * {@link InvalidationListener#onTrackSelectionsInvalidated()} when it's set with default
   * values of {@link Parameters}.
   */
  @Test
  public void testSetParameterWithDefaultParametersDoesNotNotifyInvalidationListener()
      throws Exception {
    trackSelector.setParameters(Parameters.DEFAULT);
    verify(invalidationListener, never()).onTrackSelectionsInvalidated();
  }

  /**
   * Tests that track selector will call {@link InvalidationListener#onTrackSelectionsInvalidated()}
   * when it's set with non-default values of {@link Parameters}.
   */
  @Test
  public void testSetParameterWithNonDefaultParameterNotifyInvalidationListener()
      throws Exception {
    Parameters parameters = Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("eng").build();
    trackSelector.setParameters(parameters);
    verify(invalidationListener).onTrackSelectionsInvalidated();
  }

  /**
   * Tests that track selector will not call
   * {@link InvalidationListener#onTrackSelectionsInvalidated()} again when it's set with
   * the same values of {@link Parameters}.
   */
  @Test
  public void testSetParameterWithSameParametersDoesNotNotifyInvalidationListenerAgain()
      throws Exception {
    ParametersBuilder builder = Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("eng");
    trackSelector.setParameters(builder.build());
    trackSelector.setParameters(builder.build());
    verify(invalidationListener, times(1)).onTrackSelectionsInvalidated();
  }

  /**
   * Tests that track selector will select audio track with {@link C#SELECTION_FLAG_DEFAULT}
   * given default values of {@link Parameters}.
   */
  @Test
  public void testSelectTracksSelectTrackWithSelectionFlag() throws Exception {
    Format audioFormat =
        buildAudioFormatWithLanguageAndFlags(
            "audio", /* language= */ null, /* selectionFlags= */ 0);
    Format formatWithSelectionFlag =
        buildAudioFormatWithLanguageAndFlags(
            "audio", /* language= */ null, C.SELECTION_FLAG_DEFAULT);
    TrackGroupArray trackGroups = wrapFormats(audioFormat, formatWithSelectionFlag);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, formatWithSelectionFlag);
  }

  /**
   * Tests that track selector will select audio track with language that match preferred language
   * given by {@link Parameters}.
   */
  @Test
  public void testSelectTracksSelectPreferredAudioLanguage()
      throws Exception {
    Format frAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, "fra");
    Format enAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, "eng");
    TrackGroupArray trackGroups = wrapFormats(frAudioFormat, enAudioFormat);

    trackSelector.setParameters(
        Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("eng").build());
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            wrapFormats(frAudioFormat, enAudioFormat),
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, enAudioFormat);
  }

  /**
   * Tests that track selector will prefer selecting audio track with language that match preferred
   * language given by {@link Parameters} over track with {@link C#SELECTION_FLAG_DEFAULT}.
   */
  @Test
  public void testSelectTracksSelectPreferredAudioLanguageOverSelectionFlag()
      throws Exception {
    Format frAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, C.SELECTION_FLAG_DEFAULT, "fra");
    Format enAudioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, "eng");
    TrackGroupArray trackGroups = wrapFormats(frAudioFormat, enAudioFormat);

    trackSelector.setParameters(
        Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("eng").build());
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, enAudioFormat);
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * track that exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilities() throws Exception {
    Format supportedFormat = buildAudioFormat("supportedFormat");
    Format exceededFormat = buildAudioFormat("exceededFormat");
    TrackGroupArray trackGroups = wrapFormats(exceededFormat, supportedFormat);

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(supportedFormat.id, FORMAT_HANDLED);
    mappedCapabilities.put(exceededFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {mappedAudioRendererCapabilities},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, supportedFormat);
  }

  /**
   * Tests that track selector will select a track that exceeds the renderer's capabilities when
   * there are no other choice, given the default {@link Parameters}.
   */
  @Test
  public void testSelectTracksWithNoTrackWithinCapabilitiesSelectExceededCapabilityTrack()
      throws Exception {
    Format audioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = singleTrackGroup(audioFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, audioFormat);
  }

  /**
   * Tests that track selector will return a null track selection for a renderer when
   * all tracks exceed that renderer's capabilities when {@link Parameters} does not allow
   * exceeding-capabilities tracks.
   */
  @Test
  public void testSelectTracksWithNoTrackWithinCapabilitiesAndSetByParamsReturnNoSelection()
      throws Exception {
    Format audioFormat =
        Format.createAudioSampleFormat("audio", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = singleTrackGroup(audioFormat);

    trackSelector.setParameters(
        Parameters.DEFAULT.buildUpon().setExceedRendererCapabilitiesIfNecessary(false).build());
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertNoSelection(result.selections.get(0));
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * tracks that have {@link C#SELECTION_FLAG_DEFAULT} but exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilitiesOverSelectionFlag()
      throws Exception {
    Format exceededWithSelectionFlagFormat =
        Format.createAudioSampleFormat(
            "exceededFormat",
            MimeTypes.AUDIO_AAC,
            null,
            Format.NO_VALUE,
            Format.NO_VALUE,
            2,
            44100,
            null,
            null,
            C.SELECTION_FLAG_DEFAULT,
            null);
    Format supportedFormat =
        Format.createAudioSampleFormat("supportedFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = wrapFormats(exceededWithSelectionFlagFormat, supportedFormat);

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(supportedFormat.id, FORMAT_HANDLED);
    mappedCapabilities.put(exceededWithSelectionFlagFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {mappedAudioRendererCapabilities},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, supportedFormat);
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * track that have language matching preferred audio given by {@link Parameters} but exceed
   * renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilitiesOverPreferredLanguage()
      throws Exception {
    Format exceededEnFormat =
        Format.createAudioSampleFormat(
            "exceededFormat",
            MimeTypes.AUDIO_AAC,
            null,
            Format.NO_VALUE,
            Format.NO_VALUE,
            2,
            44100,
            null,
            null,
            0,
            "eng");
    Format supportedFrFormat =
        Format.createAudioSampleFormat("supportedFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, "fra");
    TrackGroupArray trackGroups = wrapFormats(exceededEnFormat, supportedFrFormat);

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(exceededEnFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    mappedCapabilities.put(supportedFrFormat.id, FORMAT_HANDLED);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    trackSelector.setParameters(
        Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("eng").build());
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {mappedAudioRendererCapabilities},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, supportedFrFormat);
  }

  /**
   * Tests that track selector will prefer tracks that are within renderer's capabilities over
   * track that have both language matching preferred audio given by {@link Parameters} and
   * {@link C#SELECTION_FLAG_DEFAULT}, but exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksPreferTrackWithinCapabilitiesOverSelectionFlagAndPreferredLanguage()
      throws Exception {
    Format exceededDefaultSelectionEnFormat =
        Format.createAudioSampleFormat(
            "exceededFormat",
            MimeTypes.AUDIO_AAC,
            null,
            Format.NO_VALUE,
            Format.NO_VALUE,
            2,
            44100,
            null,
            null,
            C.SELECTION_FLAG_DEFAULT,
            "eng");
    Format supportedFrFormat =
        Format.createAudioSampleFormat("supportedFormat", MimeTypes.AUDIO_AAC, null,
            Format.NO_VALUE, Format.NO_VALUE, 2, 44100, null, null, 0, "fra");
    TrackGroupArray trackGroups = wrapFormats(exceededDefaultSelectionEnFormat, supportedFrFormat);

    Map<String, Integer> mappedCapabilities = new HashMap<>();
    mappedCapabilities.put(exceededDefaultSelectionEnFormat.id, FORMAT_EXCEEDS_CAPABILITIES);
    mappedCapabilities.put(supportedFrFormat.id, FORMAT_HANDLED);
    RendererCapabilities mappedAudioRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, mappedCapabilities);

    trackSelector.setParameters(
        Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("eng").build());
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {mappedAudioRendererCapabilities},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, supportedFrFormat);
  }

  /**
   * Tests that track selector will select audio tracks with higher num channel when other factors
   * are the same, and tracks are within renderer's capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesSelectHigherNumChannel()
      throws Exception {
    Format higherChannelFormat =
        Format.createAudioSampleFormat(
            "audioFormat",
            MimeTypes.AUDIO_AAC,
            null,
            Format.NO_VALUE,
            Format.NO_VALUE,
            6,
            44100,
            null,
            null,
            0,
            null);
    Format lowerChannelFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = wrapFormats(higherChannelFormat, lowerChannelFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, higherChannelFormat);
  }

  /**
   * Tests that track selector will select audio tracks with higher sample rate when other factors
   * are the same, and tracks are within renderer's capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesSelectHigherSampleRate()
      throws Exception {
    Format higherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format lowerSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);
    TrackGroupArray trackGroups = wrapFormats(higherSampleRateFormat, lowerSampleRateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, higherSampleRateFormat);
  }

  /**
   * Tests that track selector will select audio tracks with higher bit-rate when other factors are
   * the same, and tracks are within renderer's capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesSelectHigherBitrate() throws Exception {
    Format lowerBitrateFormat =
        Format.createAudioSampleFormat(
            "audioFormat",
            MimeTypes.AUDIO_AAC,
            null,
            15000,
            Format.NO_VALUE,
            2,
            44100,
            null,
            null,
            0,
            null);
    Format higherBitrateFormat =
        Format.createAudioSampleFormat(
            "audioFormat",
            MimeTypes.AUDIO_AAC,
            null,
            30000,
            Format.NO_VALUE,
            2,
            44100,
            null,
            null,
            0,
            null);
    TrackGroupArray trackGroups = wrapFormats(lowerBitrateFormat, higherBitrateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, higherBitrateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with higher channel count over tracks with
   * higher sample rate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksPreferHigherNumChannelBeforeSampleRate()
      throws Exception {
    Format higherChannelLowerSampleRateFormat =
        Format.createAudioSampleFormat(
            "audioFormat",
            MimeTypes.AUDIO_AAC,
            null,
            Format.NO_VALUE,
            Format.NO_VALUE,
            6,
            22050,
            null,
            null,
            0,
            null);
    Format lowerChannelHigherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups =
        wrapFormats(higherChannelLowerSampleRateFormat, lowerChannelHigherSampleRateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, higherChannelLowerSampleRateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with higher sample rate over tracks with
   * higher bitrate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksPreferHigherSampleRateBeforeBitrate()
      throws Exception {
    Format higherSampleRateLowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format lowerSampleRateHigherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);
    TrackGroupArray trackGroups =
        wrapFormats(higherSampleRateLowerBitrateFormat, lowerSampleRateHigherBitrateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, higherSampleRateLowerBitrateFormat);
  }

  /**
   * Tests that track selector will select audio tracks with lower num channel when other factors
   * are the same, and tracks exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesSelectLowerNumChannel()
      throws Exception {
    Format higherChannelFormat =
        Format.createAudioSampleFormat(
            "audioFormat",
            MimeTypes.AUDIO_AAC,
            null,
            Format.NO_VALUE,
            Format.NO_VALUE,
            6,
            44100,
            null,
            null,
            0,
            null);
    Format lowerChannelFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = wrapFormats(higherChannelFormat, lowerChannelFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, lowerChannelFormat);
  }

  /**
   * Tests that track selector will select audio tracks with lower sample rate when other factors
   * are the same, and tracks exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesSelectLowerSampleRate()
      throws Exception {
    Format lowerSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);
    Format higherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = wrapFormats(higherSampleRateFormat, lowerSampleRateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, lowerSampleRateFormat);
  }

  /**
   * Tests that track selector will select audio tracks with lower bit-rate when other factors
   * are the same, and tracks exceed renderer's capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesSelectLowerBitrate()
      throws Exception {
    Format lowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = wrapFormats(lowerBitrateFormat, higherBitrateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, lowerBitrateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with lower channel count over tracks with
   * lower sample rate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesPreferLowerNumChannelBeforeSampleRate()
      throws Exception {
    Format lowerChannelHigherSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherChannelLowerSampleRateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, Format.NO_VALUE,
            Format.NO_VALUE, 6, 22050, null, null, 0, null);
    TrackGroupArray trackGroups =
        wrapFormats(higherChannelLowerSampleRateFormat, lowerChannelHigherSampleRateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, lowerChannelHigherSampleRateFormat);
  }

  /**
   * Tests that track selector will prefer audio tracks with lower sample rate over tracks with
   * lower bitrate when other factors are the same, and tracks are within renderer's
   * capabilities.
   */
  @Test
  public void testSelectTracksExceedingCapabilitiesPreferLowerSampleRateBeforeBitrate()
      throws Exception {
    Format higherSampleRateLowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format lowerSampleRateHigherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 22050, null, null, 0, null);
    TrackGroupArray trackGroups =
        wrapFormats(higherSampleRateLowerBitrateFormat, lowerSampleRateHigherBitrateFormat);

    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_EXCEEDED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, lowerSampleRateHigherBitrateFormat);
  }

  /** Tests text track selection flags. */
  @Test
  public void testsTextTrackSelectionFlags() throws ExoPlaybackException {
    Format forcedOnly = buildTextFormat("forcedOnly", "eng", C.SELECTION_FLAG_FORCED);
    Format forcedDefault =
        buildTextFormat("forcedDefault", "eng", C.SELECTION_FLAG_FORCED | C.SELECTION_FLAG_DEFAULT);
    Format defaultOnly = buildTextFormat("defaultOnly", "eng", C.SELECTION_FLAG_DEFAULT);
    Format forcedOnlySpanish = buildTextFormat("forcedOnlySpanish", "spa", C.SELECTION_FLAG_FORCED);
    Format noFlag = buildTextFormat("noFlag", "eng");

    RendererCapabilities[] textRendererCapabilities =
        new RendererCapabilities[] {ALL_TEXT_FORMAT_SUPPORTED_RENDERER_CAPABILITIES};

    // There is no text language preference, the first track flagged as default should be selected.
    TrackGroupArray trackGroups = wrapFormats(forcedOnly, forcedDefault, defaultOnly, noFlag);
    TrackSelectorResult result =
        trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, forcedDefault);

    // Ditto.
    trackGroups = wrapFormats(forcedOnly, noFlag, defaultOnly);
    result = trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, defaultOnly);

    // With no language preference and no text track flagged as default, the first forced should be
    // selected.
    trackGroups = wrapFormats(forcedOnly, noFlag);
    result = trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, forcedOnly);

    // Default flags are disabled, so the first track flagged as forced should be selected.
    trackGroups = wrapFormats(defaultOnly, noFlag, forcedOnly, forcedDefault);
    trackSelector.setParameters(
        Parameters.DEFAULT
            .buildUpon()
            .setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build());
    result = trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, forcedOnly);

    // Default flags are disabled, but there is a text track flagged as forced whose language
    // matches the preferred audio language.
    trackGroups = wrapFormats(forcedDefault, forcedOnly, defaultOnly, noFlag, forcedOnlySpanish);
    trackSelector.setParameters(
        trackSelector.getParameters().buildUpon().setPreferredAudioLanguage("spa").build());
    result = trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, forcedOnlySpanish);

    // All selection flags are disabled and there is no language preference, so nothing should be
    // selected.
    trackGroups = wrapFormats(forcedOnly, forcedDefault, defaultOnly, noFlag);
    trackSelector.setParameters(
        trackSelector
            .getParameters()
            .buildUpon()
            .setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED)
            .build());
    result = trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertNoSelection(result.selections.get(0));

    // There is a preferred language, so the first language-matching track flagged as default should
    // be selected.
    trackSelector.setParameters(
        Parameters.DEFAULT.buildUpon().setPreferredTextLanguage("eng").build());
    result = trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, forcedDefault);

    // Same as above, but the default flag is disabled. If multiple tracks match the preferred
    // language, those not flagged as forced are preferred, as they likely include the contents of
    // forced subtitles.
    trackGroups = wrapFormats(noFlag, forcedOnly, forcedDefault, defaultOnly);
    trackSelector.setParameters(
        trackSelector
            .getParameters()
            .buildUpon()
            .setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build());
    result = trackSelector.selectTracks(textRendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, noFlag);
  }

  /**
   * Tests that the default track selector will select a text track with undetermined language if no
   * text track with the preferred language is available but
   * {@link Parameters#selectUndeterminedTextLanguage} is true.
   */
  @Test
  public void testSelectUndeterminedTextLanguageAsFallback() throws ExoPlaybackException{
    Format spanish = buildTextFormat("spanish", "spa");
    Format german = buildTextFormat("german", "de");
    Format undeterminedUnd = buildTextFormat("undeterminedUnd", "und");
    Format undeterminedNull = buildTextFormat("undeterminedNull", null);

    RendererCapabilities[] textRendererCapabilites =
        new RendererCapabilities[] {ALL_TEXT_FORMAT_SUPPORTED_RENDERER_CAPABILITIES};

    TrackGroupArray trackGroups = wrapFormats(spanish, german, undeterminedUnd, undeterminedNull);
    TrackSelectorResult result =
        trackSelector.selectTracks(textRendererCapabilites, trackGroups, periodId, TIMELINE);
    assertNoSelection(result.selections.get(0));

    trackSelector.setParameters(
        Parameters.DEFAULT.buildUpon().setSelectUndeterminedTextLanguage(true).build());
    result = trackSelector.selectTracks(textRendererCapabilites, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, undeterminedUnd);

    ParametersBuilder builder = Parameters.DEFAULT.buildUpon().setPreferredTextLanguage("spa");
    trackSelector.setParameters(builder.build());
    result = trackSelector.selectTracks(textRendererCapabilites, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, spanish);

    trackGroups = wrapFormats(german, undeterminedUnd, undeterminedNull);

    result = trackSelector.selectTracks(textRendererCapabilites, trackGroups, periodId, TIMELINE);
    assertNoSelection(result.selections.get(0));

    trackSelector.setParameters(builder.setSelectUndeterminedTextLanguage(true).build());
    result = trackSelector.selectTracks(textRendererCapabilites, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, undeterminedUnd);

    trackGroups = wrapFormats(german, undeterminedNull);
    result = trackSelector.selectTracks(textRendererCapabilites, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, undeterminedNull);

    trackGroups = wrapFormats(german);
    result = trackSelector.selectTracks(textRendererCapabilites, trackGroups, periodId, TIMELINE);
    assertNoSelection(result.selections.get(0));
  }

  /** Tests audio track selection when there are multiple audio renderers. */
  @Test
  public void testSelectPreferredTextTrackMultipleRenderers() throws Exception {
    Format english = buildTextFormat("en", "en");
    Format german = buildTextFormat("de", "de");

    // First renderer handles english.
    Map<String, Integer> firstRendererMappedCapabilities = new HashMap<>();
    firstRendererMappedCapabilities.put(english.id, FORMAT_HANDLED);
    firstRendererMappedCapabilities.put(german.id, FORMAT_UNSUPPORTED_SUBTYPE);
    RendererCapabilities firstRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_TEXT, firstRendererMappedCapabilities);

    // Second renderer handles german.
    Map<String, Integer> secondRendererMappedCapabilities = new HashMap<>();
    secondRendererMappedCapabilities.put(english.id, FORMAT_UNSUPPORTED_SUBTYPE);
    secondRendererMappedCapabilities.put(german.id, FORMAT_HANDLED);
    RendererCapabilities secondRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_TEXT, secondRendererMappedCapabilities);

    RendererCapabilities[] rendererCapabilities =
        new RendererCapabilities[] {firstRendererCapabilities, secondRendererCapabilities};
    TrackGroupArray trackGroups = wrapFormats(english, german);

    // Without an explicit language preference, nothing should be selected.
    TrackSelectorResult result =
        trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);
    assertNoSelection(result.selections.get(0));
    assertNoSelection(result.selections.get(1));

    // Explicit language preference for english. First renderer should be used.
    trackSelector.setParameters(Parameters.DEFAULT.buildUpon().setPreferredTextLanguage("en"));
    result = trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, english);
    assertNoSelection(result.selections.get(1));

    // Explicit language preference for German. Second renderer should be used.
    trackSelector.setParameters(Parameters.DEFAULT.buildUpon().setPreferredTextLanguage("de"));
    result = trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);
    assertNoSelection(result.selections.get(0));
    assertFixedSelection(result.selections.get(1), trackGroups, german);
  }

  /**
   * Tests that track selector will select audio tracks with lower bitrate when {@link Parameters}
   * indicate lowest bitrate preference, even when tracks are within capabilities.
   */
  @Test
  public void testSelectTracksWithinCapabilitiesAndForceLowestBitrateSelectLowerBitrate()
      throws Exception {
    Format lowerBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 15000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    Format higherBitrateFormat =
        Format.createAudioSampleFormat("audioFormat", MimeTypes.AUDIO_AAC, null, 30000,
            Format.NO_VALUE, 2, 44100, null, null, 0, null);
    TrackGroupArray trackGroups = wrapFormats(lowerBitrateFormat, higherBitrateFormat);

    trackSelector.setParameters(Parameters.DEFAULT.buildUpon().setForceLowestBitrate(true).build());
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {ALL_AUDIO_FORMAT_SUPPORTED_RENDERER_CAPABILITIES},
            trackGroups,
            periodId,
            TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, lowerBitrateFormat);
  }

  @Test
  public void testSelectTracksWithMultipleAudioTracks() throws Exception {
    TrackGroupArray trackGroups = singleTrackGroup(buildAudioFormat("0"), buildAudioFormat("1"));
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {AUDIO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 0, 1);
  }

  @Test
  public void testSelectTracksWithMultipleAudioTracksOverrideReturnsAdaptiveTrackSelection()
      throws Exception {
    TrackGroupArray trackGroups =
        singleTrackGroup(buildAudioFormat("0"), buildAudioFormat("1"), buildAudioFormat("2"));
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setSelectionOverride(
                /* rendererIndex= */ 0,
                trackGroups,
                new SelectionOverride(/* groupIndex= */ 0, /* tracks= */ 1, 2)));
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {AUDIO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 1, 2);
  }

  /** Tests audio track selection when there are multiple audio renderers. */
  @Test
  public void testSelectPreferredAudioTrackMultipleRenderers() throws Exception {
    Format english = buildAudioFormatWithLanguage("en", "en");
    Format german = buildAudioFormatWithLanguage("de", "de");

    // First renderer handles english.
    Map<String, Integer> firstRendererMappedCapabilities = new HashMap<>();
    firstRendererMappedCapabilities.put(english.id, FORMAT_HANDLED);
    firstRendererMappedCapabilities.put(german.id, FORMAT_UNSUPPORTED_SUBTYPE);
    RendererCapabilities firstRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, firstRendererMappedCapabilities);

    // Second renderer handles german.
    Map<String, Integer> secondRendererMappedCapabilities = new HashMap<>();
    secondRendererMappedCapabilities.put(english.id, FORMAT_UNSUPPORTED_SUBTYPE);
    secondRendererMappedCapabilities.put(german.id, FORMAT_HANDLED);
    RendererCapabilities secondRendererCapabilities =
        new FakeMappedRendererCapabilities(C.TRACK_TYPE_AUDIO, secondRendererMappedCapabilities);

    RendererCapabilities[] rendererCapabilities =
        new RendererCapabilities[] {firstRendererCapabilities, secondRendererCapabilities};

    // Without an explicit language preference, prefer the first renderer.
    TrackGroupArray trackGroups = wrapFormats(english, german);
    TrackSelectorResult result =
        trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, english);
    assertNoSelection(result.selections.get(1));

    // Explicit language preference for english. First renderer should be used.
    trackSelector.setParameters(Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("en"));
    result = trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);
    assertFixedSelection(result.selections.get(0), trackGroups, english);
    assertNoSelection(result.selections.get(1));

    // Explicit language preference for German. Second renderer should be used.
    trackSelector.setParameters(Parameters.DEFAULT.buildUpon().setPreferredAudioLanguage("de"));
    result = trackSelector.selectTracks(rendererCapabilities, trackGroups, periodId, TIMELINE);
    assertNoSelection(result.selections.get(0));
    assertFixedSelection(result.selections.get(1), trackGroups, german);
  }

  @Test
  public void testSelectTracksWithMultipleVideoTracks() throws Exception {
    TrackGroupArray trackGroups = singleTrackGroup(buildVideoFormat("0"), buildVideoFormat("1"));
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {VIDEO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 0, 1);
  }

  @Test
  public void testSelectTracksWithMultipleVideoTracksOverrideReturnsAdaptiveTrackSelection()
      throws Exception {
    TrackGroupArray trackGroups =
        singleTrackGroup(buildVideoFormat("0"), buildVideoFormat("1"), buildVideoFormat("2"));
    trackSelector.setParameters(
        trackSelector
            .buildUponParameters()
            .setSelectionOverride(
                /* rendererIndex= */ 0,
                trackGroups,
                new SelectionOverride(/* groupIndex= */ 0, /* tracks= */ 1, 2)));
    TrackSelectorResult result =
        trackSelector.selectTracks(
            new RendererCapabilities[] {VIDEO_CAPABILITIES}, trackGroups, periodId, TIMELINE);

    assertThat(result.length).isEqualTo(1);
    assertAdaptiveSelection(result.selections.get(0), trackGroups.get(0), 1, 2);
  }

  private static void assertSelections(TrackSelectorResult result, TrackSelection[] expected) {
    assertThat(result.length).isEqualTo(expected.length);
    for (int i = 0; i < expected.length; i++) {
      assertThat(result.selections.get(i)).isEqualTo(expected[i]);
    }
  }

  private static void assertFixedSelection(
      TrackSelection selection, TrackGroupArray trackGroups, Format expectedFormat) {
    int trackGroupIndex = -1;
    for (int i = 0; i < trackGroups.length; i++) {
      int expectedTrack = trackGroups.get(i).indexOf(expectedFormat);
      if (expectedTrack != -1) {
        assertThat(trackGroupIndex).isEqualTo(-1);
        assertFixedSelection(selection, trackGroups.get(i), expectedTrack);
        trackGroupIndex = i;
      }
    }
    // Assert that we found the expected format in a track group
    assertThat(trackGroupIndex).isNotEqualTo(-1);
  }

  private static void assertFixedSelection(
      TrackSelection selection, TrackGroup expectedTrackGroup, int expectedTrack) {
    assertThat(selection).isInstanceOf(FixedTrackSelection.class);
    assertThat(selection.getTrackGroup()).isEqualTo(expectedTrackGroup);
    assertThat(selection.length()).isEqualTo(1);
    assertThat(selection.getIndexInTrackGroup(0)).isEqualTo(expectedTrack);
    assertThat(selection.getFormat(0))
        .isSameAs(expectedTrackGroup.getFormat(selection.getIndexInTrackGroup(0)));
  }

  private static void assertNoSelection(TrackSelection selection) {
    assertThat(selection).isNull();
  }

  private static void assertAdaptiveSelection(
      TrackSelection selection, TrackGroup expectedTrackGroup, int... expectedTracks) {
    assertThat(selection).isInstanceOf(AdaptiveTrackSelection.class);
    assertThat(selection.getTrackGroup()).isEqualTo(expectedTrackGroup);
    assertThat(selection.length()).isEqualTo(expectedTracks.length);
    for (int i = 0; i < expectedTracks.length; i++) {
      assertThat(selection.getIndexInTrackGroup(i)).isEqualTo(expectedTracks[i]);
      assertThat(selection.getFormat(i))
          .isSameAs(expectedTrackGroup.getFormat(selection.getIndexInTrackGroup(i)));
    }
  }

  private static TrackGroupArray singleTrackGroup(Format... formats) {
    return new TrackGroupArray(new TrackGroup(formats));
  }

  private static TrackGroupArray wrapFormats(Format... formats) {
    TrackGroup[] trackGroups = new TrackGroup[formats.length];
    for (int i = 0; i < trackGroups.length; i++) {
      trackGroups[i] = new TrackGroup(formats[i]);
    }
    return new TrackGroupArray(trackGroups);
  }

  private static Format buildVideoFormatWithMimeType(String id, String mimeType) {
    return Format.createVideoSampleFormat(
        id,
        mimeType,
        null,
        Format.NO_VALUE,
        Format.NO_VALUE,
        1024,
        768,
        Format.NO_VALUE,
        null,
        null);
  }

  private static Format buildVideoFormat(String id) {
    return buildVideoFormatWithMimeType(id, MimeTypes.VIDEO_H264);
  }

  private static Format buildAudioFormatWithLanguage(String id, String language) {
    return buildAudioFormatWithLanguageAndFlags(id, language, /* selectionFlags= */ 0);
  }

  private static Format buildAudioFormatWithLanguageAndFlags(
      String id, String language, int selectionFlags) {
    return buildAudioFormat(
        id,
        MimeTypes.AUDIO_AAC,
        language,
        selectionFlags,
        /* channelCount= */ 2,
        /* sampleRate= */ 44100);
  }

  private static Format buildAudioFormat(String id) {
    return buildAudioFormat(
        id,
        MimeTypes.AUDIO_AAC,
        /* language= */ null,
        /* selectionFlags= */ 0,
        /* channelCount= */ 2,
        /* sampleRate= */ 44100);
  }

  private static Format buildAudioFormat(
      String id,
      String mimeType,
      String language,
      int selectionFlags,
      int channelCount,
      int sampleRate) {
    return Format.createAudioSampleFormat(
        id,
        mimeType,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        /* maxInputSize= */ Format.NO_VALUE,
        channelCount,
        sampleRate,
        /* initializationData= */ null,
        /* drmInitData= */ null,
        selectionFlags,
        language);
  }

  private static Format buildTextFormat(String id, String language) {
    return buildTextFormat(id, language, /* selectionFlags= */ 0);
  }

  private static Format buildTextFormat(String id, String language, int selectionFlags) {
    return Format.createTextContainerFormat(
        id,
        /* label= */ null,
        /* containerMimeType= */ null,
        /* sampleMimeType= */ MimeTypes.TEXT_VTT,
        /* codecs= */ null,
        /* bitrate= */ Format.NO_VALUE,
        selectionFlags,
        language);
  }

  /**
   * A {@link RendererCapabilities} that advertises support for all formats of a given type using
   * a provided support value. For any format that does not have the given track type,
   * {@link #supportsFormat(Format)} will return {@link #FORMAT_UNSUPPORTED_TYPE}.
   */
  private static final class FakeRendererCapabilities implements RendererCapabilities {

    private final int trackType;
    private final int supportValue;

    /**
     * Returns {@link FakeRendererCapabilities} that advertises adaptive support for all
     * tracks of the given type.
     *
     * @param trackType the track type of all formats that this renderer capabilities advertises
     * support for.
     */
    FakeRendererCapabilities(int trackType) {
      this(trackType, FORMAT_HANDLED | ADAPTIVE_SEAMLESS);
    }

    /**
     * Returns {@link FakeRendererCapabilities} that advertises support level using given value
     * for all tracks of the given type.
     *
     * @param trackType the track type of all formats that this renderer capabilities advertises
     * support for.
     * @param supportValue the support level value that will be returned for formats with
     * the given type.
     */
    FakeRendererCapabilities(int trackType, int supportValue) {
      this.trackType = trackType;
      this.supportValue = supportValue;
    }

    @Override
    public int getTrackType() {
      return trackType;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return MimeTypes.getTrackType(format.sampleMimeType) == trackType
          ? (supportValue) : FORMAT_UNSUPPORTED_TYPE;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }

  }

  /**
   * A {@link RendererCapabilities} that advertises support for different formats using a mapping
   * between format ID and format-support value.
   */
  private static final class FakeMappedRendererCapabilities implements RendererCapabilities {

    private final int trackType;
    private final Map<String, Integer> formatToCapability;

    /**
     * Returns {@link FakeRendererCapabilities} that advertises support level using the given
     * mapping between format ID and format-support value.
     *
     * @param trackType the track type to be returned for {@link #getTrackType()}
     * @param formatToCapability a map of (format id, support level) that will be used to return
     * support level for any given format. For any format that's not in the map,
     * {@link #supportsFormat(Format)} will return {@link #FORMAT_UNSUPPORTED_TYPE}.
     */
    FakeMappedRendererCapabilities(int trackType, Map<String, Integer> formatToCapability) {
      this.trackType = trackType;
      this.formatToCapability = new HashMap<>(formatToCapability);
    }

    @Override
    public int getTrackType() {
      return trackType;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
      return format.id != null && formatToCapability.containsKey(format.id)
          ? formatToCapability.get(format.id)
          : FORMAT_UNSUPPORTED_TYPE;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
      return ADAPTIVE_SEAMLESS;
    }

  }

}
