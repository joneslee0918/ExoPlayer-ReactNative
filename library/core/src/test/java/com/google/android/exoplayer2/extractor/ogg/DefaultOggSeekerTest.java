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
package com.google.android.exoplayer2.extractor.ogg;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultOggSeeker}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultOggSeekerTest {

  @Test
  public void testSetupWithUnsetEndPositionFails() {
    try {
      new DefaultOggSeeker(
          /* streamReader= */ new TestStreamReader(),
          /* payloadStartPosition= */ 0,
          /* payloadEndPosition= */ C.LENGTH_UNSET,
          /* firstPayloadPageSize= */ 1,
          /* firstPayloadPageGranulePosition= */ 1,
          /* firstPayloadPageIsLastPage= */ false);
      fail();
    } catch (IllegalArgumentException e) {
      // ignored
    }
  }

  @Test
  public void testSeeking() throws IOException, InterruptedException {
    Random random = new Random(0);
    for (int i = 0; i < 100; i++) {
      testSeeking(random);
    }
  }

  private void testSeeking(Random random) throws IOException, InterruptedException {
    OggTestFile testFile = OggTestFile.generate(random, 1000);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(testFile.data).build();
    TestStreamReader streamReader = new TestStreamReader();
    DefaultOggSeeker oggSeeker =
        new DefaultOggSeeker(
            /* streamReader= */ streamReader,
            /* payloadStartPosition= */ 0,
            /* payloadEndPosition= */ testFile.data.length,
            /* firstPayloadPageSize= */ testFile.firstPayloadPageSize,
            /* firstPayloadPageGranulePosition= */ testFile.firstPayloadPageGranulePosition,
            /* firstPayloadPageIsLastPage= */ false);
    OggPageHeader pageHeader = new OggPageHeader();

    while (true) {
      long nextSeekPosition = oggSeeker.read(input);
      if (nextSeekPosition == -1) {
        break;
      }
      input.setPosition((int) nextSeekPosition);
    }

    // Test granule 0 from file start.
    long granule = seekTo(input, oggSeeker, 0, 0);
    assertThat(granule).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    // Test granule 0 from file end.
    granule = seekTo(input, oggSeeker, 0, testFile.data.length - 1);
    assertThat(granule).isEqualTo(0);
    assertThat(input.getPosition()).isEqualTo(0);

    // Test last granule.
    granule = seekTo(input, oggSeeker, testFile.lastGranule, 0);
    long position = testFile.data.length;
    // TODO: Simplify this.
    assertThat(
            (testFile.lastGranule > granule && position > input.getPosition())
                || (testFile.lastGranule == granule && position == input.getPosition()))
        .isTrue();

    // Test exact granule.
    input.setPosition(testFile.data.length / 2);
    oggSeeker.skipToNextPage(input);
    assertThat(pageHeader.populate(input, true)).isTrue();
    position = input.getPosition() + pageHeader.headerSize + pageHeader.bodySize;
    granule = seekTo(input, oggSeeker, pageHeader.granulePosition, 0);
    // TODO: Simplify this.
    assertThat(
            (pageHeader.granulePosition > granule && position > input.getPosition())
                || (pageHeader.granulePosition == granule && position == input.getPosition()))
        .isTrue();

    for (int i = 0; i < 100; i += 1) {
      long targetGranule = (long) (random.nextDouble() * testFile.lastGranule);
      int initialPosition = random.nextInt(testFile.data.length);
      granule = seekTo(input, oggSeeker, targetGranule, initialPosition);
      long currentPosition = input.getPosition();
      if (granule == 0) {
        assertThat(currentPosition).isEqualTo(0);
      } else {
        int previousPageStart = testFile.findPreviousPageStart(currentPosition);
        input.setPosition(previousPageStart);
        pageHeader.populate(input, false);
        assertThat(granule).isEqualTo(pageHeader.granulePosition);
      }

      input.setPosition((int) currentPosition);
      pageHeader.populate(input, false);
      // The target granule should be within the current page.
      assertThat(granule).isAtMost(targetGranule);
      assertThat(targetGranule).isLessThan(pageHeader.granulePosition);
    }
  }

  private long seekTo(
      FakeExtractorInput input, DefaultOggSeeker oggSeeker, long targetGranule, int initialPosition)
      throws IOException, InterruptedException {
    long nextSeekPosition = initialPosition;
    oggSeeker.startSeek(targetGranule);
    int count = 0;
    while (nextSeekPosition >= 0) {
      if (count++ > 100) {
        fail("Seek failed to converge in 100 iterations");
      }
      input.setPosition((int) nextSeekPosition);
      nextSeekPosition = oggSeeker.read(input);
    }
    return -(nextSeekPosition + 2);
  }

  private static class TestStreamReader extends StreamReader {
    @Override
    protected long preparePayload(ParsableByteArray packet) {
      return 0;
    }

    @Override
    protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData) {
      return false;
    }
  }
}
