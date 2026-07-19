package com.espsa.mobilepos.core.model;

/** Immutable product provenance. Only a reliably established MS2011 GID may create MS2011_SYNC. */
public enum ProductOrigin {
    LOCAL,
    LEGACY_IMPORT,
    MS2011_SYNC
}
