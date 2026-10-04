package com.securevault.securevault_backend.entity;

public enum SharePermission {
    VIEW_ONLY,
    EDIT_ACCESS,
    FULL_MANAGEMENT;

    public static SharePermission fromString(String val) {
        if (val == null) return VIEW_ONLY;
        String upper = val.trim().toUpperCase();
        switch (upper) {
            case "READ_ONLY":
            case "VIEW_ONLY":
            case "VIEW":
                return VIEW_ONLY;
            case "EDIT":
            case "EDIT_ACCESS":
                return EDIT_ACCESS;
            case "FULL_ACCESS":
            case "FULL_MANAGEMENT":
            case "ADMIN":
                return FULL_MANAGEMENT;
            default:
                try {
                    return SharePermission.valueOf(upper);
                } catch (IllegalArgumentException e) {
                    return VIEW_ONLY;
                }
        }
    }
}
