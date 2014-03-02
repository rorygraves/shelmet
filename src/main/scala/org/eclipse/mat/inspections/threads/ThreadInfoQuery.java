/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG & IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - optional columns
 *******************************************************************************/
package org.eclipse.mat.inspections.threads;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.ListResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.report.Spec;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IThreadInfo;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

import java.util.ArrayList;
import java.util.List;

@CommandName("thread_details")
public class ThreadInfoQuery implements IQuery {
    public static class Result extends SectionSpec {
        List<IThreadInfo> infos = new ArrayList<IThreadInfo>();

        private Result(String name) {
            super(name);
        }

        /* package */void add(SectionSpec child, ThreadInfoImpl tInfo) {
            add(child);
            infos.add(tInfo);
        }

        public List<IThreadInfo> getThreads() {
            return infos;
        }
    }

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument threadIds;

    public Result execute(IProgressListener listener) throws Exception {
        InspectionAssert.heapFormatIsNot(snapshot, "phd");

        Result spec = new Result(Messages.ThreadInfoQuery_ThreadDetails);
        List<ThreadInfoImpl> infos = new ArrayList<ThreadInfoImpl>();

        for (int[] ids : threadIds) {
            for (int id : ids) {
                infos.add(processThread(id, spec, listener));
            }
        }

        return spec;
    }

    private ThreadInfoImpl processThread(int threadId, Result parent, IProgressListener listener)
            throws SnapshotException {
        ThreadInfoImpl tInfo = ThreadInfoImpl.build(snapshot.getObject(threadId), true, listener);

        SectionSpec spec = new SectionSpec(MessageUtil.format(Messages.ThreadInfoQuery_ThreadLabel, tInfo.getName()));
        parent.add(spec, tInfo);

        // general properties
        QuerySpec properties = new QuerySpec(Messages.ThreadInfoQuery_ThreadProperties);
        properties.set(Params.Html.SHOW_TABLE_HEADER, Boolean.FALSE.toString());
        properties.setCommand("list_objects 0x" + Long.toHexString(tInfo.getThreadObject().getObjectAddress()));

        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        for (Column column : tInfo.getUsedColumns()) {
            Object value = tInfo.getValue(column);
            String f = value == null ? "" : column.getFormatter() != null ? column.getFormatter().format(value)
                    : String.valueOf(value);
            pairs.add(new NameValuePair(column.getLabel(), f));
        }
        properties.setResult(new ListResult(NameValuePair.class, pairs, "name", "value"));
        spec.add(properties);

        // thread stack
        QuerySpec stackResult = getStackTraceAsSpec(threadId);
        if (stackResult != null) {
            spec.add(stackResult);
        }


        // tasks information
        CompositeResult details = tInfo.getDetails();
        if (details != null && !details.isEmpty()) {
            for (CompositeResult.Entry rInfo : details.getResultEntries()) {
                QuerySpec tasks = new QuerySpec(rInfo.getName());
                tasks.setResult(rInfo.getResult());
                spec.add(tasks);
            }
        }

        // request information
        CompositeResult requests = tInfo.getRequests();
        if (requests != null && !requests.isEmpty()) {
            SectionSpec rSpec = new SectionSpec(Messages.ThreadInfoQuery_Requests);
            spec.add(rSpec);

            for (CompositeResult.Entry rInfo : requests.getResultEntries()) {
                QuerySpec thread = new QuerySpec(rInfo.getName());
                IResult res = rInfo.getResult();
                if (res instanceof CompositeResult || res instanceof Spec) {
                    thread.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
                    thread.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
                }
                thread.setResult(rInfo.getResult());
                rSpec.add(thread);
            }
        }

        return tInfo;
    }

    private QuerySpec getStackTraceAsSpec(int threadId) throws SnapshotException {
        IThreadStack stack = snapshot.getThreadStack(threadId);
        if (stack == null) return null;

        StringBuilder builder = new StringBuilder();
        IObject threadObject = snapshot.getObject(threadId);
        builder.append(threadObject.getClassSpecificName()).append("\r\n");
        for (IStackFrame frame : stack.getStackFrames()) {
            builder.append("  ").append(frame.getText()).append("\r\n");
        }

        return new QuerySpec(Messages.ThreadInfoQuery_ThreadStack, new TextResult(builder.toString()));

    }

}
