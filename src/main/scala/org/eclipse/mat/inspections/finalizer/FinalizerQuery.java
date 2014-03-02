/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections.finalizer;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

@CommandName("finalizer_overview")
public class FinalizerQuery implements IQuery {
    @Argument
    public ISnapshot snapshot;

    public IResult execute(IProgressListener listener) throws Exception {
        SectionSpec spec = new SectionSpec(Messages.FinalizerQuery_Finalizers);

        IResult result = SnapshotQuery.lookup("finalizer_in_processing", snapshot).execute(listener);
        QuerySpec inProcessing = new QuerySpec(Messages.FinalizerQuery_InProcessing, result);
        inProcessing.setCommand("finalizer_in_processing");
        spec.add(inProcessing);

        result = SnapshotQuery.lookup("finalizer_queue", snapshot).execute(listener);
        QuerySpec finalizerQueue = new QuerySpec(Messages.FinalizerQuery_ReadyForFinalizer, result);
        finalizerQueue.setCommand("finalizer_queue");
        spec.add(finalizerQueue);
        finalizerQueue.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());

        result = SnapshotQuery.lookup("finalizer_thread", snapshot).execute(listener);
        QuerySpec finalizerThread = new QuerySpec(Messages.FinalizerQuery_FinalizerThread, result);
        finalizerThread.setCommand("finalizer_thread");
        spec.add(finalizerThread);

        result = SnapshotQuery.lookup("finalizer_thread_locals", snapshot).execute(listener);
        QuerySpec finalizerLocals = new QuerySpec(Messages.FinalizerQuery_FinalizerThreadLocals, result);
        finalizerLocals.setCommand("finalizer_thread_locals");
        spec.add(finalizerLocals);
        return spec;
    }
}
