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
package org.eclipse.mat.inspections.component;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.inspections.InspectionAssert;
import org.eclipse.mat.inspections.ReferenceQuery;
import org.eclipse.mat.inspections.collections.CollectionUtil;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.internal.snapshot.inspections.MultiplePath2GCRootsQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTree;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.TotalsRow;
import org.eclipse.mat.query.results.CompositeResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.report.Params;
import org.eclipse.mat.report.QuerySpec;
import org.eclipse.mat.report.SectionSpec;
import org.eclipse.mat.snapshot.*;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.snapshot.query.PieFactory;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.Units;

import java.util.*;

@CommandName("component_report")
public class ComponentReportQuery implements IQuery {

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public boolean aggressive;

    private static final String HTML_BREAK = "<br>";

    public IResult execute(IProgressListener listener) throws Exception {
        SectionSpec componentReport = new SectionSpec(MessageUtil.format(Messages.ComponentReportQuery_ComponentReport,
                objects.getLabel()));

        Ticks ticks = new Ticks(listener, componentReport.getName(), 31);

        // calculate retained set
        int[] retained = calculateRetainedSize(ticks);

        ticks.tick();

        Histogram histogram = snapshot.getHistogram(retained, ticks);
        if (ticks.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        ticks.tick();

        long totalSize = snapshot.getHeapSize(retained);
        ticks.tick();

        addOverview(componentReport, totalSize, retained, histogram, ticks);

        SectionSpec possibleWaste = new SectionSpec(Messages.ComponentReportQuery_PossibleMemoryWaste);
        componentReport.add(possibleWaste);

        try {
            addDuplicateStrings(possibleWaste, histogram, ticks);
        } catch (UnsupportedOperationException e) { /* ignore, if not supported by heap format */}

        try {
            addEmptyCollections(possibleWaste, totalSize, histogram, ticks);
        } catch (UnsupportedOperationException e) { /* ignore, if not supported by heap format */}

        try {
            addCollectionFillRatios(possibleWaste, totalSize, histogram, ticks);
        } catch (UnsupportedOperationException e) { /* ignore, if not supported by heap format */}

        SectionSpec miscellaneous = new SectionSpec(Messages.ComponentReportQuery_Miscellaneous);
        componentReport.add(miscellaneous);

        try {
            ReferenceMessages msg = new SoftReferenceMessages();
            addReferenceStatistic(miscellaneous, histogram, ticks, "java.lang.ref.SoftReference", msg);
        } catch (UnsupportedOperationException e) { /* ignore, if not supported by heap format */}

        try {
            ReferenceMessages msg = new WeakReferenceMessages();
            addReferenceStatistic(miscellaneous, histogram, ticks, "java.lang.ref.WeakReference", msg);
        } catch (UnsupportedOperationException e) { /* ignore, if not supported by heap format */}

        try {
            addFinalizerStatistic(miscellaneous, retained, ticks);
        } catch (UnsupportedOperationException e) { /* ignore, if not supported by heap format */}

        try {
            addHashMapsCollisionRatios(miscellaneous, totalSize, histogram, ticks);
        } catch (UnsupportedOperationException e) { /* ignore, if not supported by heap format */}

        ticks.delegate.done();

        return componentReport;
    }

    /**
     * Purpose: absorb progress reports from sub-queries to report a smoother
     * overall progress
     */
    private static class Ticks implements IProgressListener {
        IProgressListener delegate;

        public Ticks(IProgressListener delegate, String task, int totalTicks) {
            this.delegate = delegate;
            this.delegate.beginTask(task, totalTicks);
        }

        public void tick() {
            delegate.worked(1);
        }

        public void beginTask(String name, int totalWork) {
            delegate.subTask(name);
        }

        public void subTask(String name) {
            delegate.subTask(name);
        }

        public void done() {
        }

        public boolean isCanceled() {
            return delegate.isCanceled();
        }

        public void sendUserMessage(Severity severity, String message, Throwable exception) {
            delegate.sendUserMessage(severity, message, exception);
        }

        public void setCanceled(boolean value) {
            delegate.setCanceled(true);
        }

        public void worked(int work) {
        }

    }

    // //////////////////////////////////////////////////////////////
    // calculate retained size
    // //////////////////////////////////////////////////////////////

    private int[] calculateRetainedSize(Ticks ticks) throws SnapshotException {
        int[] retained = null;

        List<ExcludedReferencesDescriptor> excludes = new ArrayList<ExcludedReferencesDescriptor>();

        addExcludes(excludes, "java.lang.ref.Finalizer", "referent");
        addExcludes(excludes, "java.lang.ref.PhantomReference", "referent");
        addExcludes(excludes, "java.lang.ref.WeakReference", "referent");
        addExcludes(excludes, "java.lang.ref.SoftReference", "referent");

        if (excludes.isEmpty()) {
            retained = snapshot.getRetainedSet(objects.getIds(ticks), ticks);
        } else {
            retained = snapshot.getRetainedSet(objects.getIds(ticks),
                    excludes.toArray(new ExcludedReferencesDescriptor[0]),
                    ticks);
        }
        return retained;
    }

    private void addExcludes(List<ExcludedReferencesDescriptor> excludes, String className, String... fields)
            throws SnapshotException {
        Collection<IClass> finalizer = snapshot.getClassesByName(className, true);
        if (finalizer != null) {
            ArrayInt objectIds = new ArrayInt();
            for (IClass c : finalizer)
                objectIds.addAll(c.getObjectIds());

            excludes.add(new ExcludedReferencesDescriptor(objectIds.toArray(), fields));
        }
    }

    // //////////////////////////////////////////////////////////////
    // overview section
    // //////////////////////////////////////////////////////////////

    private void addOverview(SectionSpec componentReport, long totalSize, int[] retained, Histogram histogram,
                             Ticks listener) throws Exception {
        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_Overview);
        overview.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());

        addOverviewNumbers(retained, histogram, overview, totalSize);
        listener.tick();
        addOverviewPie(overview, totalSize);
        listener.tick();
        addTopConsumer(overview, retained, listener);
        listener.tick();
        addRetainedSet(overview, histogram);
        listener.tick();

        componentReport.add(overview);
    }

    private void addOverviewNumbers(int[] retained, Histogram histogram, SectionSpec overview, long totalSize) {
        int noOfClasses = histogram.getClassHistogramRecords().size();
        int noOfObjects = retained.length;
        int noOfClassLoaders = histogram.getClassLoaderHistogramRecords().size();

        StringBuilder buf = new StringBuilder();
        buf.append(Messages.ComponentReportQuery_Size + " <strong>")
                .append(Units.Storage.of(totalSize).format(totalSize)).append("</strong> ");
        buf.append(Messages.ComponentReportQuery_Classes + " <strong>")
                .append(Units.Plain.of(noOfClasses).format(noOfClasses)).append("</strong> ");
        buf.append(Messages.ComponentReportQuery_Objects + " <strong>")
                .append(Units.Plain.of(noOfObjects).format(noOfObjects)).append("</strong> ");
        buf.append(Messages.ComponentReportQuery_ClassLoader + " <strong>")
                .append(Units.Plain.of(noOfClassLoaders).format(noOfClassLoaders)).append("</strong>");
        QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Details, new TextResult(buf.toString(), true));
        spec.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        overview.add(spec);
    }

    private void addOverviewPie(SectionSpec overview, long totalSize) {
        PieFactory pie = new PieFactory(snapshot);
        pie.addSlice(-1, objects.getLabel(), totalSize, totalSize);
        QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Distribution, pie.build());
        spec.set(Params.Html.SHOW_HEADING, Boolean.FALSE.toString());
        overview.add(spec);
    }

    private void addTopConsumer(SectionSpec componentReport, int[] retained, IProgressListener listener)
            throws Exception {
        IResult result = SnapshotQuery.lookup("top_consumers_html", snapshot)
                .setArgument("objects", retained)
                .execute(listener);

        QuerySpec topConsumers = new QuerySpec(Messages.ComponentReportQuery_TopConsumers);
        topConsumers.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
        topConsumers.set(Params.Html.COLLAPSED, Boolean.FALSE.toString());
        topConsumers.setResult(result);
        componentReport.add(topConsumers);
    }

    private void addRetainedSet(SectionSpec componentReport, Histogram histogram) {
        QuerySpec retainedSet = new QuerySpec(Messages.ComponentReportQuery_RetainedSet);
        retainedSet.set(Params.Html.SEPARATE_FILE, Boolean.TRUE.toString());
        retainedSet.setResult(histogram);
        componentReport.add(retainedSet);
    }

    // //////////////////////////////////////////////////////////////
    // duplicate strings
    // //////////////////////////////////////////////////////////////

    private void addDuplicateStrings(SectionSpec componentReport, Histogram histogram, Ticks listener) throws Exception {
        InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD");
        for (ClassHistogramRecord record : histogram.getClassHistogramRecords()) {
            if (!"char[]".equals(record.getLabel()))
                continue;

            int[] objectIds = record.getObjectIds();
            if (objectIds.length > 100000) {
                int[] copy = new int[100000];
                System.arraycopy(objectIds, 0, copy, 0, copy.length);
                objectIds = copy;
            }

            RefinedResultBuilder builder = SnapshotQuery.lookup("group_by_value", snapshot)
                    .setArgument("objects", objectIds)
                    .refine(listener);

            builder.setFilter(1, ">=10");
            builder.setInlineRetainedSizeCalculation(true);
            RefinedTable table = (RefinedTable) builder.build();
            TotalsRow totals = new TotalsRow();
            table.calculateTotals(table.getRows(), totals, listener);

            StringBuilder comment = new StringBuilder();
            comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_FoundOccurrences, table.getRowCount(),
                    totals.getLabel(2)));
            comment.append("<p>" + Messages.ComponentReportQuery_TopElementsInclude + "</p><ul>");

            for (int rowId = 0; rowId < table.getRowCount() && rowId < 5; rowId++) {
                Object row = table.getRow(rowId);
                String value = table.getFormattedColumnValue(row, 0);
                if (value.length() > 50)
                    value = value.substring(0, 50) + "...";

                String size = table.getFormattedColumnValue(row, 3);

                comment.append("<li>").append(table.getFormattedColumnValue(row, 1));
                comment.append(" &times; <strong>").append(value).append("</strong> ");
                comment.append(MessageUtil.format(Messages.ComponentReportQuery_Label_Bytes, size)).append("</li>");
            }
            comment.append("</ul>");

            // build result
            SectionSpec duplicateStrings = new SectionSpec(Messages.ComponentReportQuery_DuplicateStrings);
            componentReport.add(duplicateStrings);

            QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                    true));
            spec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
            duplicateStrings.add(spec);

            spec = new QuerySpec(Messages.ComponentReportQuery_Histogram);
            spec.setResult(table);
            duplicateStrings.add(spec);

            listener.tick();

            break;
        }
    }

    // //////////////////////////////////////////////////////////////
    // collection usage
    // //////////////////////////////////////////////////////////////

    private void addEmptyCollections(SectionSpec componentReport, long totalSize, Histogram histogram, Ticks listener)
            throws Exception {
        // Works, but very slow for some dump types, so disable for them.
        if (!aggressive)
            InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD");
        long threshold = totalSize / 20;

        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_EmptyCollections);

        StringBuilder comment = new StringBuilder();
        SectionSpec collectionbySizeSpec = new SectionSpec(Messages.ComponentReportQuery_Details);
        collectionbySizeSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = new HashMapIntObject<CollectionUtil.Info>();
        for (CollectionUtil.Info info : CollectionUtil.getKnownCollections(snapshot)) {
            if (!info.hasSize())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords()) {
            if (metadata.containsKey(record.getClassId())) {
                IClass clazz = (IClass) snapshot.getObject(record.getClassId());

                // run the query: collections by size
                RefinedResultBuilder builder = SnapshotQuery.lookup("collections_grouped_by_size", snapshot)
                        .setArgument("objects", record.getObjectIds())
                        .refine(listener);

                // refine result: sort & evaluate
                builder.setInlineRetainedSizeCalculation(true);
                RefinedTable refinedTable = (RefinedTable) builder.build();

                int count = refinedTable.getRowCount();
                for (int rowId = 0; rowId < count && rowId < 10; rowId++) {
                    Object row = refinedTable.getRow(rowId);
                    int collectionSize = (Integer) refinedTable.getColumnValue(row, 0);

                    if (collectionSize == 0) {
                        long size = Math.abs((Long) refinedTable.getColumnValue(row, 3));
                        if (size > threshold) {
                            int numberOfObjects = (Integer) refinedTable.getColumnValue(row, 1);
                            String retainedSize = refinedTable.getFormattedColumnValue(row, 3);

                            if (comment.length() == 0)
                                comment.append(Messages.ComponentReportQuery_DetectedEmptyCollections + "<ul>");

                            comment.append("<li>");
                            comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_InstancesRetainBytes,
                                    numberOfObjects, clazz.getName(), retainedSize));
                            comment.append("</li>");
                        }

                        break;
                    }
                }

                QuerySpec bySizeSpec = new QuerySpec(clazz.getName());
                bySizeSpec.setResult(refinedTable);
                collectionbySizeSpec.add(bySizeSpec);

                listener.tick();
            }
        }

        if (collectionbySizeSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append(Messages.ComponentReportQuery_Msg_NoExcessiveEmptyCollectionsFound);
        else
            comment.append("</ul>");

        QuerySpec spec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(), true));
        spec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(spec);

        overview.add(collectionbySizeSpec);

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // collection fill ratio
    // //////////////////////////////////////////////////////////////

    private void addCollectionFillRatios(SectionSpec componentReport, long totalSize, Histogram histogram,
                                         Ticks listener) throws Exception {
        // Works, but very slow for some dump types, so disable for them.
        if (!aggressive)
            InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD");
        long threshold = totalSize / 20;

        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_CollectionFillRatios);

        StringBuilder comment = new StringBuilder();
        SectionSpec detailsSpec = new SectionSpec(Messages.ComponentReportQuery_Details);
        detailsSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = new HashMapIntObject<CollectionUtil.Info>();
        for (CollectionUtil.Info info : CollectionUtil.getKnownCollections(snapshot)) {
            if (!info.hasSize() || !info.hasBackingArray())
                continue;

            Collection<IClass> classes = snapshot.getClassesByName(info.getClassName(), true);
            if (classes != null)
                for (IClass clasz : classes)
                    metadata.put(clasz.getObjectId(), info);
        }

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords()) {
            if (metadata.containsKey(record.getClassId())) {
                IClass clazz = (IClass) snapshot.getObject(record.getClassId());

                // run the query: collections by size
                RefinedResultBuilder builder = SnapshotQuery.lookup("collection_fill_ratio", snapshot)
                        .setArgument("objects", record.getObjectIds())
                        .refine(listener);

                // refine result: sort & evaluate
                builder.setInlineRetainedSizeCalculation(true);
                RefinedTable refinedTable = (RefinedTable) builder.build();

                int count = refinedTable.getRowCount();
                for (int rowId = 0; rowId < count; rowId++) {
                    Object row = refinedTable.getRow(rowId);
                    double fillRatio = (Double) refinedTable.getColumnValue(row, 0);

                    if (fillRatio > 0d && fillRatio < 0.21d) {
                        long size = Math.abs((Long) refinedTable.getColumnValue(row, 3));
                        if (size > threshold) {
                            int numberOfObjects = (Integer) refinedTable.getColumnValue(row, 1);
                            String retainedSize = refinedTable.getFormattedColumnValue(row, 3);

                            if (comment.length() == 0)
                                comment.append(Messages.ComponentReportQuery_Msg_DetectedCollectionFillRatios + "<ul>");

                            comment.append("<li>");
                            comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_InstancesRetainBytes,
                                    numberOfObjects, clazz.getName(), retainedSize));
                            comment.append("</li>");
                        }

                        break;
                    }
                }

                QuerySpec spec = new QuerySpec(clazz.getName());
                spec.setResult(refinedTable);
                detailsSpec.add(spec);

                listener.tick();
            }
        }

        if (detailsSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append(Messages.ComponentReportQuery_Msg_NoLowFillRatiosFound);
        else
            comment.append("</ul>");

        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);

        overview.add(commentSpec);
        overview.add(detailsSpec);
        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // hash map collision ratios
    // //////////////////////////////////////////////////////////////

    private void addHashMapsCollisionRatios(SectionSpec componentReport, long totalSize, Histogram histogram, Ticks listener)
            throws Exception {
        // Works, but very slow for some dump types, so disable for them.
        if (!aggressive)
            InspectionAssert.heapFormatIsNot(snapshot, "DTFJ-PHD");
        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_MapCollisionRatios);

        StringBuilder comment = new StringBuilder();
        SectionSpec detailsSpec = new SectionSpec(Messages.ComponentReportQuery_Details);
        detailsSpec.set(Params.Html.COLLAPSED, Boolean.TRUE.toString());

        // prepare meta-data of known collections
        HashMapIntObject<CollectionUtil.Info> metadata = CollectionUtil.getKnownMaps(snapshot);

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords()) {
            if (metadata.containsKey(record.getClassId())) {
                IClass clazz = (IClass) snapshot.getObject(record.getClassId());

                // run the query: collections by size
                int[] objectIds = record.getObjectIds();
                if (objectIds.length > 20000) {
                    int[] copy = new int[20000];
                    System.arraycopy(objectIds, 0, copy, 0, copy.length);
                    objectIds = copy;
                }

                RefinedResultBuilder builder = SnapshotQuery.lookup("map_collision_ratio", snapshot)
                        .setArgument("objects", objectIds)
                        .refine(listener);

                // refine result: sort & evaluate
                builder.setInlineRetainedSizeCalculation(true);
                RefinedTable refinedTable = (RefinedTable) builder.build();

                int count = refinedTable.getRowCount();
                for (int rowId = 0; rowId < count; rowId++) {
                    Object row = refinedTable.getRow(rowId);
                    double collisionRato = (Double) refinedTable.getColumnValue(row, 0);

                    if (collisionRato > 0.79d) {
                        int numberOfObjects = (Integer) refinedTable.getColumnValue(row, 1);
                        String retainedSize = refinedTable.getFormattedColumnValue(row, 3);

                        if (comment.length() == 0)
                            comment.append(Messages.ComponentReportQuery_Msg_DetectedCollisionRatios + "<ul>");

                        comment.append("<li>");
                        comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_InstancesRetainBytes,
                                numberOfObjects, clazz.getName(), retainedSize));
                        comment.append("</li>");
                        break;
                    }
                }

                QuerySpec spec = new QuerySpec(clazz.getName());
                spec.setResult(refinedTable);
                detailsSpec.add(spec);

                listener.tick();
            }
        }

        if (detailsSpec.getChildren().isEmpty())
            return;

        if (comment.length() == 0)
            comment.append(Messages.ComponentReportQuery_Msg_NoCollisionRatiosFound);
        else
            comment.append("</ul>");

        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);

        overview.add(commentSpec);
        overview.add(detailsSpec);
        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // weak/soft reference statistics
    // //////////////////////////////////////////////////////////////
    private static abstract class ReferenceMessages {
        public String ReferenceStatistics;
        public String Msg_NoReferencesFound;
        public String NoAliveReferences;
        public String HistogramOfReferences;
        public String ReferenceStatQuery_Label_Referenced;
        public String ReferenceStatQuery_Label_Retained;
        public String ReferenceStatQuery_Label_StronglyRetainedReferents;
        public String Msg_ReferencesFound;
        public String Msg_ReferencesRetained;
        public String Msg_ReferencesStronglyRetained;
    }

    private static class SoftReferenceMessages extends ReferenceMessages {
        {
            ReferenceStatistics = Messages.ComponentReportQuery_SoftReferenceStatistics;
            Msg_NoReferencesFound = Messages.ComponentReportQuery_Msg_NoSoftReferencesFound;
            NoAliveReferences = Messages.ComponentReportQuery_Msg_NoAliveSoftReferences;
            HistogramOfReferences = Messages.ComponentReportQuery_HistogramOfSoftReferences;
            ReferenceStatQuery_Label_Referenced = Messages.SoftReferenceStatQuery_Label_Referenced;
            ReferenceStatQuery_Label_Retained = Messages.SoftReferenceStatQuery_Label_Retained;
            ReferenceStatQuery_Label_StronglyRetainedReferents = Messages.SoftReferenceStatQuery_Label_StronglyRetainedReferents;
            Msg_ReferencesFound = Messages.ComponentReportQuery_Msg_SoftReferencesFound;
            Msg_ReferencesRetained = Messages.ComponentReportQuery_Msg_SoftReferencesRetained;
            Msg_ReferencesStronglyRetained = Messages.ComponentReportQuery_Msg_SoftReferencesStronglyRetained;
        }
    }

    private static class WeakReferenceMessages extends ReferenceMessages {
        {
            ReferenceStatistics = Messages.ComponentReportQuery_WeakReferenceStatistics;
            Msg_NoReferencesFound = Messages.ComponentReportQuery_Msg_NoWeakReferencesFound;
            NoAliveReferences = Messages.ComponentReportQuery_Msg_NoAliveWeakReferences;
            HistogramOfReferences = Messages.ComponentReportQuery_HistogramOfWeakReferences;
            ReferenceStatQuery_Label_Referenced = Messages.WeakReferenceStatQuery_Label_Referenced;
            ReferenceStatQuery_Label_Retained = Messages.WeakReferenceStatQuery_Label_Retained;
            ReferenceStatQuery_Label_StronglyRetainedReferents = Messages.WeakReferenceStatQuery_Label_StronglyRetainedReferents;
            Msg_ReferencesFound = Messages.ComponentReportQuery_Msg_WeakReferencesFound;
            Msg_ReferencesRetained = Messages.ComponentReportQuery_Msg_WeakReferencesRetained;
            Msg_ReferencesStronglyRetained = Messages.ComponentReportQuery_Msg_WeakReferencesStronglyRetained;
        }
    }

    private void addReferenceStatistic(SectionSpec componentReport, Histogram histogram, Ticks ticks, String className, ReferenceMessages messages)
            throws SnapshotException {
        Collection<IClass> classes = snapshot.getClassesByName(className, true);
        if (classes == null || classes.isEmpty()) {
            addEmptyResult(componentReport, messages.ReferenceStatistics,
                    messages.Msg_NoReferencesFound);
            return;
        }

        SetInt softRefClassIds = new SetInt(classes.size());
        for (IClass c : classes)
            softRefClassIds.add(c.getObjectId());

        ArrayList<ClassHistogramRecord> softRefs = new ArrayList<ClassHistogramRecord>();
        long numObjects = 0, heapSize = 0;
        ArrayInt instanceSet = new ArrayInt();
        SetInt referentSet = new SetInt();

        for (ClassHistogramRecord record : histogram.getClassHistogramRecords()) {
            if (softRefClassIds.contains(record.getClassId())) {
                softRefs.add(record);
                numObjects += record.getNumberOfObjects();
                heapSize += record.getUsedHeapSize();

                instanceSet.addAll(record.getObjectIds());

                for (int objectId : record.getObjectIds()) {
                    IInstance obj = (IInstance) snapshot.getObject(objectId);

                    ObjectReference ref = ReferenceQuery.getReferent(obj);
                    if (ref != null)
                        referentSet.add(ref.getObjectId());
                }
            }
        }

        if (instanceSet.isEmpty()) {
            addEmptyResult(componentReport, messages.ReferenceStatistics,
                    messages.NoAliveReferences);
            return;
        }

        Histogram softRefHistogram = new Histogram(messages.HistogramOfReferences, softRefs,
                null, numObjects, heapSize, 0);

        CompositeResult referents = ReferenceQuery.execute(instanceSet, referentSet, snapshot,
                messages.ReferenceStatQuery_Label_Referenced,
                messages.ReferenceStatQuery_Label_Retained,
                messages.ReferenceStatQuery_Label_StronglyRetainedReferents, "referent", ticks);

        StringBuilder comment = new StringBuilder();
        comment.append(MessageUtil.format(messages.Msg_ReferencesFound, instanceSet.size(),
                referentSet.size())).append(HTML_BREAK);

        Histogram onlySoftlyReachable = (Histogram) referents.getResultEntries().get(1).getResult();
        numObjects = 0;
        heapSize = 0;
        for (ClassHistogramRecord r : onlySoftlyReachable.getClassHistogramRecords()) {
            numObjects += r.getNumberOfObjects();
            heapSize += r.getUsedHeapSize();
        }
        comment.append(MessageUtil.format(messages.Msg_ReferencesRetained,
                numObjects,
                Units.Storage.of(heapSize).format(heapSize))).append(HTML_BREAK);

        Histogram stronglyReachableReferents = (Histogram) referents.getResultEntries().get(2).getResult();
        numObjects = 0;
        heapSize = 0;
        for (ClassHistogramRecord r : stronglyReachableReferents.getClassHistogramRecords()) {
            numObjects += r.getNumberOfObjects();
            heapSize += r.getUsedHeapSize();
        }
        if (numObjects >= 1) {
            comment.append("<strong>").append(MessageUtil.format(Messages.ComponentReportQuery_PossibleMemoryLeak)).append("</strong> ");
        }
        comment.append(MessageUtil.format(messages.Msg_ReferencesStronglyRetained,
                numObjects,
                Units.Storage.of(heapSize).format(heapSize)));

        SectionSpec overview = new SectionSpec(messages.ReferenceStatistics);
        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(commentSpec);

        QuerySpec child = new QuerySpec(softRefHistogram.getLabel(), softRefHistogram);
        child.set(Params.Rendering.DERIVED_DATA_COLUMN, "_default_=" + RetainedSizeDerivedData.APPROXIMATE.getCode());
        overview.add(child);

        for (CompositeResult.Entry entry : referents.getResultEntries()) {
            child = new QuerySpec(entry.getName(), entry.getResult());
            overview.add(child);
        }

        if (numObjects >= 1) {
            // convert excludes into the required format
            Set<String> fields = Collections.singleton("referent");
            Map<IClass, Set<String>> excludeMap = new HashMap<IClass, Set<String>>();
            for (IClass c : classes)
                excludeMap.put(c, fields);

            // Add all the suspect referents
            ArrayInt ai = new ArrayInt();
            for (ClassHistogramRecord r : stronglyReachableReferents.getClassHistogramRecords()) {
                ai.addAll(r.getObjectIds());
            }

            // calculate the shortest path for each object
            IMultiplePathsFromGCRootsComputer computer = snapshot.getMultiplePathsFromGCRoots(ai.toArray(),
                    excludeMap);

            // Display the paths
            IResultTree r = MultiplePath2GCRootsQuery.create(snapshot, computer, null);
            child = new QuerySpec(Messages.ComponentReportQuery_PathsToReferents, r);
            commentSpec.set(Params.Html.IS_IMPORTANT, Boolean.TRUE.toString());
            overview.add(child);
        }

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // finalizer statistics
    // //////////////////////////////////////////////////////////////

    private void addFinalizerStatistic(SectionSpec componentReport, int[] retained, Ticks ticks)
            throws SnapshotException {
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.ref.Finalizer", true);
        if (classes == null || classes.isEmpty()) {
            addEmptyResult(componentReport, Messages.ComponentReportQuery_FinalizerStatistics,
                    Messages.ComponentReportQuery_Msg_NoFinalizerObjects);
            return;
        }

        SetInt retainedSet = new SetInt((retained.length / 100) * 110);
        for (int ii = 0; ii < retained.length; ii++)
            retainedSet.add(retained[ii]);

        // Avoid duplicates from the two approaches by using a set
        SetInt finalizers = new SetInt();

        for (IClass c : classes) {
            for (int objectId : c.getObjectIds()) {
                IInstance obj = (IInstance) snapshot.getObject(objectId);

                ObjectReference ref = ReferenceQuery.getReferent(obj);
                if (ref != null) {
                    int referentId = ref.getObjectId();
                    if (retainedSet.contains(referentId)) {
                        finalizers.add(referentId);
                    }
                }
            }
        }

        // Add other objects marked as not yet finalized
        for (int root : snapshot.getGCRoots()) {
            GCRootInfo ifo[] = snapshot.getGCRootInfo(root);
            for (GCRootInfo rootInfo : ifo) {
                if (rootInfo.getType() == GCRootInfo.Type.UNFINALIZED
                        || rootInfo.getType() == GCRootInfo.Type.FINALIZABLE) {
                    int referentId = rootInfo.getObjectId();
                    if (retainedSet.contains(referentId)) {
                        finalizers.add(referentId);
                    }
                    break;
                }
            }
        }

        if (finalizers.isEmpty()) {
            addEmptyResult(componentReport, Messages.ComponentReportQuery_FinalizerStatistics,
                    Messages.ComponentReportQuery_Msg_NoFinalizerFound);
            return;
        }

        SectionSpec overview = new SectionSpec(Messages.ComponentReportQuery_FinalizerStatistics);
        StringBuilder comment = new StringBuilder();
        comment.append(MessageUtil.format(Messages.ComponentReportQuery_Msg_TotalFinalizerMethods, finalizers.size()));

        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(comment.toString(),
                true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        overview.add(commentSpec);

        Histogram histogram = snapshot.getHistogram(finalizers.toArray(), ticks);
        if (ticks.isCanceled())
            throw new IProgressListener.OperationCanceledException();
        histogram.setLabel(Messages.ComponentReportQuery_HistogramFinalizeMethod);
        overview.add(new QuerySpec(histogram.getLabel(), histogram));

        componentReport.add(overview);
    }

    // //////////////////////////////////////////////////////////////
    // internal utilitiy methods
    // //////////////////////////////////////////////////////////////

    private void addEmptyResult(SectionSpec report, String sectionLabel, String message) {
        SectionSpec section = new SectionSpec(sectionLabel);
        QuerySpec commentSpec = new QuerySpec(Messages.ComponentReportQuery_Comment, new TextResult(message, true));
        commentSpec.set(Params.Rendering.PATTERN, Params.Rendering.PATTERN_OVERVIEW_DETAILS);
        section.add(commentSpec);
        report.add(section);
    }
}
