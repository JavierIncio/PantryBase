package com.pantrybase.api.user.domain;

/**
 * Filtering mode applied when ranking recipes against the pantry.
 * STRICT requires full coverage; LAX uses the configured threshold.
 */
public enum FilterMode { STRICT, LAX }
