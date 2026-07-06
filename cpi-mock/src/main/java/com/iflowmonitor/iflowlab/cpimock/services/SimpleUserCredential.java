package com.iflowmonitor.iflowlab.cpimock.services;

import com.sap.it.api.securestore.UserCredential;

/** A plain {@link UserCredential} holding a username and password. */
public final class SimpleUserCredential implements UserCredential {

    private final String username;
    private final char[] password;

    public SimpleUserCredential(String username, String password) {
        this.username = username;
        this.password = password == null ? new char[0] : password.toCharArray();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public char[] getPassword() {
        return password.clone();
    }
}
