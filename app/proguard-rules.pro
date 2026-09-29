# Add project specific ProGuard rules here.

# Enum constant names are persisted (Room stores `.name`, DataStore stores `.name`, migrations write
# literals like 'CHIQIM' / 'ODDIY' / 'NAQD'). Renaming them in release would make existing data unreadable.
-keepclassmembers enum com.onikki.app.** { <fields>; }
