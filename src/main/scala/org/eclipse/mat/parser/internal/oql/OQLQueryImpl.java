/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - bug fixes for instanceof
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.*;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.oql.compiler.CompilerImpl;
import org.eclipse.mat.parser.internal.oql.compiler.EvaluationContext;
import org.eclipse.mat.parser.internal.oql.compiler.Expression;
import org.eclipse.mat.parser.internal.oql.compiler.Query;
import org.eclipse.mat.parser.internal.oql.compiler.Query.SelectItem;
import org.eclipse.mat.parser.internal.oql.parser.OQLParser;
import org.eclipse.mat.parser.internal.oql.parser.ParseException;
import org.eclipse.mat.parser.internal.oql.parser.TokenMgrError;
import org.eclipse.mat.query.*;
import org.eclipse.mat.snapshot.IOQLQuery;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.OQL;
import org.eclipse.mat.snapshot.OQLParseException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.*;

import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class OQLQueryImpl implements IOQLQuery {
    Query query;
    EvaluationContext ctx;

    // //////////////////////////////////////////////////////////////
    // result set implementations
    // //////////////////////////////////////////////////////////////

    private interface CustomTableResultSet extends IOQLQuery.Result, IResultTable {
    }

    /**
     * Result from a select with select list where the from clause returned a list or array of objects.
     */
    private static class ObjectResultSet implements CustomTableResultSet {
        private static final Object NULL_VALUE = new Object();

        private static class ValueHolder {
            Object subject;
            Object[] values;

            public ValueHolder(Object subject, Object[] values) {
                this.subject = subject;
                this.values = values;
            }
        }

        OQLQueryImpl source;

        Column[] columns;
        Object[] objects;

        ObjectResultSet(OQLQueryImpl source, List<Object> objects) throws SnapshotException {
            this(source, objects.toArray());
        }

        ObjectResultSet(OQLQueryImpl source, Object[] objects) throws SnapshotException {
            this.source = source;
            this.objects = objects;

            List<SelectItem> selectList = source.query.getSelectClause().getSelectList();
            columns = new Column[selectList.size()];

            try {
                for (int ii = 0; ii < columns.length; ii++)
                    columns[ii] = buildColumn(selectList.get(ii), getColumnValue(0, ii));
            } catch (RuntimeException e) {
                throw SnapshotException.rethrow(e);
            }
        }

        public ResultMetaData getResultMetaData() {
            return null;
        }

        public Column[] getColumns() {
            return columns;
        }

        public int getRowCount() {
            return objects.length;
        }

        public Object getColumnValue(Object row, int columnIndex) {
            int index = (Integer) row;
            if (!(objects[index] instanceof ValueHolder))
                resolve(index);

            ValueHolder holder = ((ValueHolder) objects[index]);

            if (holder.values[columnIndex] == null) {
                try {
                    source.ctx.setSubject(holder.subject);
                    Query.SelectItem column = source.query.getSelectClause().getSelectList().get(columnIndex);
                    Object v = column.getExpression().compute(source.ctx);

                    holder.values[columnIndex] = v == null ? NULL_VALUE : v;
                } catch (SnapshotException e) {
                    throw new RuntimeException(e);
                }
            }

            return holder.values[columnIndex] == NULL_VALUE ? null : holder.values[columnIndex];
        }

        public IContextObjectSet getContext(Object row) {
            final int index = (Integer) row;
            if (!(objects[index] instanceof ValueHolder))
                resolve(index);

            if (((ValueHolder) objects[index]).subject instanceof IObject) {
                return new IContextObjectSet() {
                    public int getObjectId() {
                        return ((IObject) ((ValueHolder) objects[index]).subject).getObjectId();
                    }

                    public int[] getObjectIds() {
                        return new int[]{getObjectId()};
                    }

                    public String getOQL() {
                        String alias = source.query.getFromClause().getAlias();
                        String alias2;
                        if (alias == null)
                            alias2 = "";
                        else
                            alias2 = " " + alias;
                        return "SELECT " + source.query.getSelectClause().toString() + " FROM OBJECTS " + getObjectId() + alias2;
                    }
                };
            } else {
                return null;
            }
        }

        public Object getRow(int index) {
            return index;
        }

        public String getOQLQuery() {
            return source.toString();
        }

        private void resolve(int index) {
            ValueHolder answer = new ValueHolder(objects[index], new Object[columns.length]);
            objects[index] = answer;
        }
    }

    /**
     * Result from a select with select list where the from clause returned an array of object IDs.
     */
    private static class ResultSet implements CustomTableResultSet {
        private static final Object NULL_VALUE = new Object();

        private static class ValueHolder {
            Object[] values;

            public ValueHolder(Object[] values) {
                this.values = values;
            }
        }

        OQLQueryImpl source;

        Column[] columns;
        int[] objectIds;
        ValueHolder[] objects;

        public ResultSet(OQLQueryImpl source, int[] objectIds) throws SnapshotException {
            this.source = source;
            this.objectIds = objectIds;
            this.objects = new ValueHolder[objectIds.length];

            List<SelectItem> selectList = source.query.getSelectClause().getSelectList();
            columns = new Column[selectList.size()];
            try {
                for (int ii = 0; ii < columns.length; ii++)
                    columns[ii] = buildColumn(selectList.get(ii), getColumnValue(0, ii));
            } catch (RuntimeException e) {
                throw SnapshotException.rethrow(e);
            }

        }

        public ResultMetaData getResultMetaData() {
            return null;
        }

        public int getRowCount() {
            return objectIds.length;
        }

        public Object getColumnValue(Object row, int columnIndex) {
            int index = (Integer) row;

            if (objects[index] == null)
                objects[index] = new ValueHolder(new Object[columns.length]);

            if (objects[index].values[columnIndex] == null) {
                // each column value is calculated separately, because I do not
                // want sorting to resolve all values in all rows

                // NULL_VALUE is used to keep track of column values which have
                // been calculated but returned a null value

                try {
                    IObject object = source.ctx.getSnapshot().getObject(objectIds[index]);
                    source.ctx.setSubject(object);
                    List<SelectItem> selectList = source.query.getSelectClause().getSelectList();
                    Object value = selectList.get(columnIndex).getExpression().compute(source.ctx);

                    objects[index].values[columnIndex] = value != null ? value : NULL_VALUE;
                } catch (SnapshotException e) {
                    throw new RuntimeException(e);
                }
            }

            Object value = objects[index].values[columnIndex];
            return value == NULL_VALUE ? null : value;
        }

        public IContextObjectSet getContext(final Object row) {
            return new IContextObjectSet() {
                public int getObjectId() {
                    return objectIds[(Integer) row];
                }

                public int[] getObjectIds() {
                    return new int[]{getObjectId()};
                }

                public String getOQL() {
                    String alias = source.query.getFromClause().getAlias();
                    String alias2;
                    if (alias == null)
                        alias2 = "";
                    else
                        alias2 = " " + alias;
                    return "SELECT " + source.query.getSelectClause().toString() + " FROM OBJECTS " + getObjectId() + alias2;
                }
            };
        }

        public Object getRow(int index) {
            return index;
        }

        public Column[] getColumns() {
            return columns;
        }

        public String getOQLQuery() {
            return source.query.toString();
        }
    }

    private static Column buildColumn(SelectItem column, Object columnValue) {
        String name = column.getName();
        if (name == null)
            name = column.getExpression().toString();

        Class<?> type = columnValue != null ? columnValue.getClass() : Object.class;
        return new Column(name, type).noTotals();
    }

    private static class UnionResultSet implements Result, IResultTable {
        private static class ValueHolder {
            IResultTable source;
            Object row;

            public ValueHolder(IResultTable source, Object row) {
                this.source = source;
                this.row = row;
            }

        }

        int size = 0;
        List<CustomTableResultSet> resultSets = new ArrayList<CustomTableResultSet>();
        ArrayInt sizes = new ArrayInt(5);

        public void addResultSet(CustomTableResultSet resultSet) {
            sizes.add(size);
            size += resultSet.getRowCount();
            resultSets.add(resultSet);
        }

        public ResultMetaData getResultMetaData() {
            return null;
        }

        public Column[] getColumns() {
            return resultSets.get(0).getColumns();
        }

        public int getRowCount() {
            return size;
        }

        public Object getRow(int index) {
            int ii = findPageFor(index);
            IResultTable rs = resultSets.get(ii);
            Object value = rs.getRow(index - sizes.get(ii));
            return new ValueHolder(rs, value);
        }

        public Object getColumnValue(Object row, int columnIndex) {
            ValueHolder holder = (ValueHolder) row;
            return holder.source.getColumnValue(holder.row, columnIndex);
        }

        public IContextObject getContext(Object row) {
            ValueHolder holder = (ValueHolder) row;
            return holder.source.getContext(holder.row);
        }

        private int findPageFor(int rowNo) {
            int pageIndex = 0;
            while (pageIndex + 1 < sizes.size() && rowNo >= sizes.get(pageIndex + 1))
                pageIndex++;
            return pageIndex;
        }

        public String getOQLQuery() {
            StringBuilder buf = new StringBuilder();
            for (Result resultSet : resultSets)
                OQL.union(buf, resultSet.getOQLQuery());
            return buf.toString();
        }
    }

    private interface IntIterator {
        int nextInt();

        boolean hasNext();
    }

    private interface IntResult {
        void add(int id);

        void addAll(int[] ids);

        void addAll(IntResult intResult);

        int size();

        int[] toArray();

        boolean isEmpty();

        IntIterator iterator();
    }

    private static class IntArrayResult implements IntResult {
        ArrayInt arrayInt;

        public IntArrayResult(int capacity) {
            this.arrayInt = new ArrayInt(capacity);
        }

        public IntArrayResult(int[] initialValues) {
            this.arrayInt = new ArrayInt(initialValues);
        }

        public IntArrayResult(ArrayInt values) {
            this.arrayInt = values;
        }

        public void add(int id) {
            this.arrayInt.add(id);
        }

        public void addAll(int[] ids) {
            this.arrayInt.addAll(ids);
        }

        public void addAll(IntResult intResult) {
            if (intResult instanceof IntArrayResult) {
                this.arrayInt.addAll(((IntArrayResult) intResult).arrayInt);
            } else {
                for (IntIterator iter = intResult.iterator(); iter.hasNext(); )
                    this.arrayInt.add(iter.nextInt());
            }
        }

        public int size() {
            return this.arrayInt.size();
        }

        public int[] toArray() {
            return this.arrayInt.toArray();
        }

        public boolean isEmpty() {
            return this.arrayInt.isEmpty();
        }

        public IntIterator iterator() {
            return new IntIterator() {

                int nextIndex = 0;

                public boolean hasNext() {
                    return nextIndex < arrayInt.size();
                }

                public int nextInt() {
                    return arrayInt.get(nextIndex++);
                }

            };
        }

    }

    private static class IntSetResult implements IntResult {
        SetInt setInt;

        public IntSetResult(int capacity) {
            this.setInt = new SetInt(capacity);
        }

        public void add(int id) {
            this.setInt.add(id);
        }

        public void addAll(int[] ids) {
            for (int id : ids)
                this.setInt.add(id);
        }

        public void addAll(IntResult intResult) {
            for (IntIterator iter = intResult.iterator(); iter.hasNext(); )
                this.setInt.add(iter.nextInt());
        }

        public int size() {
            return this.setInt.size();
        }

        public int[] toArray() {
            return this.setInt.toArray();
        }

        public boolean isEmpty() {
            return this.setInt.isEmpty();
        }

        public IntIterator iterator() {
            return new IntIterator() {

                IteratorInt intEnum = setInt.iterator();

                public boolean hasNext() {
                    return intEnum.hasNext();
                }

                public int nextInt() {
                    return intEnum.next();
                }

            };
        }

    }

    // //////////////////////////////////////////////////////////////
    // oql execution
    // //////////////////////////////////////////////////////////////

    public OQLQueryImpl(EvaluationContext parent, Query query) {
        init(parent, query);
    }

    public OQLQueryImpl(String queryString) throws OQLParseException {
        try {
            OQLParser p = new OQLParser(new StringReader(queryString));
            p.setCompiler(new CompilerImpl());
            Query query = p.ParseQuery();

            init(null, query);
        } catch (ParseException e) {
            int line = e.currentToken.next.beginLine;
            int column = e.currentToken.next.beginColumn;

            // stack of no additional use but clutters UI
            throw new OQLParseException(e.getMessage(), null, line, column);
        } catch (TokenMgrError e) {
            String msg = e.getMessage();
            int line = 1, column = 1;

            Pattern pattern = Pattern.compile("Lexical error at line ([0-9]*), column ([0-9]*)\\..*");
            Matcher matcher = pattern.matcher(msg);
            if (matcher.matches()) {
                line = Integer.parseInt(matcher.group(1));
                column = Integer.parseInt(matcher.group(2));
            }

            // stack of no additional use but clutters UI
            throw new OQLParseException(msg, null, line, column);
        }
    }

    private void init(EvaluationContext parent, Query query) {
        this.query = query;

        this.ctx = new EvaluationContext(parent);

        if (query.getFromClause() != null)
            this.ctx.setAlias(query.getFromClause().getAlias());
    }

    private void initSnapshot(ISnapshot snapshot) {
        this.ctx.setSnapshot(snapshot);
    }

    public Object execute(ISnapshot snapshot, IProgressListener monitor) throws SnapshotException {
        if (snapshot == null)
            throw new NullPointerException(Messages.OQLQueryImpl_Error_MissingSnapshot);
        initSnapshot(snapshot);

        if (monitor == null)
            monitor = new VoidProgressListener();

        Object result = internalExecute(monitor);
        return result instanceof IntResult ? ((IntResult) result).toArray() : result;
    }

    protected Object internalExecute(IProgressListener monitor) throws SnapshotException {
        int percentages[] = new int[(1 + (query.getUnionQueries() != null ? query.getUnionQueries().size() : 0))];
        Arrays.fill(percentages, 100);
        SimpleMonitor listener = new SimpleMonitor(query.toString(), monitor, percentages);

        // process query
        Object result = null;

        if (query.getFromClause().getSubSelect() != null) {
            result = doSubQuery(listener.nextMonitor());
        } else if (query.getFromClause().getCall() != null) {
            result = doMethodCall(listener.nextMonitor());
        } else {
            result = doFromItem(listener.nextMonitor());
        }

        if (query.getUnionQueries() != null) {
            result = union(listener, result);
        }

        return result;

    }

    private Object union(SimpleMonitor monitor, Object result) throws SnapshotException, OQLParseException {
        // one of those will hold the result
        UnionResultSet unionResultSet = null;
        IntResult unionIntResult = null;

        if (result instanceof CustomTableResultSet) {
            unionResultSet = new UnionResultSet();
            unionResultSet.addResultSet((CustomTableResultSet) result);
        } else if (result instanceof IntResult) {
            IntResult intResult = (IntResult) result;
            unionIntResult = new IntArrayResult(intResult.size());
            unionIntResult.addAll(intResult);
        }

        for (Query q : query.getUnionQueries()) {
            // check the compatibility of UNION queries
            if (this.query.getSelectClause().getSelectList().isEmpty() || this.query.getSelectClause().isAsObjects()) {
                if (!q.getSelectClause().getSelectList().isEmpty() && !q.getSelectClause().isAsObjects()) {
                    throw new SnapshotException(
                            MessageUtil.format(Messages.OQLQueryImpl_Error_QueryMustReturnObjects,
                                    new Object[]{q}));
                }
            } else {
                if (q.getSelectClause().getSelectList().size() != this.query.getSelectClause().getSelectList().size()) {
                    throw new SnapshotException(
                            MessageUtil.format(Messages.OQLQueryImpl_Error_QueryMustHaveIdenticalSelectItems,
                                    new Object[]{q}));
                }
            }

            OQLQueryImpl unionQuery = new OQLQueryImpl(this.ctx, q);
            Object unionResult = unionQuery.internalExecute(monitor.nextMonitor());

            if (unionResult != null) {
                if (unionResultSet != null) {
                    unionResultSet.addResultSet((CustomTableResultSet) unionResult);
                } else if (unionIntResult != null) {
                    unionIntResult.addAll((IntResult) unionResult);
                }
                // If no combined result has been created then get one now.
                else if (this.query.getSelectClause().getSelectList().isEmpty() || this.query.getSelectClause().isAsObjects()) {
                    unionIntResult = new IntArrayResult(0);
                    unionIntResult.addAll((IntResult) unionResult);
                } else {
                    unionResultSet = new UnionResultSet();
                    unionResultSet.addResultSet((CustomTableResultSet) unionResult);
                }
            }
        }

        return unionResultSet != null ? unionResultSet : unionIntResult;
    }

    private Object doSubQuery(IProgressListener monitor) throws SnapshotException {
        OQLQueryImpl subQuery = new OQLQueryImpl(this.ctx, query.getFromClause().getSubSelect());
        Object result = subQuery.internalExecute(monitor);

        if (!(result instanceof IntResult))
            throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_MustReturnObjectList,
                    new Object[]{query.getFromClause().getSubSelect()}));

        IntResult baseSet = (IntResult) result;

        if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses()) {
            return filterAndSelect(baseSet, monitor);
        } else {
            // result must contain only classes
            // convert and process as usual

            try {
                List<IClass> classes = new ArrayList<IClass>(baseSet.size());
                for (IntIterator iter = baseSet.iterator(); iter.hasNext(); ) {
                    int id = iter.nextInt();
                    IClass subjectClass = (IClass) ctx.getSnapshot().getObject(id);
                    classes.add(subjectClass);
                    if (query.getFromClause().includeSubClasses()) {
                        classes.addAll(subjectClass.getAllSubclasses());
                    }
                }

                return filterClasses(monitor, classes);
            } catch (ClassCastException e) {
                throw new SnapshotException(Messages.OQLQueryImpl_Error_ClassCastExceptionOccured, e);
            }

        }
    }

    /**
     * Does the FROM clause of a select with classes/objects.
     * Input from:
     * <pre>
     * classname
     * classnamepattern
     * object ids
     * object addresses
     * </pre>
     *
     * @param listener
     * @return
     * @throws SnapshotException
     */
    private Object doFromItem(IProgressListener listener) throws SnapshotException {
        Collection<IClass> classes = null;

        if (query.getFromClause().getClassName() != null) {
            // [a] class name given
            classes = ctx.getSnapshot().getClassesByName(query.getFromClause().getClassName(),
                    query.getFromClause().includeSubClasses());
        } else if (query.getFromClause().getClassNamePattern() != null) {
            // [b] class name pattern given
            try {
                Pattern pattern = Pattern.compile(PatternUtil.smartFix(query.getFromClause().getClassNamePattern(),
                        false));
                classes = ctx.getSnapshot().getClassesByName(pattern, query.getFromClause().includeSubClasses());
            } catch (PatternSyntaxException e) {
                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_InvalidClassNamePattern,
                        new Object[]{query.getFromClause().getClassNamePattern()}), e);
            }
        } else if (query.getFromClause().getObjectIds() != null) {
            if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses()) {
                return filterAndSelect(new IntArrayResult(query.getFromClause().getObjectIds().toArray()), listener);
            } else {
                classes = new ArrayList<IClass>();
                for (IteratorInt ee = query.getFromClause().getObjectIds().iterator(); ee.hasNext(); ) {
                    IObject subject = ctx.getSnapshot().getObject(ee.next());
                    if (subject instanceof IClass) {
                        IClass subjectClass = (IClass) subject;
                        classes.add(subjectClass);
                        if (query.getFromClause().includeSubClasses()) {
                            classes.addAll(subjectClass.getAllSubclasses());
                        }
                    } else {
                        throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Errot_IsNotClass,
                                new Object[]{Long.toHexString(subject.getObjectAddress())}));
                    }

                }
            }
        } else if (query.getFromClause().getObjectAddresses() != null) {
            ArrayLong objectAddresses = query.getFromClause().getObjectAddresses();
            IntArrayResult result = new IntArrayResult(objectAddresses.size());

            for (IteratorLong ee = objectAddresses.iterator(); ee.hasNext(); )
                result.add(ctx.getSnapshot().mapAddressToId(ee.next()));

            if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses()) {
                return filterAndSelect(result, listener);
            } else {
                classes = new ArrayList<IClass>();
                for (IntIterator iter = result.iterator(); iter.hasNext(); ) {
                    IObject subject = ctx.getSnapshot().getObject(iter.nextInt());
                    if (subject instanceof IClass) {
                        IClass subjectClass = (IClass) subject;
                        classes.add(subjectClass);
                        if (query.getFromClause().includeSubClasses()) {
                            classes.addAll(subjectClass.getAllSubclasses());
                        }
                    } else {
                        throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Errot_IsNotClass,
                                new Object[]{Long.toHexString(subject.getObjectAddress())}));
                    }

                }
            }
        }

        if (listener.isCanceled())
            throw new IProgressListener.OperationCanceledException();

        if (classes == null || classes.isEmpty())
            return null;

        return filterClasses(listener, classes);
    }

    /**
     * Does a method call in a FROM clause of a select.
     * <pre>
     * OBJECTS
     *   null
     *   Iterable
     *   array
     * NONE/INSTANCEOF
     *   null
     *   Iterable/array
     *      null
     *      Integer
     *      int[]
     *      IClass
     * </pre>
     *
     * @param listener
     * @return
     * @throws SnapshotException
     */
    private Object doMethodCall(IProgressListener listener) throws SnapshotException {
        Expression method = query.getFromClause().getCall();
        this.ctx.setSubject(this.ctx.getSnapshot());
        Object result = method.compute(this.ctx);

        if (query.getFromClause().includeObjects() && !query.getFromClause().includeSubClasses()) {
            if (result == null) {
                return null;
            } else if (result instanceof Iterable) {
                List<Object> r = new ArrayList<Object>();

                for (Object obj : (Iterable<?>) result) {
                    if (accept(obj))
                        r.add(obj);
                }

                return r.isEmpty() ? null : select(r, listener);
            } else if (result.getClass().isArray()) {
                List<Object> r = new ArrayList<Object>();

                int length = Array.getLength(result);
                for (int ii = 0; ii < length; ii++) {
                    Object obj = Array.get(result, ii);
                    if (accept(obj))
                        r.add(obj);
                }

                return r.isEmpty() ? null : select(r, listener);
            } else {
                return accept(result) ? select(result, listener) : null;
            }
        } else {
            // result must contain only classes
            // convert and process as usual

            List<IClass> classes = new ArrayList<IClass>();

            try {
                if (result == null) {
                    return null;
                } else if (result instanceof Iterable) {
                    for (Object obj : (Iterable<?>) result) {
                        if (obj == null) {
                            // allowed value
                        } else if (obj instanceof Integer) {
                            IClass subjectClass = (IClass) this.ctx.getSnapshot().getObject(((Integer) obj).intValue());
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses()) {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        } else if (obj instanceof int[]) {
                            for (int id : (int[]) obj) {
                                IClass subjectClass = (IClass) this.ctx.getSnapshot().getObject(id);
                                classes.add(subjectClass);
                                if (query.getFromClause().includeSubClasses()) {
                                    classes.addAll(subjectClass.getAllSubclasses());
                                }
                            }
                        } else {
                            IClass subjectClass = (IClass) obj;
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses()) {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        }
                    }
                } else if (result.getClass().isArray()) {
                    int length = Array.getLength(result);
                    for (int ii = 0; ii < length; ii++) {
                        Object obj = Array.get(result, ii);
                        if (obj == null) {
                            // allowed value
                        } else if (obj instanceof Integer) {
                            IClass subjectClass = (IClass) this.ctx.getSnapshot().getObject(((Integer) obj).intValue());
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses()) {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        } else if (obj instanceof int[]) {
                            for (int id : (int[]) obj) {
                                IClass subjectClass = (IClass) this.ctx.getSnapshot().getObject(id);
                                classes.add(subjectClass);
                                if (query.getFromClause().includeSubClasses()) {
                                    classes.addAll(subjectClass.getAllSubclasses());
                                }
                            }
                        } else {
                            IClass subjectClass = (IClass) obj;
                            classes.add(subjectClass);
                            if (query.getFromClause().includeSubClasses()) {
                                classes.addAll(subjectClass.getAllSubclasses());
                            }
                        }
                    }
                } else {
                    IClass subjectClass = (IClass) result;
                    classes.add(subjectClass);
                    if (query.getFromClause().includeSubClasses()) {
                        classes.addAll(subjectClass.getAllSubclasses());
                    }
                }
            } catch (ClassCastException e) {
                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ElementIsNotClass,
                        new Object[]{e.getMessage()}), e);
            }

            return filterClasses(listener, classes);
        }
    }

    private Object filterClasses(IProgressListener listener, Collection<IClass> classes) throws SnapshotException {
        if (query.getFromClause().includeObjects()) {
            listener.beginTask(Messages.OQLQueryImpl_SelectingObjects, classes.size());

            IntResult filteredSet = createIntResult(classes.size());
            for (IClass clasz : classes) {
                if (accept(clasz.getObjectId()))
                    filteredSet.add(clasz.getObjectId());

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
                listener.worked(1);
            }
            return filteredSet.isEmpty() ? null : select(filteredSet, listener);

        } else {
            listener.beginTask(Messages.OQLQueryImpl_CollectingObjects, classes.size());

            IntResult filteredSet = createIntResult(classes.size() * 100);
            for (IClass clasz : classes) {
                listener.subTask(MessageUtil.format(Messages.OQLQueryImpl_CheckingClass,
                        new Object[]{clasz.getName()}));

                int[] ids = clasz.getObjectIds();
                for (int id : ids) {
                    if (accept(id))
                        filteredSet.add(id);
                }

                if (listener.isCanceled())
                    throw new IProgressListener.OperationCanceledException();
                listener.worked(1);
            }

            return filteredSet.isEmpty() ? null : select(filteredSet, listener);
        }
    }

    private boolean accept(int objectId) throws SnapshotException {
        if (query.getWhereClause() == null)
            return true;

        return accept(ctx.getSnapshot().getObject(objectId));
    }

    private boolean accept(Object object) throws SnapshotException {
        if (query.getWhereClause() == null)
            return true;

        ctx.setSubject(object);

        Boolean result = (Boolean) query.getWhereClause().compute(ctx);

        return result == null ? false : result.booleanValue();
    }

    private Object filterAndSelect(IntResult objectIds, IProgressListener listener) throws SnapshotException {
        IntResult filteredSet = createIntResult(objectIds.size());

        for (IntIterator iter = objectIds.iterator(); iter.hasNext(); ) {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            int id = iter.nextInt();
            if (accept(id))
                filteredSet.add(id);
        }

        return filteredSet.isEmpty() ? null : select(filteredSet, listener);
    }

    private Object select(IntResult objectIds, IProgressListener listener) throws SnapshotException {
        Query.SelectClause select = query.getSelectClause();

        // calculate retained set
        if (select.isRetainedSet()) {
            objectIds = new IntArrayResult(ctx.getSnapshot().getRetainedSet(objectIds.toArray(), listener));
        }

        if (select.getSelectList().isEmpty()) {
            return objectIds;
        } else if (select.isAsObjects()) {
            ResultSet temp = new ResultSet(getSelectQuery(), objectIds.toArray());
            IntResult r = createIntResult(objectIds.size());
            convertToObjects(temp, r, listener);
            return r;
        } else {
            return new ResultSet(getSelectQuery(), objectIds.toArray());
        }
    }

    private Object select(List<Object> objects, IProgressListener listener) throws SnapshotException {
        Query.SelectClause select = query.getSelectClause();

        // calculate retained set
        if (select.isRetainedSet()) {
            return select(convertToObjectIds(objects), listener);
        }

        if (select.getSelectList().isEmpty()) {
            return objects;
        } else if (select.isAsObjects()) {
            ObjectResultSet temp = new ObjectResultSet(getSelectQuery(), objects);
            IntResult r = createIntResult(temp.getRowCount());
            convertToObjects(temp, r, listener);
            return r;
        } else {
            return new ObjectResultSet(getSelectQuery(), objects);
        }
    }

    private Object select(Object object, IProgressListener listener) throws SnapshotException {
        Query.SelectClause select = query.getSelectClause();

        // calculate retained set
        if (select.isRetainedSet()) {
            return select(convertToObjectIds(Arrays.asList(new Object[]{object})),
                    listener);
        }

        if (select.getSelectList().isEmpty()) {
            return object;
        } else if (select.isAsObjects()) {
            ObjectResultSet temp = new ObjectResultSet(getSelectQuery(), Arrays.asList(new Object[]{object}));
            IntResult r = createIntResult(temp.getRowCount());
            convertToObjects(temp, r, listener);
            return r;
        } else {
            return new ObjectResultSet(getSelectQuery(), Arrays.asList(new Object[]{object}));
        }
    }

    /**
     * Get an query without the union clause for results before applying the union clause.
     *
     * @return A new query without the union clause.
     */
    private OQLQueryImpl getSelectQuery() {
        Query q2 = new Query();
        q2.setSelectClause(query.getSelectClause());
        q2.setFromClause(query.getFromClause());
        q2.setWhereClause(query.getWhereClause());
        OQLQueryImpl qi = new OQLQueryImpl(ctx, q2);
        return qi;
    }

    private IntArrayResult convertToObjectIds(List<?> objects) throws SnapshotException {
        ArrayInt a = new ArrayInt();

        for (Object object : objects) {
            if (object == null) {
                // valid value
            } else if (object instanceof Integer) {
                a.add(((Integer) object).intValue());
            } else if (object instanceof IObject) {
                a.add(((IObject) object).getObjectId());
            } else if (object instanceof int[]) {
                a.addAll((int[]) object);
            } else {
                throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_CannotCalculateRetainedSet,
                        new Object[]{object}));
            }
        }

        return new IntArrayResult(a);
    }

    private void convertToObjects(CustomTableResultSet set, IntResult resultSet, IProgressListener listener)
            throws SnapshotException {
        if (set.getColumns().length != 1) {
            throw new SnapshotException(MessageUtil.format(
                    Messages.OQLQueryImpl_Error_QueryCannotBeConverted, new Object[]{set.getOQLQuery()}));
        }

        int count = set.getRowCount();
        for (int ii = 0; ii < count; ii++) {
            if (listener.isCanceled())
                throw new IProgressListener.OperationCanceledException();

            Object rowObject = set.getColumnValue(set.getRow(ii), 0);
            /**
             * Convert arrays or collections of IObjects
             * or ints or int arrays or IObjects
             * or longs or arrays of longs into object ids.
             */
            Iterable<?> it;
            if (rowObject instanceof Iterable) {
                it = (Iterable<?>) rowObject;
            } else if (rowObject instanceof Object[]) {
                it = Arrays.asList((Object[]) rowObject);
            } else {
                it = Collections.singleton(rowObject);
            }
            for (Object object : it) {
                if (object == null) {
                    // acceptable value -> do nothing
                } else if (object instanceof Integer) {
                    resultSet.add(((Integer) object).intValue());
                } else if (object instanceof int[]) {
                    resultSet.addAll((int[]) object);
                } else if (object instanceof IObject) {
                    resultSet.add(((IObject) object).getObjectId());
                } else if (object instanceof Long) {
                    long addr = ((Long) object).longValue();
                    if (addr != 0) {
                        int id = ctx.getSnapshot().mapAddressToId(addr);
                        resultSet.add(id);
                    }
                } else if (object instanceof long[]) {
                    for (long addr : (long[]) object) {
                        if (addr != 0) {
                            int id = ctx.getSnapshot().mapAddressToId(addr);
                            resultSet.add(id);
                        }
                    }
                } else {
                    throw new SnapshotException(MessageUtil.format(Messages.OQLQueryImpl_Error_ResultMustReturnObjectList,
                            new Object[]{set.getOQLQuery(), String.valueOf(rowObject)}));
                }
            }
        }
    }

    private IntResult createIntResult(int capacity) {
        return query.getSelectClause().isDistinct() || query.getSelectClause().isRetainedSet() ? new IntSetResult(
                capacity) : new IntArrayResult(capacity);
    }

    @Override
    public String toString() {
        return query.toString();
    }

}
