/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.registry.Converters.IConverter;
import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

public abstract class QueryContextImpl implements IQueryContext {

    public boolean available(Class<?> type, Advice advice) {
        return IQueryContext.class.isAssignableFrom(type);
    }

    public Object get(Class<?> type, Advice advice) {
        return IQueryContext.class.isAssignableFrom(type) ? this : null;
    }

    public boolean converts(Class<?> type, Advice advice) {
        return Converters.getConverter(type) != null;
    }

    public String convertToString(Class<?> type, Advice advice, Object value) throws SnapshotException {
        return Converters.getConverter(type).toString(value);
    }

    public Object convertToValue(Class<?> type, Advice advice, String value) throws SnapshotException {
        try {
            IConverter<Object> conv = Converters.getConverter(type);
            if (conv == null)
                throw new SnapshotException(MessageUtil.format(Messages.QueryContextImpl_ImpossibleToConvert,
                        type.getName()));
            return conv.toObject(value, advice);
        } catch (IllegalArgumentException e) {
            throw new SnapshotException(e);
        }
    }
}
