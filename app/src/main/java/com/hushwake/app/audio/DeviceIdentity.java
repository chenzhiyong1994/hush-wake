package com.hushwake.app.audio;

/** Irreversible per-install device identity and a coarse user-facing type. */
public record DeviceIdentity(String hash, String typeLabel) {}
