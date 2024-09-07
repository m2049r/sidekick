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

package org.guntherkorp.sidekick.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.guntherkorp.sidekick.R;
import org.guntherkorp.sidekick.SidekickActivity;
import org.guntherkorp.sidekick.model.SidekickWallet;
import org.guntherkorp.sidekick.model.Wallet;
import org.guntherkorp.sidekick.util.Helper;
import org.guntherkorp.sidekick.util.LocaleHelper;

import java.util.concurrent.CountDownLatch;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import timber.log.Timber;

// a foreground service which keeps the bluetooth comms running and
// exchanges messages and sends updates to the SidekickActivity for UI updates
public class SidekickService extends Service {
    public static SidekickService Instance = null;

    final static int NOTIFICATION_ID = 2050;
    final static String CHANNEL_ID = "g_service";

    public static final String REQUEST_WALLET = "wallet";
    public static final String REQUEST = "request";

    public static final String REQUEST_CMD_LOAD = "load";
    public static final String REQUEST_CMD_LOAD_PW = "walletPassword";

    public static final int START_SERVICE = 1;
    public static final int STOP_SERVICE = 2;

    /////////////////////////////////////////////
    // communication back to client (activity) //
    /////////////////////////////////////////////
    // NB: This allows for only one observer, i.e. only a single activity bound here

    private Observer observer = null; //TODO use a handler here

    public void setObserver(Observer anObserver) {
        observer = anObserver;
        Timber.d("setObserver %s", observer);
    }

    public interface Observer {
        enum Event {
            LOADING, LOADED, LOAD_FAILED, UNLOADED, CALL, CONFIRM
        }

        void onEvent(Event event);

        void onEvent(Event event, String msg);

        void userConfirmation(Confirmation confirmation);
    }

    private void showEvent(Observer.Event event) {
        if (observer != null) {
            observer.onEvent(event);
        }
    }

    private void showEvent(Observer.Event event, String msg) {
        if (observer != null) {
            observer.onEvent(event, msg);
        }
    }

    private void userConfirmation(Confirmation confirmation) {
        if (observer != null) {
            observer.userConfirmation(confirmation);
        }
    }

    @Getter
    SidekickWallet wallet = null;

    private void invalidateWallet() {
        setInstance(null);
        if (wallet != null) { // we already have one
            wallet.invalidate();
            wallet = null;
        }
    }
    /////////////////////////////////////////////
    /////////////////////////////////////////////

    private SidekickService.ServiceHandler mServiceHandler;

    private boolean errorState = false;

    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Timber.d("Handling %s", msg.arg2);
            if (errorState) {
                Timber.i("In error state.");
                // also, we have already stopped ourselves
                return;
            }
            switch (msg.arg2) {
                case START_SERVICE: {
                    final Bundle extras = msg.getData();
                    final String cmd = extras.getString(REQUEST, null);
                    if (REQUEST_CMD_LOAD.equals(cmd)) {
                        invalidateWallet();
                        String walletId = extras.getString(REQUEST_WALLET, null);
                        String walletPw = extras.getString(REQUEST_CMD_LOAD_PW, null);
                        Timber.d("LOAD wallet %s", walletId);
                        if (walletId != null) {
                            showEvent(Observer.Event.LOADING);
                            Timber.d("start()...");
                            start(walletId, walletPw);
                            Timber.d("...start()");
                            if ((wallet == null) || !wallet.getStatus().isOk()) {
                                errorState = true;
                                stop();
                                break;
                            }
                            startCommHandler();
                            setInstance(SidekickService.this);
                        }
                    }
                }
                break;
                case STOP_SERVICE:
                    stop();
                    break;
                default:
                    Timber.w("UNKNOWN message %s", msg.arg2);
            }
        }
    }

    private static synchronized void setInstance(SidekickService service) {
        Instance = service;
    }

    @Override
    public void onCreate() {
        // We are using a HandlerThread and a Looper to avoid loading and closing concurrency
        final MoneroHandlerThread thread = new MoneroHandlerThread("SidekickService", Process.THREAD_PRIORITY_DEFAULT);
        thread.start();
        mServiceHandler = new SidekickService.ServiceHandler(thread.getLooper());
        Timber.d("Service created");
    }

    @Override
    public void onDestroy() {
        Timber.d("onDestroy()");
    }

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(LocaleHelper.setPreferredLocale(context));
    }

    public class WalletServiceBinder extends Binder {
        public SidekickService getService() {
            return SidekickService.this;
        }
    }

    private final IBinder mBinder = new WalletServiceBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Timber.d("onStartCommand()");
        Message msg = mServiceHandler.obtainMessage();
        msg.arg2 = START_SERVICE;
        if (intent != null) {
            msg.setData(intent.getExtras());
            mServiceHandler.sendMessage(msg);
            return START_STICKY;
        } else {
            // process restart - don't do anything - let system kill it again
            stop();
            return START_NOT_STICKY;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Very first client binds
        Timber.d("onBind()");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Timber.d("onUnbind()");
        // All clients have unbound with unbindService()
        Message msg = mServiceHandler.obtainMessage();
        msg.arg2 = STOP_SERVICE;
        mServiceHandler.sendMessage(msg);
        Timber.d("onUnbind() message sent");
        return true; // true is important so that onUnbind is also called next time
    }

    private void start(String walletName, String walletPassword) {
        Timber.d("start()");
        startNotfication();
        Timber.d("openWallet...");
        wallet = openWallet(walletName, walletPassword);
        Timber.d("...openWallet %s", wallet);
        if (wallet != null)
            showEvent(Observer.Event.LOADED, Long.toString(wallet.getRestoreheight()));
    }

    public void stop() {
        Timber.d("stop()");
        setObserver(null); // in case it was not reset already
        Timber.d("stop() closing");
        invalidateWallet();
        showEvent(Observer.Event.UNLOADED);
        Timber.d("stop() closed");
        stopForeground(true);
        stopSelf();
    }

    private SidekickWallet openWallet(String walletName, String walletPassword) {
        String path = Helper.getWalletFile(getApplicationContext(), walletName).getAbsolutePath();
        SidekickWallet wallet = null;
        if (Wallet.walletExists(path)) {
            Timber.d("open wallet %s", path);
            Wallet.Device device = Wallet.queryWalletDevice(path + ".keys", walletPassword);
            Timber.d("device is %s", device.toString());
            if (device != Wallet.Device.Software) {
                showEvent(Observer.Event.LOAD_FAILED, "Not a regular wallet"); //TODO strings.xml
                return null;
            }
            try {
                wallet = SidekickWallet.loadFromWallet(path, walletPassword);
            } catch (IllegalStateException ex) {
                Timber.d(ex);
                showEvent(Observer.Event.LOAD_FAILED); //TODO strings.xml
                return null;
            }
            Timber.d("wallet opened");
        }
        return wallet;
    }

    private void startNotfication() {
        Intent notificationIntent = new Intent(this, SidekickActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? createNotificationChannel() : "";
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.sidekick)) //TODO rename this
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_logo_sidekick)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.service_description),
                NotificationManager.IMPORTANCE_LOW);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
        return CHANNEL_ID;
    }

    // A handler for communicating to the Sidekick device
    // it serializes the requests
    // TODO timeout certain calls, add interrupt and stuff like that
    // TODO when does this end?
    private static final class CommHandler extends Handler {
        SidekickService service;

        CommHandler(Looper looper, SidekickService service) {
            super(looper);
            this.service = service;
        }

        @Override
        public void handleMessage(Message msg) {
            Timber.d("Comm Handling 0x%x", msg.what);
            if (service.errorState) {
                Timber.i("In error state.");
                // also, we have already stopped ourselves
                return;
            }
            final byte[] result = service.getWallet().call(msg.what, (byte[]) msg.obj);
            BluetoothService.Write(result); // TODO check concurrency
        }
    }

    @Getter
    private SidekickService.CommHandler commHandler;

    private synchronized void startCommHandler() {
        if (commHandler != null) return; // already started
        final MoneroHandlerThread thread = new MoneroHandlerThread("SidekickService", Process.THREAD_PRIORITY_DEFAULT);
        thread.start();
        commHandler = new SidekickService.CommHandler(thread.getLooper(), this);
        Timber.d("CommHandler started");
        BluetoothService.Instance.setCommHandler(commHandler);
    }

    @RequiredArgsConstructor
    @Getter
    static public class Confirmation {
        final private String transfers;
        private boolean accepted = false;
        final private CountDownLatch signal = new CountDownLatch(1);

        public void await() throws InterruptedException {
            signal.await();
        }

        public void accept() {
            accepted = true;
            signal.countDown();
        }

        public void deny() {
            accepted = false;
            signal.countDown();
        }
    }

    public static boolean ConfirmTransfers(String transfers) {
        Timber.e("Transfers= |%s|", transfers);
        final Confirmation confirmation = new Confirmation(transfers);
        synchronized (SidekickService.class) {
            if (Instance != null) { // TODO deal with this becoming null
                Instance.userConfirmation(confirmation);
            }
        }
        try {
            confirmation.await();
        } catch (InterruptedException ex) {
            return false;
        }
        return confirmation.isAccepted();
    }
}
