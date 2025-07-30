#   http://developer.android.com/guide/developing/tools/proguard.html


# For backup/restore config
# `keep` also keeps the class inner structure
-keep class spam.blocker.config.* { *; }

# For backup/restore permissions
# `keepnames` only keeps the class name, this will generate `PermissionTypes$CallScreening`
#   in release mode, use `substringAfterLast('$')` to get rid of the `PermissionTypes$`.
-keepnames class spam.blocker.util.PermissionType$** { *; }