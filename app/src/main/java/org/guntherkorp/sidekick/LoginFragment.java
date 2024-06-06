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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.guntherkorp.sidekick.data.WalletInfo;
import org.guntherkorp.sidekick.layout.WalletInfoAdapter;
import org.guntherkorp.sidekick.model.Wallet;
import org.guntherkorp.sidekick.util.Helper;
import org.guntherkorp.sidekick.util.KeyStoreHelper;
import org.guntherkorp.sidekick.widget.Toolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import timber.log.Timber;

public class LoginFragment extends Fragment implements WalletInfoAdapter.OnInteractionListener,
        View.OnClickListener {

    private WalletInfoAdapter adapter;

    private final List<WalletInfo> walletList = new ArrayList<>();

    private View tvGuntherSays;
    private ImageView ivGunther;
    private View tvNetWarning;

    private Listener activityCallback;

    // Container Activity must implement this interface
    public interface Listener {
        File getStorageRoot();

        boolean onWalletSelected(String wallet);

        void onWalletDetails(String wallet);

        void onWalletRename(String name);

        void onWalletBackup(String name);

        void onWalletRestore();

        void onWalletDelete(String walletName);

        void onAddWallet(String type);

        void showNet();

        void setToolbarButton(int type);

        void setTitle(String title);

        boolean isNetworkAvailable();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.activityCallback = (Listener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement Listener");
        }
    }

    @Override
    public void onPause() {
        Timber.d("onPause()");
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        activityCallback.setTitle(null);
        activityCallback.setToolbarButton(Toolbar.BUTTON_SETTINGS);
        activityCallback.showNet();
        sidekickMode();
    }

    private void sidekickMode() {
        if (activityCallback.isNetworkAvailable())
            tvNetWarning.setVisibility(View.VISIBLE);
    }

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            animateFAB();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.d("onCreateView");
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        tvGuntherSays = view.findViewById(R.id.tvGuntherSays);
        ivGunther = view.findViewById(R.id.ivGunther);
        fabScreen = view.findViewById(R.id.fabScreen);
        fab = view.findViewById(R.id.fab);
        fabNew = view.findViewById(R.id.fabNew);
        fabView = view.findViewById(R.id.fabView);
        fabKey = view.findViewById(R.id.fabKey);
        fabSeed = view.findViewById(R.id.fabSeed);
        fabImport = view.findViewById(R.id.fabImport);

        fabNewL = view.findViewById(R.id.fabNewL);
        fabViewL = view.findViewById(R.id.fabViewL);
        fabKeyL = view.findViewById(R.id.fabKeyL);
        fabSeedL = view.findViewById(R.id.fabSeedL);
        fabImportL = view.findViewById(R.id.fabImportL);

        fab_pulse = AnimationUtils.loadAnimation(getContext(), R.anim.fab_pulse);
        fab_open_screen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open_screen);
        fab_close_screen = AnimationUtils.loadAnimation(getContext(), R.anim.fab_close_screen);
        fab_open = AnimationUtils.loadAnimation(getContext(), R.anim.fab_open);
        fab_close = AnimationUtils.loadAnimation(getContext(), R.anim.fab_close);
        rotate_forward = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_forward);
        rotate_backward = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_backward);
        fab.setOnClickListener(this);
        fabNew.setOnClickListener(this);
        fabView.setOnClickListener(this);
        fabKey.setOnClickListener(this);
        fabSeed.setOnClickListener(this);
        fabImport.setOnClickListener(this);
        fabScreen.setOnClickListener(this);

        RecyclerView recyclerView = view.findViewById(R.id.list);
        registerForContextMenu(recyclerView);
        this.adapter = new WalletInfoAdapter(getActivity(), this);
        recyclerView.setAdapter(adapter);

        tvNetWarning = view.findViewById(R.id.tvNetWarning);
        tvNetWarning.setVisibility(View.GONE);

        Helper.hideKeyboard(getActivity());

        loadList();

        return view;
    }

    // Callbacks from WalletInfoAdapter

    // Wallet touched
    @Override
    public void onInteraction(final View view, final WalletInfo infoItem) {
        activityCallback.onWalletSelected(infoItem.getName());
    }

    @Override
    public boolean onContextInteraction(MenuItem item, WalletInfo listItem) {
        final int id = item.getItemId();
        if (id == R.id.action_info) {
            showInfo(listItem.getName());
        } else if (id == R.id.action_rename) {
            activityCallback.onWalletRename(listItem.getName());
        } else if (id == R.id.action_backup) {
            activityCallback.onWalletBackup(listItem.getName());
        } else if (id == R.id.action_archive) {
            activityCallback.onWalletDelete(listItem.getName());
        } else {
            return super.onContextItemSelected(item);
        }
        return true;
    }

    public void loadList() {
        Timber.d("loadList()");
        walletList.clear();
        walletList.addAll(Wallet.getWallets(activityCallback.getStorageRoot()));
        adapter.setInfos(walletList);

        // deal with Gunther & FAB animation
        if (walletList.isEmpty()) {
            fab.startAnimation(fab_pulse);
            if (ivGunther.getDrawable() == null) {
                ivGunther.setImageResource(R.drawable.ic_emptygunther);
                tvGuntherSays.setVisibility(View.VISIBLE);
            }
        } else {
            fab.clearAnimation();
            if (ivGunther.getDrawable() != null) {
                ivGunther.setImageDrawable(null);
            }
            tvGuntherSays.setVisibility(View.GONE);
        }

        // remove information of non-existent wallet
        Set<String> removedWallets = requireActivity()
                .getSharedPreferences(KeyStoreHelper.SecurityConstants.WALLET_PASS_PREFS_NAME, Context.MODE_PRIVATE)
                .getAll().keySet();
        for (WalletInfo s : walletList) {
            removedWallets.remove(s.getName());
        }
        for (String name : removedWallets) {
            KeyStoreHelper.removeWalletUserPass(getActivity(), name);
        }
    }

    private void showInfo(@NonNull String name) {
        activityCallback.onWalletDetails(name);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.list_menu_sidekick, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Getter
    private boolean fabOpen = false;
    private FloatingActionButton fab, fabNew, fabView, fabKey, fabSeed, fabImport;
    private RelativeLayout fabScreen;
    private RelativeLayout fabNewL, fabViewL, fabKeyL, fabSeedL, fabImportL;
    private Animation fab_open, fab_close, rotate_forward, rotate_backward, fab_open_screen, fab_close_screen;
    private Animation fab_pulse;

    private void setFabOpen(boolean value) {
        fabOpen = value;
        onBackPressedCallback.setEnabled(value);
    }

    public void animateFAB() {
        if (isFabOpen()) { // close the fab
            fabScreen.setClickable(false);
            fabScreen.startAnimation(fab_close_screen);
            fab.startAnimation(rotate_backward);

            fabNewL.startAnimation(fab_close);
            fabNew.setClickable(false);
            fabViewL.startAnimation(fab_close);
            fabView.setClickable(false);
            fabKeyL.startAnimation(fab_close);
            fabKey.setClickable(false);
            fabSeedL.startAnimation(fab_close);
            fabSeed.setClickable(false);
            fabImportL.startAnimation(fab_close);
            fabImport.setClickable(false);

            setFabOpen(false);
        } else { // open the fab
            fabScreen.setClickable(true);
            fabScreen.startAnimation(fab_open_screen);
            fab.startAnimation(rotate_forward);

            fabNewL.setVisibility(View.VISIBLE);
            fabViewL.setVisibility(View.VISIBLE);
            fabKeyL.setVisibility(View.VISIBLE);
            fabSeedL.setVisibility(View.VISIBLE);
            fabImportL.setVisibility(View.VISIBLE);

            fabNewL.startAnimation(fab_open);
            fabNew.setClickable(true);
            fabViewL.startAnimation(fab_open);
            fabView.setClickable(true);
            fabKeyL.startAnimation(fab_open);
            fabKey.setClickable(true);
            fabSeedL.startAnimation(fab_open);
            fabSeed.setClickable(true);
            fabImportL.startAnimation(fab_open);
            fabImport.setClickable(true);

            setFabOpen(true);
        }
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        Timber.d("onClick %d/%d", id, R.id.fabLedger);
        if (id == R.id.fab) {
            animateFAB();
        } else if (id == R.id.fabNew) {
            fabScreen.setVisibility(View.INVISIBLE);
            setFabOpen(false);
            activityCallback.onAddWallet(GenerateFragment.TYPE_NEW);
        } else if (id == R.id.fabView) {
            animateFAB();
            activityCallback.onAddWallet(GenerateFragment.TYPE_VIEWONLY);
        } else if (id == R.id.fabKey) {
            animateFAB();
            activityCallback.onAddWallet(GenerateFragment.TYPE_KEY);
        } else if (id == R.id.fabSeed) {
            animateFAB();
            activityCallback.onAddWallet(GenerateFragment.TYPE_SEED);
        } else if (id == R.id.fabImport) {
            animateFAB();
            activityCallback.onWalletRestore();
        } else if (id == R.id.fabScreen) {
            animateFAB();
        }
    }
}
