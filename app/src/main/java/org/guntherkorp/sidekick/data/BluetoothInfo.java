/*
 * Copyright (c) 2018 m2049r
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

package org.guntherkorp.sidekick.data;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import java.util.Comparator;

import lombok.Data;
import lombok.Getter;

@Getter
@Data
public class BluetoothInfo {
    final private String name;
    final private String address;
    private boolean bonded;

    @SuppressLint("MissingPermission")
    public BluetoothInfo(BluetoothDevice device) {
        name = device.getName().trim();
        address = device.getAddress();
        bonded = device.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    static public Comparator<BluetoothInfo> NameComparator = (o1, o2) -> o1.name.compareTo(o2.name);
}
