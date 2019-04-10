/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.os.ConditionVariable;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.Download.State;
import com.google.android.exoplayer2.offline.DownloadManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** A {@link DownloadManager.Listener} for testing. */
public final class TestDownloadManagerListener implements DownloadManager.Listener {

  private static final int TIMEOUT = 1000;
  private static final int INITIALIZATION_TIMEOUT = 10000;
  private static final int STATE_REMOVED = -1;

  private final DownloadManager downloadManager;
  private final DummyMainThread dummyMainThread;
  private final HashMap<String, ArrayBlockingQueue<Integer>> actionStates;
  private final ConditionVariable initializedCondition;

  private CountDownLatch downloadFinishedCondition;
  @Download.FailureReason private int failureReason;

  public TestDownloadManagerListener(
      DownloadManager downloadManager, DummyMainThread dummyMainThread) {
    this.downloadManager = downloadManager;
    this.dummyMainThread = dummyMainThread;
    actionStates = new HashMap<>();
    initializedCondition = new ConditionVariable();
    downloadManager.addListener(this);
  }

  public Integer pollStateChange(String taskId, long timeoutMs) throws InterruptedException {
    return getStateQueue(taskId).poll(timeoutMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void onInitialized(DownloadManager downloadManager) {
    initializedCondition.open();
  }

  public void waitUntilInitialized() {
    if (!downloadManager.isInitialized()) {
      assertThat(initializedCondition.block(INITIALIZATION_TIMEOUT)).isTrue();
    }
  }

  @Override
  public void onDownloadChanged(DownloadManager downloadManager, Download download) {
    if (download.state == Download.STATE_FAILED) {
      failureReason = download.failureReason;
    }
    getStateQueue(download.action.id).add(download.state);
  }

  @Override
  public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
    getStateQueue(download.action.id).add(STATE_REMOVED);
  }

  @Override
  public synchronized void onIdle(DownloadManager downloadManager) {
    if (downloadFinishedCondition != null) {
      downloadFinishedCondition.countDown();
    }
  }

  /**
   * Blocks until all remove and download tasks are complete and throws an exception if there was an
   * error.
   */
  public void blockUntilTasksCompleteAndThrowAnyDownloadError() throws Throwable {
    blockUntilTasksComplete();
    if (failureReason != Download.FAILURE_REASON_NONE) {
      throw new Exception("Failure reason: " + Download.getFailureString(failureReason));
    }
  }

  /** Blocks until all remove and download tasks are complete. Task errors are ignored. */
  public void blockUntilTasksComplete() throws InterruptedException {
    synchronized (this) {
      downloadFinishedCondition = new CountDownLatch(1);
    }
    dummyMainThread.runOnMainThread(
        () -> {
          if (downloadManager.isIdle()) {
            downloadFinishedCondition.countDown();
          }
        });
    assertThat(downloadFinishedCondition.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
  }

  private ArrayBlockingQueue<Integer> getStateQueue(String taskId) {
    synchronized (actionStates) {
      if (!actionStates.containsKey(taskId)) {
        actionStates.put(taskId, new ArrayBlockingQueue<>(10));
      }
      return actionStates.get(taskId);
    }
  }

  public void assertRemoved(String taskId, int timeoutMs) {
    assertStateInternal(taskId, STATE_REMOVED, timeoutMs);
  }

  public void assertState(String taskId, @State int expectedState, int timeoutMs) {
    assertStateInternal(taskId, expectedState, timeoutMs);
  }

  private void assertStateInternal(String taskId, int expectedState, int timeoutMs) {
    ArrayList<Integer> receivedStates = new ArrayList<>();
    while (true) {
      Integer state = null;
      try {
        state = pollStateChange(taskId, timeoutMs);
      } catch (InterruptedException e) {
        fail(e.getMessage());
      }
      if (state != null) {
        if (expectedState == state) {
          return;
        }
        receivedStates.add(state);
      } else {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < receivedStates.size(); i++) {
          if (i > 0) {
            sb.append(',');
          }
          int receivedState = receivedStates.get(i);
          String receivedStateString =
              receivedState == STATE_REMOVED ? "REMOVED" : Download.getStateString(receivedState);
          sb.append(receivedStateString);
        }
        fail(
            String.format(
                Locale.US,
                "for download (%s) expected:<%s> but was:<%s>",
                taskId,
                Download.getStateString(expectedState),
                sb));
      }
    }
  }
}
