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
package org.eclipse.mat.inspections;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.Histogram;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;

@CommandName("histogram")
public class HistogramQuery implements IQuery {
    public enum Grouping {
        BY_CLASS(Messages.HistogramQuery_GroupByClass),
        BY_SUPERCLASS(Messages.HistogramQuery_GroupBySuperclass),
        BY_CLASSLOADER(Messages.HistogramQuery_GroupByClassLoader),
        BY_PACKAGE(Messages.HistogramQuery_GroupByPackage);

        String label;

        private Grouping(String label) {
            this.label = label;
        }

        public String toString() {
            return label;
        }
    }

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED, isMandatory = false)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public Grouping groupBy = Grouping.BY_CLASS;

    public IResult execute(IProgressListener listener) throws Exception {
        Histogram histogram = objects == null ? snapshot.getHistogram(listener)
                : snapshot.getHistogram(objects.getIds(listener), listener);

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        if (objects != null)
            histogram.setLabel(MessageUtil.format(Messages.HistogramQuery_HistogramOf, objects.getLabel()));

        switch (groupBy) {
            case BY_CLASS:
                return histogram;
            case BY_SUPERCLASS:
                return histogram.groupBySuperclass(snapshot);
            case BY_CLASSLOADER:
                return histogram.groupByClassLoader();
            case BY_PACKAGE:
                return histogram.groupByPackage();
            default:
                throw new RuntimeException(MessageUtil.format(Messages.HistogramQuery_IllegalArgument, groupBy));
        }
    }

}
