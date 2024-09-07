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
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import org.guntherkorp.sidekick.model.SidekickWallet;
import org.guntherkorp.sidekick.service.SidekickService;
import org.guntherkorp.sidekick.util.Flasher;
import org.guntherkorp.sidekick.util.Helper;
import org.guntherkorp.sidekick.widget.Toolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import timber.log.Timber;

public class SidekickFragment extends Fragment {
    private TextView tvFee;
    private TextView tvTransfers;
    private TextView restoreheight;
    private GridLayout lights;
    private ViewGroup confirmation;

    private Map<String, Flasher> flashLights;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sidekick, container, false);

        tvFee = view.findViewById(R.id.tvFee);
        tvTransfers = view.findViewById(R.id.tvTransfers);
        restoreheight = view.findViewById(R.id.restoreheight);

        lights = view.findViewById(R.id.lights);
        confirmation = view.findViewById(R.id.confirmation);

        flashLights = new HashMap<>();
        for (Light light : Light.values()) {
            if (light.name().startsWith("x")) {
                Flasher flasher = new Flasher(requireContext(), light);
                flashLights.put(light.name(), flasher);
                final TextView lightView = (TextView) inflater.inflate(R.layout.item_sidekick_light, lights, false);
                lightView.setCompoundDrawablesRelativeWithIntrinsicBounds(null, flasher.getDrawable(), null, null);
                lightView.setText(light.name());
                lights.addView(lightView);
            }
        }
        lights.setVisibility(View.VISIBLE);
        confirmation.setVisibility(View.GONE);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        postponeEnterTransition();
        view.getViewTreeObserver().addOnPreDrawListener(() -> {
            startPostponedEnterTransition();
            return true;
        });
    }

    public void setRestoreheight(String height) {
        restoreheight.setText(getString(R.string.restoreheight_text, height));
    }

    private void showLights() {
        requireView().findViewById(R.id.confirmation).setVisibility(View.INVISIBLE);
        requireView().findViewById(R.id.lights).setVisibility(View.VISIBLE);
    }

    @RequiredArgsConstructor
    static class TransferInfo {
        private final boolean change;
        private final String address;
        private final long amount;

        public static int compare(TransferInfo t1, TransferInfo t2) {
            if (t1.change) {
                if (t2.change) return 0;
                else return 1;
            } else return -1;
        }
    }

    // txInfo := fee (':' isChange ':' address ':' amount)+
    private void showTransfers(String transfers) {
        requireView().findViewById(R.id.confirmation).setVisibility(View.VISIBLE);
        requireView().findViewById(R.id.lights).setVisibility(View.INVISIBLE);
        // split transfers and display
        String[] parts = transfers.split(":");
        if (parts.length < 4)
            throw new IllegalArgumentException("TX info missing, found only " + parts.length + " parts");
        if (parts.length % 3 != 1)
            throw new IllegalArgumentException("TX info is broken");
        tvFee.setText(getString(R.string.send_fee, Helper.getDisplayAmount(Long.parseLong(parts[0]))));
        ArrayList<TransferInfo> transferInfos = new ArrayList<>();
        for (int i = 1; i < parts.length; i += 3) {
            transferInfos.add(new TransferInfo(parts[i].equals("T"), parts[i + 1], Long.parseLong(parts[i + 2])));
        }
        Collections.sort(transferInfos, TransferInfo::compare);
        StringBuilder transferString = new StringBuilder();
        for (TransferInfo info : transferInfos) {
            transferString.append("\n")
                    .append(Helper.getDisplayAmount(info.amount)).append(" XMR")
                    .append(info.change ? " (CHANGE)" : "").append("\n")
                    .append(info.address).append("\n");
        }
        tvTransfers.setText(transferString);
        lights.setVisibility(View.GONE);
        confirmation.setVisibility(View.VISIBLE);
    }

    public void confirmTransfers(SidekickService.Confirmation confirmation) {
        showTransfers(confirmation.getTransfers());
        final MaterialButton accept = requireView().findViewById(R.id.buttonAccept);
        accept.setOnClickListener(button -> {
            Timber.e("ACCEPTED");
            showLights();
            confirmation.accept();
        });
        final MaterialButton reject = requireView().findViewById(R.id.buttonDeny);
        reject.setOnClickListener(button -> {
            Timber.e("DENIED");
            showLights();
            confirmation.deny();
        });
    }

    public void flash(int command) {
        final Flasher flasher = flashLights.get(String.format("x%02X", command));
        if (flasher == null) {
            Timber.e("Invalid command %d/x%02x", command, command);
            return;
        }
        flasher.flash(getView());
    }

    private Listener activityCallback;

    // Container Activity must implement this interface
    public interface Listener {
        SidekickWallet getWallet();

        void setToolbarButton(int type);

        void setTitle(String title);

        void setSubtitle(String subtitle);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.activityCallback = (Listener) context;
        } else {
            throw new ClassCastException(context + " must implement Listener");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setExitTransition(null);
        setReenterTransition(null);
        activityCallback.setToolbarButton(Toolbar.BUTTON_BACK);
        Timber.d("onResume()");
    }

    @Override
    public void onPause() {
        super.onPause();
    }


    @RequiredArgsConstructor
    public enum Light implements org.guntherkorp.sidekick.util.Light {
        x61(R.drawable.sidekick_61),

        x4E(R.drawable.sidekick_4e),

        x50(R.drawable.sidekick_50),

        x46(R.drawable.sidekick_46),

        x47(R.drawable.sidekick_47),

        x5B(R.drawable.sidekick_5b),

        x4C(R.drawable.sidekick_4c),

        x4B(R.drawable.sidekick_4b),

        x5C(R.drawable.sidekick_5c),

        x5A(R.drawable.sidekick_5a),

        x43(R.drawable.sidekick_43),

        x42(R.drawable.sidekick_42),

        x48(R.drawable.sidekick_48),

        x41(R.drawable.sidekick_41),

        x49(R.drawable.sidekick_49),

        x53(R.drawable.sidekick_53),

        x40(R.drawable.sidekick_40),

        x54(R.drawable.sidekick_54),

        x56(R.drawable.sidekick_56),

        x57(R.drawable.sidekick_57),

        x44(R.drawable.sidekick_44),

        x58(R.drawable.sidekick_58),

        x59(R.drawable.sidekick_59),

        x60(R.drawable.sidekick_60),

        x5D(R.drawable.sidekick_5d),

        x5E(R.drawable.sidekick_5e),
        x5F(R.drawable.sidekick_5f),

        x55(R.drawable.sidekick_55),

        x4D(R.drawable.sidekick_4d),

        x45(R.drawable.sidekick_45),

        x4F(R.drawable.sidekick_4f),

        x51(R.drawable.sidekick_51),

        x52(R.drawable.sidekick_52),

        x4A(R.drawable.sidekick_4a),

        x62(R.drawable.sidekick_62);

        @Getter
        final private int drawableId;
    }
}
