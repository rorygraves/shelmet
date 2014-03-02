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
package org.eclipse.mat.report.internal;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.results.DisplayFileResult;
import org.eclipse.mat.report.TestSuite;
import org.eclipse.mat.util.IProgressListener;

import java.io.File;

@CommandName("create_report")
public class RunExternalTest implements IQuery {
    @Argument
    public IQueryContext context;

    @Argument(flag = Argument.UNFLAGGED)
    public File testSuite;

    public IResult execute(IProgressListener listener) throws Exception {
        TestSuite suite = new TestSuite.Builder(testSuite).build(context);

        suite.execute(listener);

        for (File f : suite.getResults()) {
            if ("index.html".equals(f.getName()))
                return new DisplayFileResult(f);
        }

        return null;
    }

}
