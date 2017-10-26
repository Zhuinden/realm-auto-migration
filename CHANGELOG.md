# Realm Auto Migration 0.1.0 (2017-10-26)

Upgraded to rely on Realm-Java 4.0.0 and above.

- Removed `@MigratedField` because Realm annotations are now kept for runtime (thanks Realm!).

- Renamed `@MigratedLink` to `@MigratedList`, and `linkType` to `listType`. Removed `? extends RealmModel` limitation to support primitive lists.

- Added support for primitive lists.

- Fixed changing @PrimaryKey from one field to another.

# Realm Auto Migration 0.0.1 (2017-10-07)
  
First version of Realm Auto Migration.

This release is created because it still supports Realm 3.7.2, and it contains all code needed to match the schema against existing RealmModels.

Known bug is that changing the primary key field will assume you are making multiple primary keys, but this can be fixed by changing one line.

# Pre-release (2017-09-28)

AutoMigration seems to be working.