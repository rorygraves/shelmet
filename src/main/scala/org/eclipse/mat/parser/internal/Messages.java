/*******************************************************************************
 * Copyright (c) 2009, 2011 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - validation of indices
 *******************************************************************************/
package org.eclipse.mat.parser.internal;

public class Messages {
    public static String AbstractObjectImpl_Error_FieldContainsIllegalReference="Field ''{0}'' of ''{1}'' contains an illegal object reference: 0x{2}";
    public static String AbstractObjectImpl_Error_FieldIsNotReference="Field ''{0}'' of ''{1}'' is not an object reference. It cannot have a field ''{2}''";
    public static String BitOutputStream_Error_ArrayFull="Array full";
    public static String ClassHistogramRecordBuilder_Error_IllegalUseOfHistogramBuilder="illegal use of class histogram record builder";
    public static String DominatorTree_CalculateRetainedSizes="Calculate retained sizes";
    public static String DominatorTree_CalculatingDominatorTree="Calculating Dominator Tree";
    public static String DominatorTree_ComputingDominators="Computing dominators";
    public static String DominatorTree_CreateDominatorsIndexFile="Create dominators index file";
    public static String DominatorTree_DepthFirstSearch="Depth-first search";
    public static String DominatorTree_DominatorTreeCalculation="Dominator Tree calculation";
    public static String Function_Error_NeedsNumberAsInput="''{0}'' yields ''{1}'' of type ''{2}'' which is not a number and hence is not supported by the built-in function ''{3}''.";
    public static String Function_ErrorNoFunction="''{0}'' yields ''{1}'' of type ''{2}'' which is not supported by the built-in function ''{3}''.";
    public static String Function_unknown="unknown";
    public static String GarbageCleaner_ReIndexingClasses="Re-indexing classes";
    public static String GarbageCleaner_ReIndexingObjects="Re-indexing objects";
    public static String GarbageCleaner_ReIndexingOutboundIndex="Re-indexing outbound index";
    public static String GarbageCleaner_RemovedUnreachableObjects="Removed {0} unreachable objects using {1} bytes";
    public static String GarbageCleaner_RemovingUnreachableObjects="Removing unreachable objects";
    public static String GarbageCleaner_SearchingForUnreachableObjects="Searching for unreachable objects";
    public static String GarbageCleaner_Writing="Writing {0}";
    public static String HistogramBuilder_Error_FailedToStoreInHistogram="Failed to store class data in histogram! Class data for this class id already stored in histogram!";
    public static String IndexReader_Error_IndexIsEmbedded="Index is embedded; stream must be set externally";
    public static String IndexWriter_Error_ObjectArrayLength="Requested length of new Object[{0}] exceeds limit of {1}";
    public static String IndexWriter_Error_ArrayLength="Requested length of new long[{0}] exceeds limit of {1}";
    public static String MethodCallExpression_Error_MethodNotFound="Method {0}({1}) not found in object {2} of type {3}";
    public static String MultiplePathsFromGCRootsComputerImpl_FindingPaths="Finding paths";
    public static String SnapshotFactoryImpl_EmptyOutbounds="Empty outbounds for index {0} address {1} type {2}";
    public static String SnapshotFactoryImpl_Error_NoParserRegistered="No parser registered for file ''{0}''";
    public static String SnapshotFactoryImpl_Error_OpeningHeapDump="Error opening heap dump ''{0}''. Check the error log for further details.";
    public static String SnapshotFactoryImpl_Error_ReparsingHeapDump="Reparsing heap dump file due to {0}";
    public static String SnapshotFactoryImpl_ErrorOpeningHeapDump="Error opening heap dump ''{0}''";
    public static String SnapshotFactoryImpl_ReparsingHeapDumpAsIndexOutOfDate="Reparsing heap dump file ''{0}'' modified at {1} as it is newer than index file ''{2}'' modified at {3}";
    public static String SnapshotFactoryImpl_ReparsingHeapDumpWithOutOfDateIndex="Reparsing heap dump file due to out of date index file";
    public static String SnapshotFactoryImpl_IndexAddressHasSameAddressAsPrevious="Index {0} type {1} has same address {2} type {3} as previous index";
    public static String SnapshotFactoryImpl_IndexAddressIsSmallerThanPrevious="Index {0} type {1} address {2} is smaller than previous address {3}";
    public static String SnapshotFactoryImpl_IndexAddressFoundAtOtherID="Index {0} address {1} found at index {2} type {3} or type {4}";
    public static String SnapshotFactoryImpl_ClassIDNotFound="Class id not found for index {0} address {1}, class id {2}";
    public static String SnapshotFactoryImpl_ClassImplNotFound="ClassImpl not found for index {0} address {1} class id {2}";
    public static String SnapshotFactoryImpl_IndexAddressNegativeArraySize="Index {0} address {1} negative size {2} type name {3}";
    public static String SnapshotFactoryImpl_InvalidFirstOutbound="Object at index {0} address {1} type {2} has first outbound index {3} address {4} which is not its class index {5}";
    public static String SnapshotFactoryImpl_InvalidOutbound="Object at index {0} address {1} type {2} has outbounds[{3}] with an invalid index {4}";
    public static String SnapshotFactoryImpl_ClassIndexAddressNotEqualClassObjectAddress="Class index {0} address {1} not equal to class object address {2} name {3}";
    public static String SnapshotFactoryImpl_ClassIndexNotEqualClassObjectID="Class index {0} address {1} not equal to class object id {2} name {3}";
    public static String SnapshotFactoryImpl_ClassIndexAddressTypeIDNotEqualClassImplClassId="Class index {0} address {1} class id1 {2} ClassImpl class id {3} name {4}";
    public static String SnapshotFactoryImpl_ClassIndexAddressNoLoaderID="Class index {0} address {1} clsId {2} no loader id {3} address {4} class name {5}";
    public static String SnapshotFactoryImpl_ObjectsFoundButClassesHadObjectsAndClassesInTotal="{0} objects found but {1} classes had {2} objects and class objects in total";
    public static String SnapshotFactoryImpl_GCRootIDOutOfRange="GC root id {0} out of range [0,{1})";
    public static String SnapshotFactoryImpl_GCRootIDDoesNotMatchIndex="GC root info object id {0} does not match index {1}";
    public static String SnapshotFactoryImpl_GCThreadIDOutOfRange="GC root thread id {0} out of range [0,{1})";
    public static String SnapshotFactoryImpl_GCThreadRootIDDoesNotMatchIndex="GC root thread id {0} info object id {1} does not match index {2}";
    public static String SnapshotFactoryImpl_GCThreadRootIDOutOfRange="GC root thread id {0} object id {1} out of range [0,{2})";
    public static String SnapshotFactoryImpl_NoOutbounds="No outbounds for index {0} address {1} type {2}";
    public static String SnapshotFactoryImpl_ObjDescClass="class {0}";
    public static String SnapshotFactoryImpl_ObjDescObjType="{0} {1}";
    public static String SnapshotFactoryImpl_ObjDescObjTypeAddress="object type address {0}";
    public static String SnapshotFactoryImpl_UnableToDeleteIndexFile="Unable to delete index file {0}";
    public static String SnapshotFactoryImpl_ValidatingGCRoots="Validating GC roots";
    public static String SnapshotFactoryImpl_ValidatingIndices="Validating indices";
    public static String SnapshotImpl_BuildingHistogram="building histogram";
    public static String SnapshotImpl_Error_DomTreeNotAvailable="Dominator tree not available. Open the Dominator Tree or delete indices and parse again.";
    public static String SnapshotImpl_Error_ObjectNotFound="Object {0} not found.";
    public static String SnapshotImpl_Error_ParserNotFound="Heap Parser not found: ";
    public static String SnapshotImpl_Error_ReplacingNonExistentClassLoader="Replacing a non-existent class loader label.";
    public static String SnapshotImpl_Error_UnknownVersion="Unknown version: {0}";
    public static String SnapshotImpl_Error_UnrecognizedState="Unrecognized state : ";
    public static String SnapshotImpl_Histogram="Histogram";
    public static String SnapshotImpl_Label="label";
    public static String SnapshotImpl_ReadingInboundReferrers="reading inbound referrers";
    public static String SnapshotImpl_ReadingOutboundReferrers="reading outbound referrers";
    public static String SnapshotImpl_ReopeningParsedHeapDumpFile="Reopening parsed heap dump file";
    public static String SnapshotImpl_RetrievingDominators="Retrieving dominators...";
    public static String ObjectArrayImpl_forArray="{0} for array {1}";
    public static String ObjectMarker_MarkingObjects="Marking reachable objects";
    public static String Operation_Error_ArgumentOfUnknownClass="right argument to IN of unknown class {0}";
    public static String Operation_Error_CannotCompare="IN: cannot compare left argument of type {0} to int[]";
    public static String Operation_Error_NotInArgumentOfUnknownClass="right argument to NOT IN of unknown class {0}";
    public static String Operation_Error_NotInCannotCompare="NOT IN: cannot compare left argument of type {0} to int[]";
    public static String Operation_ErrorNoComparable="''{0}'' yields ''{1}'' of type ''{2}'' which does not implement Comparable and hence does not support the {3} operation.";
    public static String Operation_ErrorNotNumber="''{0}'' yields ''{1}'' of type ''{2}'' which is not a number and hence does not support the {3} operation";
    public static String OQLQueryImpl_CheckingClass="Checking class {0}";
    public static String OQLQueryImpl_CollectingObjects="Collecting objects of classes";
    public static String OQLQueryImpl_Error_CannotCalculateRetainedSet="Cannot calculate retained set on {0}";
    public static String OQLQueryImpl_Error_ClassCastExceptionOccured="ClassCastException occurred. Remember: sub queries with the modifier INSTANCEOF or without the modifier OBJECTS must return only class objects.";
    public static String OQLQueryImpl_Error_ElementIsNotClass="Element is not a class: {0}";
    public static String OQLQueryImpl_Error_InvalidClassNamePattern="Invalid class name pattern {0}";
    public static String OQLQueryImpl_Error_MissingSnapshot="Missing snapshot";
    public static String OQLQueryImpl_Error_MustReturnObjectList="Sub-Select must return an object list: {0}";
    public static String OQLQueryImpl_Error_QueryCannotBeConverted="Query cannot be converted into object list: {0}";
    public static String OQLQueryImpl_Error_QueryMustHaveIdenticalSelectItems="UNION query must have identical select items: {0}";
    public static String OQLQueryImpl_Error_QueryMustReturnObjects="UNION query must return objects: {0}";
    public static String OQLQueryImpl_Error_ResultMustReturnObjectList="Result must return an object list: Query: {0} Value: {1}";
    public static String OQLQueryImpl_Errot_IsNotClass="Object 0x{0} is not a class";
    public static String OQLQueryImpl_SelectingObjects="Selecting objects of classes";
    public static String ParserRegistry_ErrorCompilingFileNamePattern="Error compiling file name pattern of extension {0}";
    public static String ParserRegistry_ErrorWhileCreating="Error while creating {0} ''{1}''";
    public static String PathExpression_Error_ArrayHasNoProperty="The array of type {0} has no property {1}";
    public static String PathExpression_Error_TypeHasNoProperty="Type {0} has no property {1}";
    public static String PathExpression_Error_UnknownElementInPath="Unknown element in path {0}";
    public static String PositionInputStream_mark="mark";
    public static String PositionInputStream_reset="reset";
    public static String PositionInputStream_seek="seek";
    public static String RetainedSizeCache_ErrorReadingRetainedSizes="Error reading pre-calculated retained sizes. Re-calculating...";
    public static String RetainedSizeCache_Warning_IgnoreError="Ignoring error while storing calculated retained size";
    public static String OQLParser_Encountered_X_at_line_X_column_X_Was_expecting_one_of_X="Encountered \"{0}\" at line {1}, column {2}.\nWas expecting one of: {3}";
    public static String OQLParser_Missing_return_statement_in_function="Missing return statement in function";

    private Messages() {
    }
}
