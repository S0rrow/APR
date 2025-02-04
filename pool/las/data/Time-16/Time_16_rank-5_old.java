/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.configuration;

import org.mockito.exceptions.Reporter;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.configuration.injection.FinalMockCandidateFilter;
import org.mockito.internal.configuration.injection.MockCandidateFilter;
import org.mockito.internal.configuration.injection.NameBasedCandidateFilter;
import org.mockito.internal.configuration.injection.TypeBasedCandidateFilter;
import org.mockito.internal.util.reflection.FieldInitializer;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

/**
 * Initializes mock/spies dependencies for fields annotated with &#064;InjectMocks
 * <p/>
 * See {@link org.mockito.MockitoAnnotations}
 */
public class DefaultInjectionEngine {

    private final MockCandidateFilter mockCandidateFilter = new TypeBasedCandidateFilter(new NameBasedCandidateFilter(new FinalMockCandidateFilter()));
    private Comparator<Field> supertypesLast = new Comparator<Field>() {
        public int compare(Field field1, Field field2) {
            Class<?> field1Type = field1.getType();
            Class<?> field2Type = field2.getType();

            if(field1Type.isAssignableFrom(field2Type)) {
                return -1;
            }
            if(field2Type.isAssignableFrom(field1Type)) {
                return 1;
            }
            return 0;
        }
    };

    // for each tested
    // - for each field of tested
    //   - find mock candidate by type
    //   - if more than *one* find mock candidate on name
    //   - if one mock candidate then set mock
    //   - else don't fail, user will then provide dependencies
    public void injectMocksOnFields(Set<Field> testClassFields, Set<Object> mocks, Object testClass) {
        for (Field field : testClassFields) {
            Object fieldInstance = null;
            try {
                fieldInstance = new FieldInitializer(testClass, field).initialize();
            } catch (MockitoException e) {
                new Reporter().cannotInitializeForInjectMocksAnnotation(field.getName(), e);
            }

            // for each field in the class hierarchy
            Class<?> fieldClass = fieldInstance.getClass();
            while (fieldClass != Object.class) {
                injectMockCandidate(fieldClass, mocks, fieldInstance);
                fieldClass = fieldClass.getSuperclass();
            }
        }
    }

    private void injectMockCandidate(Class<?> awaitingInjectionClazz, Set<Object> mocks, Object fieldInstance) {
        Field[] declaredFields = awaitingInjectionClazz.getDeclaredFields();
        Arrays.sort(declaredFields, supertypesLast);

        for(Field field : declaredFields) {
            mockCandidateFilter.filterCandidate(mocks, field, fieldInstance).thenInject();
        }
    }

}
