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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;

/**
 * Loops a {@link MediaSource} a specified number of times.
 * <p>
 * Note: To loop a {@link MediaSource} indefinitely, it is usually better to use
 * {@link ExoPlayer#setRepeatMode(int)}.
 */
public final class LoopingMediaSource implements MediaSource {

  private final MediaSource childSource;
  private final int loopCount;

  private int childPeriodCount;

  /**
   * Loops the provided source indefinitely. Note that it is usually better to use
   * {@link ExoPlayer#setRepeatMode(int)}.
   *
   * @param childSource The {@link MediaSource} to loop.
   */
  public LoopingMediaSource(MediaSource childSource) {
    this(childSource, Integer.MAX_VALUE);
  }

  /**
   * Loops the provided source a specified number of times.
   *
   * @param childSource The {@link MediaSource} to loop.
   * @param loopCount The desired number of loops. Must be strictly positive.
   */
  public LoopingMediaSource(MediaSource childSource, int loopCount) {
    Assertions.checkArgument(loopCount > 0);
    this.childSource = childSource;
    this.loopCount = loopCount;
  }

  @Override
  public void prepareSource(ExoPlayer player, boolean isTopLevelSource, final Listener listener) {
    childSource.prepareSource(player, false, new Listener() {
      @Override
      public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
        childPeriodCount = timeline.getPeriodCount();
        Timeline loopingTimeline = loopCount != Integer.MAX_VALUE
            ? new LoopingTimeline(timeline, loopCount) : new InfinitelyLoopingTimeline(timeline);
        listener.onSourceInfoRefreshed(loopingTimeline, manifest);
      }
    });
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    childSource.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(int index, Allocator allocator) {
    return loopCount != Integer.MAX_VALUE
        ? childSource.createPeriod(index % childPeriodCount, allocator)
        : childSource.createPeriod(index, allocator);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    childSource.releasePeriod(mediaPeriod);
  }

  @Override
  public void releaseSource() {
    childSource.releaseSource();
  }

  private static final class LoopingTimeline extends AbstractConcatenatedTimeline {

    private final Timeline childTimeline;
    private final int childPeriodCount;
    private final int childWindowCount;
    private final int loopCount;

    public LoopingTimeline(Timeline childTimeline, int loopCount) {
      this.childTimeline = childTimeline;
      childPeriodCount = childTimeline.getPeriodCount();
      childWindowCount = childTimeline.getWindowCount();
      this.loopCount = loopCount;
      Assertions.checkState(loopCount <= Integer.MAX_VALUE / childPeriodCount,
          "LoopingMediaSource contains too many periods");
    }

    @Override
    public int getWindowCount() {
      return childWindowCount * loopCount;
    }

    @Override
    public int getPeriodCount() {
      return childPeriodCount * loopCount;
    }

    @Override
    protected Timeline getChild(int childIndex) {
      return childTimeline;
    }

    @Override
    protected int getChildCount() {
      return loopCount;
    }

    @Override
    protected int getChildIndexForPeriod(int periodIndex) {
      return periodIndex / childPeriodCount;
    }

    @Override
    protected int getFirstPeriodIndexInChild(int childIndex) {
      return childIndex * childPeriodCount;
    }

    @Override
    protected int getChildIndexForWindow(int windowIndex) {
      return windowIndex / childWindowCount;
    }

    @Override
    protected int getFirstWindowIndexInChild(int childIndex) {
      return childIndex * childWindowCount;
    }
  }

  private static final class InfinitelyLoopingTimeline extends Timeline {

    private final Timeline childTimeline;

    public InfinitelyLoopingTimeline(Timeline childTimeline) {
      this.childTimeline = childTimeline;
    }

    @Override
    public int getWindowCount() {
      return childTimeline.getWindowCount();
    }

    @Override
    public int getNextWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
      int childNextWindowIndex = childTimeline.getNextWindowIndex(windowIndex, repeatMode);
      return childNextWindowIndex == C.INDEX_UNSET ? 0 : childNextWindowIndex;
    }

    @Override
    public int getPreviousWindowIndex(int windowIndex, @ExoPlayer.RepeatMode int repeatMode) {
      int childPreviousWindowIndex = childTimeline.getPreviousWindowIndex(windowIndex, repeatMode);
      return childPreviousWindowIndex == C.INDEX_UNSET ? getWindowCount() - 1
          : childPreviousWindowIndex;
    }

    @Override
    public Window getWindow(int windowIndex, Window window, boolean setIds,
        long defaultPositionProjectionUs) {
      return childTimeline.getWindow(windowIndex, window, setIds, defaultPositionProjectionUs);
    }

    @Override
    public int getPeriodCount() {
      return childTimeline.getPeriodCount();
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      return childTimeline.getPeriod(periodIndex, period, setIds);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return childTimeline.getIndexOfPeriod(uid);
    }
  }
}
