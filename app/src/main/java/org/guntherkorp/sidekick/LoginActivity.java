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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.guntherkorp.sidekick.dialog.HelpFragment;
import org.guntherkorp.sidekick.model.NetworkType;
import org.guntherkorp.sidekick.model.Wallet;
import org.guntherkorp.sidekick.service.BluetoothService;
import org.guntherkorp.sidekick.service.SidekickService;
import org.guntherkorp.sidekick.util.Helper;
import org.guntherkorp.sidekick.util.KeyStoreHelper;
import org.guntherkorp.sidekick.util.MoneroThreadPoolExecutor;
import org.guntherkorp.sidekick.util.ThemeHelper;
import org.guntherkorp.sidekick.util.ZipBackup;
import org.guntherkorp.sidekick.util.ZipRestore;
import org.guntherkorp.sidekick.widget.Toolbar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import timber.log.Timber;

public class LoginActivity extends BaseActivity
        implements LoginFragment.Listener, GenerateFragment.Listener,
        GenerateReviewFragment.Listener, GenerateReviewFragment.AcceptListener, SettingsFragment.Listener, SidekickConnectFragment.Listener, BluetoothFragment.Listener {
    private static final String GENERATE_STACK = "gen";

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

    @Override
    public void setTitle(String title, String subtitle) {
        toolbar.setTitle(title, subtitle);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d("onCreate()");
        ThemeHelper.setPreferred(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        toolbar.setOnButtonListener(type -> {
            switch (type) {
                case Toolbar.BUTTON_BACK:
                    getOnBackPressedDispatcher().onBackPressed();
                    break;
                case Toolbar.BUTTON_CLOSE:
                    finish();
                    break;
                case Toolbar.BUTTON_SETTINGS:
                    startSettingsFragment();
                    break;
                case Toolbar.BUTTON_NONE:
                    break;
                default:
                    Timber.e("Button " + type + "pressed - how can this be?");
            }
        });

        if (savedInstanceState == null) startLoginFragment();
    }

    boolean checkServiceRunning() {
        if (SidekickService.Instance != null) {
            Toast.makeText(this, getString(R.string.service_busy), Toast.LENGTH_SHORT).show();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onWalletSelected(String walletName) {
        promptAndStart(walletName);
        return true;
    }

    @Override
    public void onWalletDetails(final String walletName) {
        Timber.d("details for wallet .%s.", walletName);
        if (checkServiceRunning()) return;
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    final File walletFile = Helper.getWalletFile(LoginActivity.this, walletName);
                    if (Wallet.exists(walletFile)) {
                        Helper.promptPassword(LoginActivity.this, walletName, true, new Helper.PasswordAction() {
                            @Override
                            public void act(String walletName1, String password, boolean fingerprintUsed) {
                                if (checkDevice(walletName1, password))
                                    startDetails(walletFile, password, GenerateReviewFragment.VIEW_TYPE_DETAILS);
                            }

                            @Override
                            public void fail(String walletName) {
                            }
                        });
                    } else { // this cannot really happen as we prefilter choices
                        Timber.e("Wallet missing: %s", walletName);
                        Toast.makeText(LoginActivity.this, getString(R.string.bad_wallet), Toast.LENGTH_SHORT).show();
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    // do nothing
                    break;
            }
        };

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setMessage(getString(R.string.details_alert_message))
                .setPositiveButton(getString(R.string.details_alert_yes), dialogClickListener)
                .setNegativeButton(getString(R.string.details_alert_no), dialogClickListener)
                .show();
    }

    private void renameWallet(String oldName, String newName) {
        File walletFile = Helper.getWalletFile(this, oldName);
        boolean success = renameWallet(walletFile, newName);
        if (success) {
            reloadWalletList();
        } else {
            Toast.makeText(LoginActivity.this, getString(R.string.rename_failed), Toast.LENGTH_LONG).show();
        }
    }

    // copy + delete seems safer than rename because we can rollback easily
    boolean renameWallet(File walletFile, String newName) {
        if (copyWallet(walletFile, new File(walletFile.getParentFile(), newName))) {
            try {
                KeyStoreHelper.copyWalletUserPass(this, walletFile.getName(), newName);
            } catch (KeyStoreHelper.BrokenPasswordStoreException ex) {
                Timber.w(ex);
            }
            deleteWallet(walletFile); // also deletes the keystore entry
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onWalletRename(final String walletName) {
        Timber.d("rename for wallet ." + walletName + ".");
        if (checkServiceRunning()) return;
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.prompt_rename, null);

        AlertDialog.Builder alertDialogBuilder = new MaterialAlertDialogBuilder(this);
        alertDialogBuilder.setView(promptsView);

        final EditText etRename = promptsView.findViewById(R.id.etRename);
        final TextView tvRenameLabel = promptsView.findViewById(R.id.tvRenameLabel);

        tvRenameLabel.setText(getString(R.string.prompt_rename, walletName));

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(getString(R.string.label_ok),
                        (dialog, id) -> {
                            Helper.hideKeyboardAlways(LoginActivity.this);
                            String newName = etRename.getText().toString();
                            renameWallet(walletName, newName);
                        })
                .setNegativeButton(getString(R.string.label_cancel),
                        (dialog, id) -> {
                            Helper.hideKeyboardAlways(LoginActivity.this);
                            dialog.cancel();
                        });

        final AlertDialog dialog = alertDialogBuilder.create();
        Helper.showKeyboard(dialog);

        // accept keyboard "ok"
        etRename.setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) && (event.getAction() == KeyEvent.ACTION_DOWN))
                    || (actionId == EditorInfo.IME_ACTION_DONE)) {
                Helper.hideKeyboardAlways(LoginActivity.this);
                String newName = etRename.getText().toString();
                dialog.cancel();
                renameWallet(walletName, newName);
                return false;
            }
            return false;
        });

        dialog.show();
    }

    private static final int CREATE_BACKUP_INTENT = 4711;
    private static final int RESTORE_BACKUP_INTENT = 4712;
    private ZipBackup zipBackup;

    @Override
    public void onWalletBackup(String walletName) {
        Timber.d("backup for wallet ." + walletName + ".");
        // overwrite any pending backup request
        zipBackup = new ZipBackup(this, walletName);

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, zipBackup.getBackupName());
        startActivityForResult(intent, CREATE_BACKUP_INTENT);
    }

    @Override
    public void onWalletRestore() {
        Timber.d("restore wallet");

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, RESTORE_BACKUP_INTENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_BACKUP_INTENT) {
            if (data == null) {
                // nothing selected
                Toast.makeText(this, getString(R.string.backup_failed), Toast.LENGTH_LONG).show();
                zipBackup = null;
                return;
            }
            try {
                if (zipBackup == null) return; // ignore unsolicited request
                zipBackup.writeTo(data.getData());
                Toast.makeText(this, getString(R.string.backup_success), Toast.LENGTH_SHORT).show();
            } catch (IOException ex) {
                Timber.e(ex);
                Toast.makeText(this, getString(R.string.backup_failed), Toast.LENGTH_LONG).show();
            } finally {
                zipBackup = null;
            }
        } else if (requestCode == RESTORE_BACKUP_INTENT) {
            if (data == null) {
                // nothing selected
                Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_LONG).show();
                return;
            }
            try {
                ZipRestore zipRestore = new ZipRestore(this, data.getData());
                Toast.makeText(this, getString(R.string.menu_restore), Toast.LENGTH_SHORT).show();
                if (zipRestore.restore()) {
                    reloadWalletList();
                } else {
                    Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_LONG).show();
                }
            } catch (IOException ex) {
                Timber.e(ex);
                Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onWalletDelete(final String walletName) {
        Timber.d("delete for wallet ." + walletName + ".");
        if (checkServiceRunning()) return;
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    if (deleteWallet(Helper.getWalletFile(LoginActivity.this, walletName))) {
                        reloadWalletList();
                    } else {
                        Toast.makeText(LoginActivity.this, getString(R.string.delete_failed), Toast.LENGTH_LONG).show();
                    }
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // do nothing
                    break;
            }
        };

        final AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        final AlertDialog confirm = builder.setMessage(getString(R.string.delete_alert_message))
                .setTitle(walletName)
                .setPositiveButton(getString(R.string.delete_alert_yes), dialogClickListener)
                .setNegativeButton(getString(R.string.delete_alert_no), dialogClickListener)
                .setView(View.inflate(builder.getContext(), R.layout.checkbox_confirm, null))
                .show();
        confirm.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        final MaterialCheckBox checkBox = confirm.findViewById(R.id.checkbox);
        assert checkBox != null;
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            confirm.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(isChecked);
        });
    }

    void reloadWalletList() {
        Timber.d("reloadWalletList()");
        try {
            LoginFragment loginFragment = (LoginFragment)
                    getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (loginFragment != null) {
                loginFragment.loadList();
            }
        } catch (ClassCastException ex) {
            Timber.w(ex);
        }
    }

    public void onWalletChangePassword() {//final String walletName, final String walletPassword) {
        try {
            GenerateReviewFragment detailsFragment = (GenerateReviewFragment)
                    getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            assert detailsFragment != null;
            AlertDialog dialog = detailsFragment.createChangePasswordDialog();
            if (dialog != null) {
                Helper.showKeyboard(dialog);
                dialog.show();
            }
        } catch (ClassCastException ex) {
            Timber.w("onWalletChangePassword() called, but no GenerateReviewFragment active");
        }
    }

    @Override
    public void onAddWallet(String type) {
        if (checkServiceRunning()) return;
        startGenerateFragment(type);
    }

    ////////////////////////////////////////
    // LoginFragment.Listener
    ////////////////////////////////////////

    @Override
    public File getStorageRoot() {
        return Helper.getWalletRoot(getApplicationContext());
    }

    ////////////////////////////////////////
    ////////////////////////////////////////

    @Override
    public void showNet() {
        showNet(Wallet.getAppNetworkType());
    }

    private void showNet(NetworkType net) {
        switch (net) {
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
                throw new IllegalStateException("NetworkType unknown: " + net);
        }
    }

    @Override
    protected void onPause() {
        Timber.d("onPause()");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        dismissProgressDialog();
        BluetoothService.Stop();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Timber.d("onResume()");
        // wait for WalletService to finish
        if (SidekickService.Instance != null && (progressDialog == null)) {
            // and show a progress dialog, but only if there isn't one already
            new AsyncWaitForService().execute();
        }
        if (BluetoothService.IsConnected()) closeDeviceOptions();
    }

    private class AsyncWaitForService extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog(R.string.service_progress);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                while ((SidekickService.Instance != null) & !isCancelled()) {
                    Thread.sleep(250);
                }
            } catch (InterruptedException ex) {
                // oh well ...
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if (isDestroyed()) {
                return;
            }
            dismissProgressDialog();
        }
    }

    void startWallet(String walletName, String walletPassword) {
        Timber.d("startSidekickWallet()");
        Intent intent = new Intent(getApplicationContext(), SidekickActivity.class);
        intent.putExtra(SidekickActivity.REQUEST_ID, walletName);
        intent.putExtra(SidekickActivity.REQUEST_PW, walletPassword);
        startActivity(intent);
    }

    void startDetails(File walletFile, String password, String type) {
        Timber.d("startDetails()");
        Bundle b = new Bundle();
        b.putString("path", walletFile.getAbsolutePath());
        b.putString("password", password);
        b.putString("type", type);
        startReviewFragment(b);
    }

    void startLoginFragment() {
        // we set these here because we cannot be certain we have permissions for storage before
        Helper.setMoneroHome(this);
        Helper.initLogger(this);
        Fragment fragment = new LoginFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragment).commit();
        Timber.d("LoginFragment added");
    }

    void startGenerateFragment(String type) {
        Bundle extras = new Bundle();
        extras.putString(GenerateFragment.TYPE, type);
        replaceFragment(new GenerateFragment(), GENERATE_STACK, extras);
        Timber.d("GenerateFragment placed");
    }

    void startReviewFragment(Bundle extras) {
        replaceFragment(new GenerateReviewFragment(), null, extras);
        Timber.d("GenerateReviewFragment placed");
    }

    void startSettingsFragment() {
        replaceFragment(new SettingsFragment(), null, null);
        Timber.d("SettingsFragment placed");
    }

    void startSidekickConnectFragment() {
        replaceFragment(new SidekickConnectFragment(), null, null);
        Timber.d("SidekickConnectFragment placed");
    }

    void replaceFragment(Fragment newFragment, String stackName, Bundle extras) {
        if (extras != null) {
            newFragment.setArguments(extras);
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, newFragment);
        transaction.addToBackStack(stackName);
        transaction.commit();
    }

    void popFragmentStack(String name) {
        getSupportFragmentManager().popBackStack(name, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    //////////////////////////////////////////
    // GenerateFragment.Listener
    //////////////////////////////////////////
    static final String MNEMONIC_LANGUAGE = "English"; // see mnemonics/electrum-words.cpp for more

    private class AsyncCreateWallet extends AsyncTask<Void, Void, Boolean> {
        final String walletName;
        final String walletPassword;
        final WalletCreator walletCreator;

        File newWalletFile;

        AsyncCreateWallet(final String name, final String password,
                          final WalletCreator walletCreator) {
            super();
            this.walletName = name;
            this.walletPassword = password;
            this.walletCreator = walletCreator;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            acquireWakeLock();
            showProgressDialog(R.string.generate_wallet_creating);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // check if the wallet we want to create already exists
            File walletFolder = getStorageRoot();
            if (!walletFolder.isDirectory()) {
                Timber.e("Wallet dir " + walletFolder.getAbsolutePath() + "is not a directory");
                return false;
            }
            File cacheFile = new File(walletFolder, walletName);
            File keysFile = new File(walletFolder, walletName + ".keys");
            File addressFile = new File(walletFolder, walletName + ".address.txt");

            if (cacheFile.exists() || keysFile.exists() || addressFile.exists()) {
                Timber.e("Some wallet files already exist for %s", cacheFile.getAbsolutePath());
                return false;
            }

            newWalletFile = new File(walletFolder, walletName);
            boolean success = walletCreator.createWallet(newWalletFile, walletPassword);
            if (success) {
                return true;
            } else {
                Timber.e("Could not create new wallet in %s", newWalletFile.getAbsolutePath());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            releaseWakeLock(RELEASE_WAKE_LOCK_DELAY);
            if (isDestroyed()) {
                return;
            }
            dismissProgressDialog();
            if (result) {
                startDetails(newWalletFile, walletPassword, GenerateReviewFragment.VIEW_TYPE_ACCEPT);
            } else {
                walletGenerateError();
            }
        }
    }

    public void createWallet(final String name, final String password,
                             final WalletCreator walletCreator) {
        new AsyncCreateWallet(name, password, walletCreator)
                .executeOnExecutor(MoneroThreadPoolExecutor.MONERO_THREAD_POOL_EXECUTOR);
    }

    void walletGenerateError() {
        try {
            GenerateFragment genFragment = (GenerateFragment)
                    getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            assert genFragment != null;
            genFragment.walletGenerateError();
        } catch (ClassCastException ex) {
            Timber.e("walletGenerateError() but not in GenerateFragment");
        }
    }

    public interface WalletCreator {
        boolean createWallet(File aFile, String password);

        Wallet.Device device();
    }

    boolean checkAndCloseWallet(Wallet aWallet) {
        Wallet.Status walletStatus = aWallet.getStatus();
        if (!walletStatus.isOk()) {
            Timber.e(walletStatus.getErrorString());
            toast(walletStatus.getErrorString());
        }
        aWallet.close();
        return walletStatus.isOk();
    }

    @Override
    public void onGenerate(final String name, final String password) {
        createWallet(name, password,
                new WalletCreator() {
                    @Override
                    public Wallet.Device device() {
                        return Wallet.Device.Software;
                    }

                    @Override
                    public boolean createWallet(File aFile, String password) {
                        final long restoreHeight = -1;
                        Wallet newWallet = Wallet.createWallet(aFile, password, MNEMONIC_LANGUAGE, restoreHeight);
                        return checkAndCloseWallet(newWallet);
                    }
                });
    }

    @Override
    public void onGenerate(final String name, final String password,
                           final String seed, final String offset,
                           final long restoreHeight) {
        createWallet(name, password,
                new WalletCreator() {
                    @Override
                    public Wallet.Device device() {
                        return Wallet.Device.Software;
                    }

                    @Override
                    public boolean createWallet(File aFile, String password) {
                        Wallet newWallet = Wallet.recoverWallet(aFile, password, seed, offset, restoreHeight);
                        return checkAndCloseWallet(newWallet);
                    }
                });
    }

    @Override
    public void onGenerate(final String name, final String password,
                           final String address, final String viewKey, final String spendKey,
                           final long restoreHeight) {
        createWallet(name, password,
                new WalletCreator() {
                    @Override
                    public Wallet.Device device() {
                        return Wallet.Device.Software;
                    }

                    @Override
                    public boolean createWallet(File aFile, String password) {
                        Wallet newWallet = Wallet
                                .recoverWalletFromKeys(aFile, password, MNEMONIC_LANGUAGE, restoreHeight, address, viewKey, spendKey);
                        return checkAndCloseWallet(newWallet);
                    }
                });
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    private void toast(final int msgId) {
        runOnUiThread(() -> Toast.makeText(LoginActivity.this, getString(msgId), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onAccept(final String name, final String password) {
        final File walletFolder = getStorageRoot();
        final File walletFile = new File(walletFolder, name);
        Timber.d("New Wallet %s", walletFile.getAbsolutePath());
        walletFile.delete(); // when recovering wallets, the cache seems corrupt - so remove it

        popFragmentStack(GENERATE_STACK);
        Toast.makeText(LoginActivity.this,
                getString(R.string.generate_wallet_created), Toast.LENGTH_SHORT).show();
    }

    boolean walletExists(File walletFile) {
        final File dir = walletFile.getParentFile();
        final String name = walletFile.getName();
        return new File(dir, name + ".keys").exists();
    }

    boolean copyWallet(File srcWallet, File dstWallet) {
        if (walletExists(dstWallet)) return false;
        boolean success = false;
        File srcDir = srcWallet.getParentFile();
        String srcName = srcWallet.getName();
        File dstDir = dstWallet.getParentFile();
        String dstName = dstWallet.getName();
        try {
            copyFile(new File(srcDir, srcName + ".keys"), new File(dstDir, dstName + ".keys"));
            success = true;
        } catch (IOException ex) {
            Timber.e("wallet copy failed: %s", ex.getMessage());
            // try to rollback
            deleteWallet(dstWallet);
        }
        return success;
    }

    // do our best to delete as much as possible of the wallet files
    boolean deleteWallet(File walletFile) {
        Timber.d("deleteWallet %s", walletFile.getAbsolutePath());
        File dir = walletFile.getParentFile();
        String name = walletFile.getName();
        boolean success = true;
        File cacheFile = new File(dir, name);
        if (cacheFile.exists()) {
            success = cacheFile.delete();
        }
        success = new File(dir, name + ".keys").delete() && success;
        File addressFile = new File(dir, name + ".address.txt");
        if (addressFile.exists()) {
            success = addressFile.delete() && success;
        }
        Timber.d("deleteWallet is %s", success);
        KeyStoreHelper.removeWalletUserPass(this, walletFile.getName());
        return success;
    }

    void copyFile(File src, File dst) throws IOException {
        try (FileChannel inChannel = new FileInputStream(src).getChannel();
             FileChannel outChannel = new FileOutputStream(dst).getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == R.id.action_create_help_new) {
            HelpFragment.display(getSupportFragmentManager(), R.string.help_create_new);
            return true;
        } else if (id == R.id.action_create_help_keys) {
            HelpFragment.display(getSupportFragmentManager(), R.string.help_create_keys);
            return true;
        } else if (id == R.id.action_create_help_view) {
            HelpFragment.display(getSupportFragmentManager(), R.string.help_create_view);
            return true;
        } else if (id == R.id.action_create_help_seed) {
            HelpFragment.display(getSupportFragmentManager(), R.string.help_create_seed);
            return true;
        } else if (id == R.id.action_details_help) {
            HelpFragment.display(getSupportFragmentManager(), R.string.help_details);
            return true;
        } else if (id == R.id.action_details_changepw) {
            onWalletChangePassword();
            return true;
        } else if (id == R.id.action_help_list) {
            HelpFragment.display(getSupportFragmentManager(), R.string.help_list);
            return true;
        } else if (id == R.id.action_ledger_seed) {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (f instanceof GenerateFragment) {
                ((GenerateFragment) f).convertLedgerSeed();
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    void btPermissionGranted() {
        startSidekickConnectFragment();
    }

    private boolean checkDevice(String walletName, String password) {
        final String keyPath = new File(Helper.getWalletRoot(this), walletName + ".keys").getAbsolutePath();
        // make sure the wallet os local
        return Wallet.queryWalletDevice(keyPath, password) == Wallet.Device.Software;
    }

    void promptAndStart(String walletName) {
        File walletFile = Helper.getWalletFile(this, walletName);
        if (Wallet.exists(walletFile)) {
            Helper.promptPassword(LoginActivity.this, walletName, false,
                    new Helper.PasswordAction() {
                        @Override
                        public void act(String walletName, String password, boolean fingerprintUsed) {
                            if (checkDevice(walletName, password))
                                startWallet(walletName, password);
                        }

                        @Override
                        public void fail(String walletName) {
                        }

                    });
        } else { // this cannot really happen as we prefilter choices
            Toast.makeText(this, getString(R.string.bad_wallet), Toast.LENGTH_SHORT).show();
        }
    }

    public void closeDeviceOptions() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (f instanceof GenerateFragment) {
            getOnBackPressedDispatcher().onBackPressed();
        } else if (f instanceof LoginFragment) {
            if (((LoginFragment) f).isFabOpen()) {
                ((LoginFragment) f).animateFAB();
            }
        }
    }

    @Override
    public void onDeviceConnected(String connectedDeviceName) {
        Timber.d("onDeviceConnected: %s", connectedDeviceName);
        try {
            SidekickConnectFragment f = (SidekickConnectFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            assert f != null;
            f.allowClick();
        } catch (ClassCastException ex) {
            // ignore it
        }
    }

    @Override
    public void abort(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        onBackPressed();
    }

    @Override
    public void onReceive(int command) {
        Timber.e("this should not be");
    }
}
