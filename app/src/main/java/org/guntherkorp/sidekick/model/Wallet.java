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

import org.guntherkorp.sidekick.Application;
import org.guntherkorp.sidekick.data.WalletInfo;
import org.guntherkorp.sidekick.util.RestoreHeight;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import timber.log.Timber;

public class Wallet {
    static {
        System.loadLibrary("monerujo");
    }

    static public class Status {
        Status(int status, String errorString) {
            this.status = StatusEnum.values()[status];
            this.errorString = errorString;
        }

        @Getter
        final private StatusEnum status;
        @Getter
        final private String errorString;

        public boolean isOk() {
            return (getStatus() == StatusEnum.Status_Ok);
        }

        @Override
        @NonNull
        public String toString() {
            return "Wallet.Status: " + status + "/" + errorString;
        }
    }

    public static NetworkType getAppNetworkType() {
        return Application.getNetworkType();
    }

    static public boolean exists(File aFile) {
        return walletExists(aFile.getAbsolutePath());
    }

    static public native boolean walletExists(String path);

    static public Wallet.Device queryWalletDevice(String keys_file_name, String password) {
        int device = queryWalletDeviceJ(keys_file_name, password);
        return Wallet.Device.values()[device + 1]; // mapping is monero+1=android
    }

    static private native int queryWalletDeviceJ(String keys_file_name, String password);

    static public boolean verifyWalletPasswordOnly(String keys_file_name, String password) {
        return queryWalletDeviceJ(keys_file_name, password) >= 0;
    }

    static public Wallet createWallet(File aFile, String password, String language, long height) {
        long walletHandle = createWalletJ(aFile.getAbsolutePath(), password, language, getAppNetworkType().getValue());
        Wallet wallet = new Wallet(walletHandle);
        if (wallet.getStatus().isOk() && (wallet.getNetworkType() == NetworkType.NetworkType_Mainnet)) {
            // (Re-)Estimate restore height based on what we know
            final long oldHeight = wallet.getRestoreHeight();
            // Go back 4 days if we don't have a precise restore height
            Calendar restoreDate = Calendar.getInstance();
            restoreDate.add(Calendar.DAY_OF_MONTH, -4);
            final long restoreHeight =
                    (height > -1) ? height : RestoreHeight.getInstance().getHeight(restoreDate.getTime());
            wallet.setRestoreHeight(restoreHeight);
            Timber.d("Changed Restore Height from %d to %d", oldHeight, wallet.getRestoreHeight());
            wallet.setPassword(password); // this rewrites the keys file (which contains the restore height)
        } else
            Timber.e(wallet.getStatus().toString());
        return wallet;
    }

    static private native long createWalletJ(String path, String password, String language, int networkType);

    static public Wallet open(String path, String password) {
        long walletHandle = openWalletJ(path, password, getAppNetworkType().getValue());
        return new Wallet(walletHandle);
    }

    static private native long openWalletJ(String path, String password, int networkType);

    static public Wallet recoverWallet(File aFile, String password,
                                       String mnemonic, String offset,
                                       long restoreHeight) {
        long walletHandle = recoveryWalletJ(aFile.getAbsolutePath(), password,
                mnemonic, offset,
                getAppNetworkType().getValue(), restoreHeight);
        return new Wallet(walletHandle);
    }

    static private native long recoveryWalletJ(String path, String password,
                                               String mnemonic, String offset,
                                               int networkType, long restoreHeight);

    static public Wallet recoverWalletFromKeys(File aFile, String password, String language, long restoreHeight,
                                               String address, String viewKey, String spendKey) {
        long walletHandle = createWalletFromKeysJ(aFile.getAbsolutePath(), password,
                language, getAppNetworkType().getValue(), restoreHeight,
                address, viewKey, spendKey);
        return new Wallet(walletHandle);
    }

    static private native long createWalletFromKeysJ(String path, String password,
                                                     String language,
                                                     int networkType,
                                                     long restoreHeight,
                                                     String addressString,
                                                     String viewKeyString,
                                                     String spendKeyString);

    static public native boolean close(Wallet wallet);

    public static boolean isAddressValid(String address) {
        return isAddressValid(address, getAppNetworkType().getValue());
    }

    public static native boolean isAddressValid(String address, int networkType);

    static public List<WalletInfo> getWallets(File path) {
        List<WalletInfo> wallets = new ArrayList<>();
        Timber.d("Scanning: %s", path.getAbsolutePath());
        File[] found = path.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".keys");
            }
        });
        for (int i = 0; i < found.length; i++) {
            String filename = found[i].getName();
            File f = new File(found[i].getParent(), filename.substring(0, filename.length() - 5)); // 5 is length of ".keys"+1
            wallets.add(new WalletInfo(f));
        }
        return wallets;
    }


    public String getName() {
        return new File(getPath()).getName();
    }

    private long handle = 0;

    Wallet(long handle) {
        this.handle = handle;
    }

    @RequiredArgsConstructor
    @Getter
    public enum Device {
        Undefined(0, 0),
        Software(50, 200),
        Ledger(5, 20),
        Trezor(5, 20),
        Sidekick(5, 20);
        private final int accountLookahead;
        private final int subaddressLookahead;
    }

    public enum StatusEnum {
        Status_Ok, Status_Error, Status_Critical
    }

    public native String getSeed(String offset);

    public native String getSeedLanguage();

    public Status getStatus() {
        return statusWithErrorString();
    }

    private native Status statusWithErrorString();

    public native synchronized boolean setPassword(String password);

    public native String getPath();

    public NetworkType getNetworkType() {
        return NetworkType.fromInteger(nettype());
    }

    public native int nettype();

    public native String getSecretViewKey();

    public native String getSecretSpendKey();

    public boolean store() {
        return store("");
    }

    public native synchronized boolean store(String path);

    public boolean close() {
        return close(this);
    }

    public native String getFilename();

    public native boolean isWatchOnly();

    public Wallet.Device getDeviceType() {
        int device = getDeviceTypeJ();
        return Wallet.Device.values()[device + 1]; // mapping is monero+1=android
    }

    public native String getAddress(int accountIndex, int addressIndex);

    public native long getRestoreHeight();

    public native void setRestoreHeight(long height);

    private native int getDeviceTypeJ();
}
