# Project-specific R8 rules. Glance, WorkManager and Compose ship their own consumer rules.

# Release builds don't log: verbose/debug/info/warning calls are removed (errors stay)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
