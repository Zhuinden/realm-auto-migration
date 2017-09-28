# Realm Auto-Migration

Automatic migration from the currently existing schema to the currently existing model classes.

Heavily proof of concept, think `0.0.1-alpha`, but it worked for my example codes.

## Using AutoMigration

Currently you can copy the `AutoMigration.java` file to your project. Not exposed on Jitpack yet or anything.

Please read the section below on what annotations you need to apply to make things work.

## Proguard

```
-keepnames public class * extends io.realm.RealmObject
-keep public class * extends io.realm.RealmObject { *; }
-keepattributes *Annotation*
```

## Behavior

This migration attempts to migrate the Realm schema from one version to the current models provided in the configuration.

In case of mismatch, fields defined only in schema but not in model are removed, and fields defined only in model but not in schema are added.

## Field Attributes

To properly handle `@Index`, `@Required`, `@PrimaryKey` annotations, you must specify {@link MigratedField} with the specified FieldAttributes.

Please note that if this annotation is not present, then these properties won't be matched against the field.

To clear the annotations (for example making a column no longer have `@Index`), you'll need to add `@MigratedField(fieldAttributes={})`.

## Ignored fields

To properly handle `@Ignore`, you must specify {@link MigrationIgnore}.

## Linked fields

To add `RealmList` field, you must specify {@link MigratedLink} on that field with the link type.

## Example

``` java
public class Dog
        extends RealmObject {
    @PrimaryKey
    @AutoMigration.MigratedField(fieldAttributes = {FieldAttribute.PRIMARY_KEY})
    private long id;

    @Index
    @AutoMigration.MigratedField(fieldAttributes = {FieldAttribute.INDEXED})
    private String name;

    @Required
    @AutoMigration.MigratedField(fieldAttributes = {FieldAttribute.REQUIRED})
    private String ownerName;

    private Cat cat;

    @AutoMigration.MigratedLink(linkType = Cat.class)
    private RealmList<Cat> manyCats;
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
