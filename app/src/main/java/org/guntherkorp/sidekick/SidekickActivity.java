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

package org.guntherkorp.sidekick;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.guntherkorp.sidekick.model.SidekickWallet;
import org.guntherkorp.sidekick.model.Wallet;
import org.guntherkorp.sidekick.service.BluetoothService;
import org.guntherkorp.sidekick.service.SidekickService;
import org.guntherkorp.sidekick.util.Helper;
import org.guntherkorp.sidekick.util.ThemeHelper;
import org.guntherkorp.sidekick.widget.Toolbar;

import timber.log.Timber;

public class SidekickActivity extends BaseActivity implements SidekickFragment.Listener,
        SidekickService.Observer, BluetoothFragment.Listener {

    public static final String REQUEST_ID = "id";
    public static final String REQUEST_PW = "pw";

    private Toolbar toolbar;

    @Override
    public void setToolbarButton(int type) {
        toolbar.setButton(type);
    }

    @Override
    public void setTitle(String title) {
        toolbar.setTitle(title);
    }

    @Override
    public void setSubtitle(String subtitle) {
        toolbar.setSubtitle(subtitle);
    }

    private SidekickFragment getSidekickFragment() {
        return (SidekickFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_sidekick);
    }

    private BluetoothFragment getBluetoothFragment() {
        return (BluetoothFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_bluetooth);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.d("onStart()");
    }

    @Override
    void btPermissionGranted() {
        startWalletService();
    }

    private void startWalletService() {
        if (mIsBound) return;
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String walletId = extras.getString(REQUEST_ID);
            connectWalletService(walletId, extras.getString(REQUEST_PW));
            acquireWakeLock(); // TODO: should this be?
        } else {
            finish();
        }
    }

    private void stopWalletService() {
        disconnectWalletService();
        if (toolbar != null)
            toolbar.setTitle(null);
        releaseWakeLock();
    }

    @Override
    protected void onStop() {
        Timber.d("onStop()");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy()");
        stopWalletService();
        BluetoothService.GetInstance().stop();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        ThemeHelper.setPreferred(this);
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            // activity restarted
            // we don't want that - finish it and fall back to previous activity
            finish();
            return;
        }

        setContentView(R.layout.activity_sidekick);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        toolbar.setOnButtonListener(type -> {
            switch (type) {
                case Toolbar.BUTTON_BACK:
                    onBackPressed();
                    break;
                case Toolbar.BUTTON_CANCEL:
                    Helper.hideKeyboard(SidekickActivity.this);
                    SidekickActivity.super.onBackPressed();
                    break;
                case Toolbar.BUTTON_CLOSE:
                    finish();
                    break;
                case Toolbar.BUTTON_NONE:
                default:
                    Timber.e("Button " + type + "pressed - how can this be?");
            }
        });

        showNet();

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_sidekick, new SidekickFragment(), SidekickFragment.class.getName())
                .add(R.id.fragment_bluetooth, new BluetoothFragment(BluetoothFragment.Mode.SERVER), BluetoothFragment.class.getName())
                .commit();
        Timber.d("fragments added");
        Timber.d("onCreate() done.");
    }

    public void showNet() {
        switch (Wallet.getAppNetworkType()) {
            case NetworkType_Mainnet:
                toolbar.setSubtitle(null);
                toolbar.setBackgroundResource(R.drawable.backgound_toolbar_mainnet);
                break;
            case NetworkType_Testnet:
                toolbar.setSubtitle(getString(R.string.connect_testnet));
                toolbar.setBackgroundResource(ThemeHelper.getThemedResourceId(this, androidx.appcompat.R.attr.colorPrimaryDark));
                break;
            case NetworkType_Stagenet:
                toolbar.setSubtitle(getString(R.string.connect_stagenet));
                toolbar.setBackgroundResource(ThemeHelper.getThemedResourceId(this, androidx.appcompat.R.attr.colorPrimaryDark));
                break;
            default:
                throw new IllegalStateException("Unsupported Network: " + Wallet.getAppNetworkType());
        }
    }

    @Override
    public SidekickWallet getWallet() {
        if (mBoundService == null) throw new IllegalStateException("SidekickService not bound.");
        return mBoundService.getWallet();
    }

    private SidekickService mBoundService = null;
    private boolean mIsBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((SidekickService.WalletServiceBinder) service).getService();
            mBoundService.setObserver(SidekickActivity.this);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                String walletId = extras.getString(REQUEST_ID);
                if (walletId != null) {
                    setTitle(walletId);
                }
            }
            Timber.d("CONNECTED");
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            setSubtitle(getString(R.string.status_wallet_disconnected));
            Timber.d("DISCONNECTED");
        }
    };

    void connectWalletService(String walletName, String walletPassword) {
        synchronized (this) {
            if (mIsBound) return;
            mIsBound = true;
        }
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        Intent intent = new Intent(getApplicationContext(), SidekickService.class);
        intent.putExtra(SidekickService.REQUEST_WALLET, walletName);
        intent.putExtra(SidekickService.REQUEST, SidekickService.REQUEST_CMD_LOAD);
        intent.putExtra(SidekickService.REQUEST_CMD_LOAD_PW, walletPassword);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Timber.d("BOUND");
    }

    void disconnectWalletService() {
        if (mIsBound) {
            // Detach our existing connection.
            mBoundService.setObserver(null);
            unbindService(mConnection);
            mIsBound = false;
            Timber.d("UNBOUND");
        }
    }

    @Override
    protected void onPause() {
        Timber.d("onPause()");
        lock();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.d("onResume()");
        if (locked) {
            unlock();
        }
        checkBtPermissions();
    }

///////////////////////////
// WalletService.Observer
///////////////////////////

    @Override
    public void onEvent(Event event) {
        onEvent(event, null);
    }

    @Override
    public void onEvent(Event event, String msg) {
        Timber.d("EVENT %s(%s)", event, msg);
        switch (event) {
            case LOADED:
                runOnUiThread(() -> {
                    setSubtitle(getString(R.string.sidekick_ready));
                    getBluetoothFragment().start();
                    getSidekickFragment().setRestoreheight(msg);
                });
                break;
            case LOAD_FAILED:
                runOnUiThread(() -> abort(getString(R.string.sidekick_failed)));
                break;
        }
    }

    @Override
    public void userConfirmation(SidekickService.Confirmation confirmation) {
        runOnUiThread(() -> {
            getSidekickFragment().confirmTransfers(confirmation);
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Helper.hideKeyboard(this);
    }

    // BluetoothFragment.Listener
    @Override
    public void onDeviceConnected(String connectedDeviceName) {
        Timber.d("DEVICE=%s", connectedDeviceName);
        if (connectedDeviceName != null)
            setSubtitle("Connected");
        else
            setSubtitle("Ready"); //TODO strings.xml
    }

    @Override
    public void abort(String message) {
        Timber.d("aborting because %s", message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onReceive(int commandId) {
        getSidekickFragment().flash(commandId);
    }

    private boolean locked = false;

    private void unlock() {
        Helper.promptPassword(SidekickActivity.this, getWallet().getName(), false, new Helper.PasswordAction() {
            @Override
            public void act(String walletName, String password, boolean fingerprintUsed) {
                popFragmentStack();
                locked = false;
                Timber.d("locked: %b", locked);
            }

            @Override
            public void fail(String walletName) {
                finish();
            }
        });
    }

    private void lock() {
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.preferred_lock), false)) {
            replaceFragment(new LockFragment());
            locked = true;
        }
        Timber.d("locked %b", locked);
    }

    void popFragmentStack() {
        getSupportFragmentManager().popBackStack();
    }

    void replaceFragment(Fragment newFragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_sidekick, newFragment)
                .addToBackStack(null)
                .commit();
    }
}
