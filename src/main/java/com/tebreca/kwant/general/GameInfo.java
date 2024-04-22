package com.tebreca.kwant.general;

public class GameInfo {

    String name;

    int version;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public GameInfo(String name, int version) {
        this.name = name;
        this.version = version;
    }
}
