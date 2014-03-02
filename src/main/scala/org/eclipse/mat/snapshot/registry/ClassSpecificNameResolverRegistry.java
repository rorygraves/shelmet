/*******************************************************************************
 * Copyright (c) 2008, 2012 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - refactor to use SubjectRegistry
 *******************************************************************************/
package org.eclipse.mat.snapshot.registry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.CommonNameResolver;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry for name resolvers which resolve the names for objects of specific
 * classes (found in an snapshot), e.g. String (where the char[] is evaluated)
 * or a specific class loader (where the appropriate field holding its name and
 * thereby deployment unit is evaluated).
 */
public final class ClassSpecificNameResolverRegistry {
    // For registerResolver()
    private static Map<String, IClassSpecificNameResolver> resolvers = new HashMap<>();

    static {
        addResolver(CommonNameResolver.AccessibleObjectResolver.class);
        addResolver(CommonNameResolver.ByteArrayResolver.class);
        addResolver(CommonNameResolver.CharArrayResolver.class);
        addResolver(CommonNameResolver.ConstructorResolver.class);
        addResolver(CommonNameResolver.FieldResolver.class);
        addResolver(CommonNameResolver.MethodResolver.class);
        addResolver(CommonNameResolver.StringBufferResolver.class);
        addResolver(CommonNameResolver.StringResolver.class);
        addResolver(CommonNameResolver.ThreadGroupResolver.class);
        addResolver(CommonNameResolver.ThreadResolver.class);
        addResolver(CommonNameResolver.URLResolver.class);
        addResolver(CommonNameResolver.ValueResolver.class);
    }

    private static <T extends IClassSpecificNameResolver> void addResolver(Class<T> res) {
        try {
            IClassSpecificNameResolver inst = res.newInstance();
            Subject subject = res.getAnnotation(Subject.class);
            if (subject != null)
                resolvers.put(subject.value(), inst);

            Subjects subjects = res.getClass().getAnnotation(Subjects.class);
            if (subjects != null)
                for (String s : subjects.value()) {
                    resolvers.put(s, inst);
                }

        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }


    }

    private ClassSpecificNameResolverRegistry() { }

    /**
     * Resolve name of the given snapshot object or return null if it can't be
     * resolved.
     *
     * @param object snapshot object for which the name should be resolved
     * @return name of the given snapshot object or null if it can't be resolved
     */
    public static String resolve(IObject object) {
        if (object == null)
            throw new NullPointerException(Messages.ClassSpecificNameResolverRegistry_Error_MissingObject);

        try {
            IClass clazz = object.getClazz();
            while (clazz != null) {
                // For registerResolver()
                IClassSpecificNameResolver resolver = resolvers.get(clazz.getName());
                if (resolver != null) {
                    return resolver.resolve(object);
                }

                resolver = resolvers.get(clazz.getName());
                if (resolver != null) {
                    return resolver.resolve(object);
                }
                clazz = clazz.getSuperClass();
            }
            return null;
        } catch (RuntimeException e) {
            Logger.getLogger(ClassSpecificNameResolverRegistry.class.getName()).log(
                    Level.SEVERE,
                    MessageUtil.format(Messages.ClassSpecificNameResolverRegistry_ErrorMsg_DuringResolving,
                            object.getTechnicalName()), e);
            return null;
        } catch (SnapshotException e) {
            Logger.getLogger(ClassSpecificNameResolverRegistry.class.getName()).log(
                    Level.SEVERE,
                    MessageUtil.format(Messages.ClassSpecificNameResolverRegistry_ErrorMsg_DuringResolving,
                            object.getTechnicalName()), e);
            return null;
        }
    }
}
