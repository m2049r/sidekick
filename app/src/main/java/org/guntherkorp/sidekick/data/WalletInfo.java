package org.guntherkorp.sidekick.data;

import java.io.File;

import lombok.Getter;

@Getter
public class WalletInfo implements Comparable<WalletInfo> {
    final private File path;
    final private String name;

    public WalletInfo(File wallet) {
        path = wallet.getParentFile();
        name = wallet.getName();
    }

    @Override
    public int compareTo(WalletInfo another) {
        return name.toLowerCase().compareTo(another.name.toLowerCase());
    }
}
