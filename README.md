# Realm Auto-Migration

Automatic migration from the currently existing schema to the currently existing model classes.

The API is subject to change.

## Using AutoMigration

Currently you can copy the `AutoMigration.java` file to your project. Not exposed on Jitpack yet or anything.

Please read the section below on what annotations you need to apply to make things work.

## Proguard

```
-keepnames public class * extends io.realm.RealmModel
-keep public class * extends io.realm.RealmModel { *; }
-keepnames public class * extends io.realm.RealmObject
-keep public class * extends io.realm.RealmObject { *; }
-keepattributes *Annotation*
```

## Behavior

This migration attempts to migrate the Realm schema from one version to the current models provided in the configuration.

In case of mismatch, fields defined only in schema but not in model are removed, and fields defined only in model but not in schema are added.

## Linked fields

To add `RealmList` field, you must specify {@link MigratedList} on that field with the list type.

This properly supports both links and primitive lists. 

`@AutoMigration.MigratedList` must be applied to detect changes in `RealmList<Primitive>`'s `@Required` annotation.

## Example

``` java
public class Dog
        extends RealmObject {
    @PrimaryKey
    private long id;

    @Index
    private String name;

    @Required
    private String ownerName;

    private Cat cat;

    @AutoMigration.MigratedList(listType = Cat.class)
    private RealmList<Cat> manyCats;
    
    @AutoMigration.MigratedList(listType = String.class)
    private RealmList<String> phoneNumbers;
}
```

## License

    Copyright 2017 Gabor Varadi

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
