package com.tebreca.kwant.util;

/**
 * Simple DRY class to hold a flag attribute for practically every builder / configurator
 * @param <T>
 */
public class FlagHolder<T extends FlagHolder<T>> {

    protected int flags = 0x0b;

    //This is by design
    @SuppressWarnings("unchecked")
    public T withFlags(int flags) {
        this.flags |= flags;
        return (T) this;
    }
}
