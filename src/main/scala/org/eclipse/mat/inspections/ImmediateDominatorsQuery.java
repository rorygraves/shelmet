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
package org.eclipse.mat.inspections;

import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.*;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Argument.Advice;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.DominatorsSummary;
import org.eclipse.mat.snapshot.DominatorsSummary.ClassDominatorRecord;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;

import java.util.regex.Pattern;

@CommandName("immediate_dominators")
public class ImmediateDominatorsQuery implements IQuery {

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false, advice = Advice.CLASS_NAME_PATTERN, flag = "skip")
    public Pattern skipPattern = Pattern.compile("java.*|com\\.sun\\..*");

    public IResult execute(IProgressListener listener) throws Exception {
        DominatorsSummary summary = snapshot.getDominatorsOf(objects.getIds(listener), skipPattern, listener);

        return new ResultImpl(summary);
    }

    public static class ResultImpl implements IResultTable {
        DominatorsSummary summary;

        public ResultImpl(DominatorsSummary summary) {
            this.summary = summary;
        }

        public ResultMetaData getResultMetaData() {
            return new ResultMetaData.Builder()

                    .addContext(new ContextProvider(Messages.ImmediateDominatorsQuery_Objects) {
                        @Override
                        public IContextObject getContext(Object row) {
                            return getObjects(row);
                        }
                    })

                    .addContext(new ContextProvider(Messages.ImmediateDominatorsQuery_DominatedObjects) {
                        @Override
                        public IContextObject getContext(Object row) {
                            return getDominatedObjects(row);
                        }
                    })

                    .build();
        }

        public Column[] getColumns() {
            return new Column[]{
                    new Column(Messages.Column_ClassName),
                    new Column(Messages.Column_Objects, Long.class),
                    new Column(Messages.ImmediateDominatorsQuery_ColumnDominatedObjects, Long.class),
                    new Column(Messages.Column_ShallowHeap, Long.class),
                    new Column(Messages.ImmediateDominatorsQuery_Column_DominatedShallowHeap, Long.class)
                            .sorting(SortDirection.DESC)};
        }

        public int getRowCount() {
            return summary.getClassDominatorRecords().length;
        }

        public ClassDominatorRecord getRow(int rowId) {
            return summary.getClassDominatorRecords()[rowId];
        }

        public Object getColumnValue(Object row, int columnIndex) {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            switch (columnIndex) {
                case 0:
                    return record.getClassName();
                case 1:
                    return record.getDominatorCount();
                case 2:
                    return record.getDominatedCount();
                case 3:
                    return record.getDominatorNetSize();
                case 4:
                    return record.getDominatedNetSize();
            }

            return null;
        }

        public IContextObject getContext(Object row) {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            if (record.getClassId() >= 0) {
                return new IContextObject() {
                    public int getObjectId() {
                        return record.getClassId();
                    }
                };
            } else {
                return null;
            }
        }

        IContextObject getObjects(Object row) {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            if (record.getClassId() >= 0) {
                return new IContextObjectSet() {
                    public int getObjectId() {
                        return record.getClassId();
                    }

                    public int[] getObjectIds() {
                        return record.getDominators();
                    }

                    public String getOQL() {
                        return null;
                    }
                };
            } else {
                return null;
            }
        }

        IContextObject getDominatedObjects(Object row) {
            final ClassDominatorRecord record = (ClassDominatorRecord) row;
            return new IContextObjectSet() {
                public int getObjectId() {
                    return record.getClassId();
                }

                public int[] getObjectIds() {
                    return record.getDominated();
                }

                public String getOQL() {
                    return null;
                }
            };
        }
    }
}
