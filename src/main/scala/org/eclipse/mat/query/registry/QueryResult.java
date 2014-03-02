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

import org.eclipse.mat.query.*;

public class QueryResult {
    private QueryResult parent;
    private QueryDescriptor query;
    private String command;
    private IResult subject;

    /**
     * caching
     */
    private ResultMetaData resultMetaData;

    /**
     * caching
     */
    private ContextProvider defaultContextProvider;

    public QueryResult(QueryDescriptor query, String command, IResult subject) {
        this(null, query, command, subject);
    }

    public QueryResult(QueryResult parent, QueryDescriptor query, String command, IResult subject) {
        this.parent = parent;
        this.query = query;
        this.command = command;
        this.subject = subject;

        resultMetaData = subject != null ? subject.getResultMetaData() : null;
        if (resultMetaData == null)
            resultMetaData = new ResultMetaData.Builder().build();

        if (subject instanceof IStructuredResult) {
            defaultContextProvider = new ContextProvider((String) null) {
                @Override
                public IContextObject getContext(Object row) {
                    return ((IStructuredResult) QueryResult.this.subject).getContext(row);
                }
            };
        }
    }

    public QueryResult getParent() {
        return parent;
    }

    public String getCommand() {
        return command;
    }

    public QueryDescriptor getQuery() {
        return query;
    }

    public IResult getSubject() {
        return subject;
    }

    public ResultMetaData getResultMetaData() {
        return resultMetaData;
    }

    public String getTitle() {
        return command;
    }

    public ContextProvider getDefaultContextProvider() {
        return defaultContextProvider;
    }

}
