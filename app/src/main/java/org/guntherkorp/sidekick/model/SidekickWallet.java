/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.guntherkorp.sidekick.model;

import androidx.annotation.NonNull;

import java.io.File;

import lombok.AllArgsConstructor;
import lombok.Getter;
import timber.log.Timber;

@AllArgsConstructor
public class SidekickWallet {
    static {
        System.loadLibrary("monerujo");
    }

    public enum Status {
        OK,
        ERROR,
        CRITICAL;

        public boolean isOk() {
            return this == OK;
        }
    }

    private long handle = 0;

    @Getter
    final private String name;

    public synchronized void invalidate() {
        if (handle != 0) {
            destroy(handle);
            handle = 0;
        }
    }

    protected void finalize() {
        invalidate();
        Timber.d("SidekickWallet destroyed");
    }

    private native void destroy(long handle);

    public native byte[] call(int id, byte[] request);

    public native void reset();

    static public SidekickWallet loadFromWallet(String path, String password) {
        long handle = loadFromWalletJ(path, password, Wallet.getAppNetworkType().getValue());
        return new SidekickWallet(handle, new File(path).getName());
    }

    static private native long loadFromWalletJ(String path, String password, int networkType);

    public @NonNull Status getStatus() {
        return Status.values()[getStatusJ()];
    }

    public native int getStatusJ();

}
