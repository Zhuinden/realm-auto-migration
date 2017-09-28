package com.zhuinden.realmautomigrationexample;

import javax.inject.Singleton;

import dagger.Component;

/**
 * Created by Zhuinden on 2017.09.24..
 */

@Singleton
@Component(modules = RealmModule.class)
public interface SingletonComponent {
    RealmManager realmManager();
}
