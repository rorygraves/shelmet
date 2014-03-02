/*******************************************************************************
 * Copyright (c) 2008, 2013 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - localization of icons
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.*;
import org.eclipse.mat.inspections.collections.CollectionFillRatioQuery;
import org.eclipse.mat.inspections.collections.CollectionsBySizeQuery;
import org.eclipse.mat.inspections.collections.HashEntriesQuery;
import org.eclipse.mat.inspections.collections.MapCollisionRatioQuery;
import org.eclipse.mat.inspections.component.ComponentReportQuery;
import org.eclipse.mat.inspections.component.TopComponentsReportQuery;
import org.eclipse.mat.inspections.eclipse.LeakingPlugins;
import org.eclipse.mat.internal.snapshot.inspections.CompareTablesQuery;
import org.eclipse.mat.internal.snapshot.inspections.DominatorQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.report.internal.RunRegisterdReport;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.RegistryReader;

import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Pattern;

public class QueryRegistry extends RegistryReader<IQuery> {
    private final Map<String, QueryDescriptor> commandsByIdentifier = new HashMap<>();
    private final Map<String, QueryDescriptor> commandsByClass = new HashMap<>();

    private static final QueryRegistry instance = new QueryRegistry();

    public static QueryRegistry instance() {
        return instance;
    }

    public QueryRegistry() {

        try {
            registerQuery(new HashEntriesQuery());
            registerQuery(new CollectionsBySizeQuery());
            registerQuery(new CollectionFillRatioQuery());
            registerQuery(new MapCollisionRatioQuery());
            registerQuery(new DominatorQuery());
            registerQuery(new BigDropsQuery());
            registerQuery(new BiggestObjectsPieQuery());
            registerQuery(new ClassReferrersQuery());
            registerQuery(new CustomizedRetainedSetQuery());
            registerQuery(new TopComponentsReportQuery());
            registerQuery(new ComponentReportQuery());
            registerQuery(new TopConsumers2Query());
            registerQuery(new GroupByValueQuery());
            registerQuery(new RunRegisterdReport());
            registerQuery(new OQLQuery());
            registerQuery(new SystemPropertiesQuery());
            registerQuery(new WasteInCharArraysQuery());
            registerQuery(new HistogramQuery());
            registerQuery(new CompareTablesQuery());
            registerQuery(new LeakingPlugins());
            registerQuery(new UnreachableObjectsQuery());
        } catch (SnapshotException e) {
            e.printStackTrace();
        }
    }

    public synchronized Collection<QueryDescriptor> getQueries() {
        return Collections.unmodifiableCollection(commandsByIdentifier.values());
    }

    public synchronized QueryDescriptor getQuery(String name) {
        QueryDescriptor descriptor = commandsByIdentifier.get(name);
        return descriptor != null ? descriptor : commandsByClass.get(name);
    }

    // //////////////////////////////////////////////////////////////
    // mama's little helpers
    // //////////////////////////////////////////////////////////////

    private String getIdentifier(IQuery query) {
        Class<? extends IQuery> queryClass = query.getClass();

        Name n = queryClass.getAnnotation(Name.class);
        String name = n != null ? n.value() : queryClass.getSimpleName();

        CommandName cn = queryClass.getAnnotation(CommandName.class);
        return (cn != null ? cn.value() : name).toLowerCase(Locale.ENGLISH).replace(' ', '_');
    }

    private synchronized QueryDescriptor registerQuery(IQuery query) throws SnapshotException {
        Class<? extends IQuery> queryClass = query.getClass();

        String key = queryClass.getSimpleName();
        ResourceBundle i18n = getBundle(queryClass);

        Name n = queryClass.getAnnotation(Name.class);
        String name = translate(i18n, key + ".name", n != null ? n.value() : queryClass.getSimpleName());

        String identifier = getIdentifier(query);

        // do NOT overwrite command names
        if (commandsByIdentifier.containsKey(identifier))
            throw new SnapshotException(MessageUtil.format(Messages.QueryRegistry_Error_NameBound, identifier,
                    commandsByIdentifier.get(identifier).getCommandType().getName()));


        /*
         * $nl$ and annotations.properties .icon substitution added with 1.3
         * This allows
         * @Icon("$nl$/MANIFEST.MF/icons/myicon.gif")
         * also this which is safe (ignored) for < V1.3
         * myquery.icon = $nl$/MANIFEST.MF/icons/myicon.gif
         */

        QueryDescriptor descriptor = new QueryDescriptor(identifier, name, queryClass);

        Class<?> clazz = queryClass;
        while (!clazz.equals(Object.class)) {
            addArguments(query, clazz, descriptor, i18n);
            clazz = clazz.getSuperclass();
        }

        commandsByIdentifier.put(identifier, descriptor);
        commandsByClass.put(query.getClass().getName().toLowerCase(Locale.ENGLISH), descriptor);
        return descriptor;
    }

    private String translate(ResourceBundle i18n, String key, String defaultValue) {
        try {
            return i18n.getString(key);
        } catch (MissingResourceException e) {
            return defaultValue;
        }
    }

    private ResourceBundle getBundle(Class<? extends IQuery> queryClass) {
        try {
            return ResourceBundle.getBundle(queryClass.getPackage().getName() + ".annotations",
                    Locale.getDefault(), queryClass.getClassLoader());
        } catch (MissingResourceException e) {
            return new ResourceBundle() {
                @Override
                protected Object handleGetObject(String key) {
                    return null;
                }

                @Override
                public Enumeration<String> getKeys() {
                    return null;
                }

                @Override
                public Locale getLocale() {
                    // All the standard queries should have annotations, so a missing annotation
                    // is for a user supplied query, so guess it is in the default locale.
                    return Locale.getDefault();
                }
            };
        }
    }

    private void addArguments(IQuery query, Class<?> clazz, QueryDescriptor descriptor, ResourceBundle i18n)
            throws SnapshotException {
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            try {
                Argument argument = field.getAnnotation(Argument.class);

                if (argument != null) {
                    ArgumentDescriptor argDescriptor = fromAnnotation(clazz, argument, field, field.get(query));

                    descriptor.addParameter(argDescriptor);
                }
            } catch (SnapshotException e) {
                throw e;
            } catch (IllegalAccessException e) {
                String msg = Messages.QueryRegistry_Error_Inaccessible;
                throw new SnapshotException(MessageUtil.format(msg, field.getName(), clazz.getName()), e);
            } catch (Exception e) {
                throw new SnapshotException(MessageUtil.format(Messages.QueryRegistry_Error_Argument, field.getName(),
                        clazz.getName()), e);
            }
        }
    }

    private ArgumentDescriptor fromAnnotation(Class<?> clazz, Argument annotation, Field field, Object defaultValue)
            throws SnapshotException {
        ArgumentDescriptor d = new ArgumentDescriptor();
        d.setMandatory(annotation.isMandatory());
        d.setName(field.getName());

        String flag = annotation.flag();
        if (flag.equals(Argument.UNFLAGGED))
            flag = null;
        else if (flag.length() == 0)
            flag = field.getName().toLowerCase(Locale.ENGLISH);
        d.setFlag(flag);

        d.setField(field);

        d.setArray(field.getType().isArray());
        d.setList(List.class.isAssignableFrom(field.getType()));

        // set type of the argument
        if (d.isArray()) {
            d.setType(field.getType().getComponentType());
        } else if (d.isList()) {
            Type type = field.getGenericType();
            if (type instanceof ParameterizedType) {
                Type[] typeArguments = ((ParameterizedType) type).getActualTypeArguments();
                d.setType((Class<?>) typeArguments[0]);
            }
        } else {
            d.setType(field.getType());
        }

        // validate the advice
        Argument.Advice advice = annotation.advice();

        if (advice == Argument.Advice.CLASS_NAME_PATTERN && !Pattern.class.isAssignableFrom(d.getType())) {
            String msg = MessageUtil.format(Messages.QueryRegistry_Error_Advice, field.getName(), clazz.getName(),
                    Argument.Advice.CLASS_NAME_PATTERN, Pattern.class.getName());
            throw new SnapshotException(msg);
        }

        if (advice != Argument.Advice.NONE)
            d.setAdvice(advice);

        // set the default value
        if (d.isArray() && defaultValue != null) {
            // internally, all multiple values have their values held as arrays
            // therefore we convert the array once and for all
            int size = Array.getLength(defaultValue);
            List<Object> l = new ArrayList<Object>(size);
            for (int ii = 0; ii < size; ii++) {
                l.add(Array.get(defaultValue, ii));
            }
            d.setDefaultValue(Collections.unmodifiableList(l));
        } else {
            d.setDefaultValue(defaultValue);
        }

        return d;
    }
}
