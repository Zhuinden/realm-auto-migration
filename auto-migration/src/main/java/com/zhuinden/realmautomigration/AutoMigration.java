/*
 * Copyright 2017 Gabor Varadi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zhuinden.realmautomigration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.realm.DynamicRealm;
import io.realm.FieldAttribute;
import io.realm.RealmConfiguration;
import io.realm.RealmList;
import io.realm.RealmMigration;
import io.realm.RealmModel;
import io.realm.RealmObjectSchema;
import io.realm.RealmResults;
import io.realm.RealmSchema;

/**
 * This migration attempts to migrate the Realm schema from one version to the current models provided in the configuration.
 *
 * In case of mismatch, fields defined only in schema but not in model are removed, and fields defined only in model but not in schema are added.
 *
 * To properly handle `@Index`, `@Required`, `@PrimaryKey` annotations, you must specify {@link MigratedField} with the specified FieldAttributes.
 *
 * To properly handle `@Ignore`, you must specify {@link MigrationIgnore}.
 *
 * To add `RealmList` field, you must specify {@link MigratedLink} on that field with the link type.
 *
 * Requires:
 * -keepnames public class * extends io.realm.RealmObject
 * -keep public class * extends io.realm.RealmObject { *; }
 * -keepattributes *Annotation*
 */
public class AutoMigration
        implements RealmMigration {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface MigrationIgnore {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface MigratedField {
        FieldAttribute[] fieldAttributes();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface MigratedLink {
        Class<? extends RealmModel> linkType(); // RealmList<T extends RealmModel> is nice, but T cannot be obtained through reflection.
    }

    @Override
    public int hashCode() {
        return AutoMigration.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof AutoMigration;
    }

    @Override
    public void migrate(DynamicRealm realm, long oldVersion, long newVersion) {
        RealmConfiguration realmConfiguration = realm.getConfiguration();

        Set<Class<? extends RealmModel>> latestRealmObjectClasses = realmConfiguration.getRealmObjectClasses();
        RealmSchema realmSchema = realm.getSchema();
        Set<RealmObjectSchema> initialObjectSchemas = realmSchema.getAll();

        // first we must create any object schema that belongs to model class that is not part of the schema yet, to allow links.
        List<RealmObjectSchema> createdObjectSchemas = new LinkedList<>();

        // first we must check for classes that are in the schema, but are not in the configuration.
        Set<String> modelClassNames = new LinkedHashSet<>();
        Map<String, Class<? extends RealmModel>> modelClassNameToClassMap = new LinkedHashMap<>();
        Set<String> schemaClassNames = new LinkedHashSet<>();
        Map<String, RealmObjectSchema> schemaClassNameToObjectSchemaMap = new LinkedHashMap<>();
        for(Class<? extends RealmModel> modelClass : latestRealmObjectClasses) {
            modelClassNames.add(modelClass.getSimpleName()); // "Cat", requires `-keepnames public class * extends io.realm.RealmObject`
            modelClassNameToClassMap.put(modelClass.getSimpleName(), modelClass);
        }
        for(RealmObjectSchema objectSchema : initialObjectSchemas) {
            schemaClassNames.add(objectSchema.getClassName()); // "Cat", requires `-keepnames public class * extends io.realm.RealmObject`
            schemaClassNameToObjectSchemaMap.put(objectSchema.getClassName(), objectSchema);
        }

        // now we must check if the model contains classes that are not part of the schema.
        for(String modelClassName : modelClassNames) {
            if(!schemaClassNames.contains(modelClassName)) {
                // the model class is not part of the schema, we must add it to the schema.
                RealmObjectSchema objectSchema = realmSchema.create(modelClassName);
                createdObjectSchemas.add(objectSchema);
            }
        }

        // we must check if existing schema classes have changed fields, or if they were removed from the model.
        for(String objectClassName : schemaClassNames) {
            RealmObjectSchema objectSchema = schemaClassNameToObjectSchemaMap.get(objectClassName);
            if(modelClassNames.contains(objectClassName)) {
                // the model was found in the schema, we must match their fields.
                Class<? extends RealmModel> modelClass = modelClassNameToClassMap.get(objectClassName);
                matchFields(realmSchema, objectSchema, modelClass);
            } else {
                // the model class was not part of the schema, so we must remove the object schema.
                realmSchema.remove(objectClassName);
            }
        }
        // now that we've set up our classes, we must also match the fields of newly created schema classes.
        for(RealmObjectSchema createdObjectSchema : createdObjectSchemas) {
            Class<? extends RealmModel> modelClass = modelClassNameToClassMap.get(createdObjectSchema.getClassName());
            matchFields(realmSchema, createdObjectSchema, modelClass);
        }
    }

    private void matchFields(RealmSchema realmSchema, RealmObjectSchema objectSchema, Class<? extends RealmModel> modelClass) {
        Field[] allModelFields = modelClass.getDeclaredFields();
        Set<String> modelFieldNames = new LinkedHashSet<>(allModelFields.length);
        Map<String, Field> modelFieldNameToFieldMap = new LinkedHashMap<>(allModelFields.length);
        for(Field field : allModelFields) {
            modelFieldNames.add(field.getName());
            modelFieldNameToFieldMap.put(field.getName(), field);
        }
        Set<String> schemaFieldNames = objectSchema.getFieldNames(); // field names require `-keep public class * extends io.realm.RealmObject { *; }`
        for(String schemaFieldName : schemaFieldNames) {
            if(!modelFieldNames.contains(schemaFieldName)) {
                // the model does not contain this field, so it no longer exists. We must remove this field.
                objectSchema.removeField(schemaFieldName);
            }
        }
        for(String modelFieldName : modelFieldNames) {
            Field field = modelFieldNameToFieldMap.get(modelFieldName);
            if(Modifier.isStatic(field.getModifiers())) { // we must ignore static fields!
                continue;
            }
            if(Modifier.isTransient(field.getModifiers())) { // transient fields are ignored.
                continue;
            }
            if(field.isAnnotationPresent(MigrationIgnore.class)) {
                continue; // manual ignore.
            }
            Class<?> fieldType = field.getType();
            if(!schemaFieldNames.contains(modelFieldName)) {
                // the schema does not contain the model's field, we must add this according to type!
                if(isNonNullPrimitive(fieldType) || isPrimitiveObjectWrapper(fieldType) || isFieldRegularObjectType(fieldType)) {
                    objectSchema.addField(modelFieldName, fieldType);
                } else {
                    if(fieldType == RealmResults.class) { // computed field (like @LinkingObjects), so this should be ignored.
                        //noinspection UnnecessaryContinue
                        continue;
                    } else if(fieldType == RealmList.class) {
                        // TODO: value lists in 4.0.0!
                        MigratedLink migratedLink = field.getAnnotation(MigratedLink.class);
                        if(migratedLink == null) {
                            throw new IllegalStateException("Link list [" + field.getName() + "] cannot be added to the schema without @MigratedLink(linkType) annotation.");
                        }
                        Class<? extends RealmModel> linkObjectClass = migratedLink.linkType();
                        String linkedObjectName = linkObjectClass.getSimpleName();
                        RealmObjectSchema linkedObjectSchema = realmSchema.get(linkedObjectName);
                        if(linkedObjectSchema == null) {
                            throw new IllegalStateException("The object schema [" + linkedObjectName + "] defined by link [" + modelFieldName + "] was not found in the schema!");
                        }
                        objectSchema.addRealmListField(field.getName(), linkedObjectSchema);
                    } else {
                        if(!RealmModel.class.isAssignableFrom(fieldType)) {
                            continue; // this is most likely an @Ignore field, let's just ignore it
                        }
                        String linkedObjectName = field.getType().getSimpleName();
                        RealmObjectSchema linkedObjectSchema = realmSchema.get(linkedObjectName);
                        if(linkedObjectSchema == null) {
                            throw new IllegalStateException("The object schema [" + linkedObjectName + "] defined by field [" + modelFieldName + "] was not found in the schema!");
                        }
                        objectSchema.addRealmObjectField(field.getName(), linkedObjectSchema);
                    }
                }
            }
            // even if it's added, its attributes might be mismatched! This must happen both if newly added, or if already exists.
            if(isNonNullPrimitive(fieldType) || isPrimitiveObjectWrapper(fieldType) || isFieldRegularObjectType(fieldType)) {
                matchMigratedField(objectSchema, modelFieldName, field);
            }
        }
    }

    private void matchMigratedField(RealmObjectSchema objectSchema, String modelFieldName, Field field) {
        MigratedField migratedField = field.getAnnotation(MigratedField.class); // @Required is not kept alive by its RetentionPolicy. We must use our own!
        if(migratedField != null) {
            boolean isIndexed = false;
            boolean isRequired = false;
            boolean isPrimaryKey = false;
            for(FieldAttribute fieldAttribute : migratedField.fieldAttributes()) {
                if(fieldAttribute == FieldAttribute.INDEXED) {
                    isIndexed = true;
                } else if(fieldAttribute == FieldAttribute.REQUIRED) {
                    isRequired = true;
                } else if(fieldAttribute == FieldAttribute.PRIMARY_KEY) {
                    isPrimaryKey = true;
                }
            }
            if((isIndexed || isPrimaryKey) && !objectSchema.hasIndex(modelFieldName)) {
                objectSchema.addIndex(modelFieldName);
            }
            if(!isIndexed && !isPrimaryKey /* primary key is indexed by default! */ && objectSchema.hasIndex(modelFieldName)) {
                objectSchema.removeIndex(modelFieldName);
            }
            if(isNonNullPrimitive(field.getType())) {
                if(!objectSchema.isRequired(modelFieldName)) {
                    objectSchema.setNullable(modelFieldName, false);
                }
            } else {
                if(isRequired && objectSchema.isNullable(modelFieldName)) {
                    objectSchema.setNullable(modelFieldName, false);
                }
                if(!isRequired && !objectSchema.isNullable(modelFieldName)) {
                    objectSchema.setNullable(modelFieldName, true);
                }
            }
            if(isPrimaryKey && !objectSchema.isPrimaryKey(modelFieldName)) {
                if(objectSchema.hasPrimaryKey()) {
                    throw new UnsupportedOperationException("Multiple primary keys are not supported: [" + objectSchema.getClassName() + " :: " + modelFieldName + "]");
                }
                objectSchema.addPrimaryKey(modelFieldName);
            }
            if(!isPrimaryKey && objectSchema.isPrimaryKey(modelFieldName)) {
                objectSchema.removePrimaryKey();
            }
        }
    }

    private boolean isFieldRegularObjectType(Class<?> fieldType) {
        return fieldType == String.class || fieldType == Date.class || fieldType == byte[].class;
    }

    private boolean isPrimitiveObjectWrapper(Class<?> fieldType) {
        return fieldType == Boolean.class //
                || fieldType == Byte.class || fieldType == Short.class || fieldType == Integer.class || fieldType == Long.class //
                || fieldType == Float.class || fieldType == Double.class;
    }

    private boolean isNonNullPrimitive(Class<?> fieldType) {
        return fieldType == boolean.class //
                || fieldType == byte.class || fieldType == short.class || fieldType == int.class || fieldType == long.class //
                || fieldType == float.class || fieldType == double.class;
    }
}