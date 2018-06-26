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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.C.WIDEVINE_UUID;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_MP4;
import static com.google.android.exoplayer2.util.MimeTypes.VIDEO_WEBM;
import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.Parcel;

import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Unit test for {@link Format}.
 */
@RunWith(RobolectricTestRunner.class)
public final class FormatTest {

  private static final List<byte[]> INIT_DATA;
  static {
    byte[] initData1 = new byte[] {1, 2, 3};
    byte[] initData2 = new byte[] {4, 5, 6};
    List<byte[]> initData = new ArrayList<>();
    initData.add(initData1);
    initData.add(initData2);
    INIT_DATA = Collections.unmodifiableList(initData);
  }

  @Test
  public void testParcelable() {
    DrmInitData.SchemeData drmData1 = new DrmInitData.SchemeData(WIDEVINE_UUID, VIDEO_MP4,
        TestUtil.buildTestData(128, 1 /* data seed */));
    DrmInitData.SchemeData drmData2 = new DrmInitData.SchemeData(C.UUID_NIL, VIDEO_WEBM,
        TestUtil.buildTestData(128, 1 /* data seed */));
    DrmInitData drmInitData = new DrmInitData(drmData1, drmData2);
    byte[] projectionData = new byte[] {1, 2, 3};
    Metadata metadata = new Metadata(
        new TextInformationFrame("id1", "description1", "value1"),
        new TextInformationFrame("id2", "description2", "value2"));
    ColorInfo colorInfo =  new ColorInfo(C.COLOR_SPACE_BT709,
        C.COLOR_RANGE_LIMITED, C.COLOR_TRANSFER_SDR, new byte[] {1, 2, 3, 4, 5, 6, 7});
    Bundle params = new Bundle();
    params.putInt(Format.KEY_ASPECT_RATIO_TYPE, 100);
    Format formatToParcel = new Format("id", MimeTypes.VIDEO_MP4, MimeTypes.VIDEO_H264, null,
        1024, 2048, 1920, 1080, 24, 90, 2, projectionData, C.STEREO_MODE_TOP_BOTTOM, colorInfo, 6,
        44100, C.ENCODING_PCM_24BIT, 1001, 1002, 0, "und", Format.NO_VALUE,
        Format.OFFSET_SAMPLE_RELATIVE, INIT_DATA, drmInitData, metadata, params);

    Parcel parcel = Parcel.obtain();
    formatToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    Format formatFromParcel = Format.CREATOR.createFromParcel(parcel);
    assertThat(formatFromParcel).isEqualTo(formatToParcel);
    int aspectRatio = formatFromParcel.params.getInt(Format.KEY_ASPECT_RATIO_TYPE);
    assertThat(aspectRatio).isEqualTo(100);

    parcel.recycle();
  }

}
