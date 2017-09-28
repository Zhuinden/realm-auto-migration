package com.zhuinden.realmautomigrationexample;

import com.zhuinden.realmautomigration.AutoMigration;

import io.realm.FieldAttribute;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.Required;

/**
 * Created by Owner on 2017. 09. 28..
 */
public class Dog
        extends RealmObject {
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
