/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package spam.blocker.util.pdu.util;


import spam.blocker.util.pdu.pdu.GenericPdu;

public final class PduCacheEntry {
    private final GenericPdu mPdu;
    private final int mMessageBox;
    private final long mThreadId;

    public PduCacheEntry(GenericPdu pdu, int msgBox, long threadId) {
        mPdu = pdu;
        mMessageBox = msgBox;
        mThreadId = threadId;
    }

    public GenericPdu getPdu() {
        return mPdu;
    }

    public int getMessageBox() {
        return mMessageBox;
    }

    public long getThreadId() {
        return mThreadId;
    }
}
