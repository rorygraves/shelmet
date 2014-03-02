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

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayInt;
import org.eclipse.mat.collect.HashMapIntObject;
import org.eclipse.mat.collect.SetInt;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.*;
import org.eclipse.mat.query.Column.SortDirection;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

import java.util.Arrays;
import java.util.List;

@CommandName("class_references")
public class ClassReferrersQuery implements IQuery {

    @Argument
    public ISnapshot snapshot;

    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    @Argument(isMandatory = false)
    public boolean inbound = false;

    public IResult execute(IProgressListener listener) throws Exception {
        return inbound ? new InboundClasses(snapshot, objects.getIds(listener))//
                : new OutboundClasses(snapshot, objects.getIds(listener));
    }

    public interface Type {
        int NEW = 0;
        int MIXED = 1;
        int OLD_FAD = 2;
    }

    // //////////////////////////////////////////////////////////////
    // result (also usable outside the query)
    // //////////////////////////////////////////////////////////////

    public static class InboundClasses extends Tree {
        public InboundClasses(ISnapshot snapshot, int[] objectIds) {
            super(snapshot, objectIds);
        }

        protected int[] children(ClassNode node) throws SnapshotException {
            return snapshot.getInboundRefererIds(node.objects.toArray(), new VoidProgressListener());
        }
    }

    public static class OutboundClasses extends Tree {
        public OutboundClasses(ISnapshot snapshot, int[] objectIds) {
            super(snapshot, objectIds);
        }

        protected int[] children(ClassNode node) throws SnapshotException {
            return snapshot.getOutboundReferentIds(node.objects.toArray(), new VoidProgressListener());
        }
    }

    private abstract static class Tree implements IResultTree {
        protected ISnapshot snapshot;
        private List<ClassNode> treeNodes;

        public Tree(ISnapshot snapshot, int[] objectIds) {
            this.snapshot = snapshot;
            this.treeNodes = prepare(objectIds, null);
        }

        public ResultMetaData getResultMetaData() {
            return null;
        }

        public final Column[] getColumns() {
            return new Column[]{new Column(Messages.Column_ClassName),
                    new Column(Messages.Column_Objects, int.class),
                    new Column(Messages.Column_ShallowHeap, long.class).sorting(SortDirection.DESC)};
        }

        public final List<?> getElements() {
            return treeNodes;
        }

        private final List<ClassNode> prepare(int[] ids, ClassNode parent) {
            try {
                HashMapIntObject<ClassNode> class2node = new HashMapIntObject<ClassNode>();

                for (int ii = 0; ii < ids.length; ii++) {
                    int objectId = ids[ii];
                    IClass clazz = snapshot.getClassOf(objectId);
                    ClassNode node = class2node.get(clazz.getObjectId());

                    if (node == null) {
                        node = new ClassNode(clazz.getObjectId(), parent);
                        node.label = clazz.getName();
                        class2node.put(node.classId, node);
                    }
                    node.objects.add(objectId);
                    node.shallowHeap += snapshot.getHeapSize(objectId);
                }

                return Arrays.asList(class2node.getAllValues(new ClassNode[0]));
            } catch (SnapshotException e) {
                throw new RuntimeException(e);
            }
        }

        public final List<ClassNode> getChildren(Object parent) {
            try {
                int[] childrenIds = children((ClassNode) parent);
                List<ClassNode> answer = prepare(childrenIds, (ClassNode) parent);
                for (ClassNode classNode : answer)
                    checkDeadEnd(classNode);
                return answer;
            } catch (SnapshotException e) {
                throw new RuntimeException(e);
            }
        }

        protected abstract int[] children(ClassNode node) throws SnapshotException;

        public final boolean hasChildren(Object element) {
            return true;
        }

        public final Object getColumnValue(Object row, int columnIndex) {
            ClassNode node = (ClassNode) row;

            switch (columnIndex) {
                case 0:
                    return node.label;
                case 1:
                    return node.objects.size();
                case 2:
                    return node.shallowHeap;
            }
            return null;
        }

        public final IContextObject getContext(final Object row) {
            return new IContextObjectSet() {
                public int getObjectId() {
                    return ((ClassNode) row).classId;
                }

                public int[] getObjectIds() {
                    return ((ClassNode) row).objects.toArray();
                }

                public String getOQL() {
                    return null;
                }
            };
        }

        public synchronized void checkDeadEnd(ClassNode node) {
            if (node.parent == null)
                return;

            // check for identical classes in path
            SetInt ids = new SetInt();
            for (int i = 0; i < node.objects.size(); i++)
                ids.add(node.objects.get(i));

            ClassNode parentNode = node;
            while (parentNode.parent != null && !ids.isEmpty()) {
                parentNode = parentNode.parent;
                if (parentNode.classId == node.classId) {
                    for (int i = 0; i < parentNode.objects.size(); i++)
                        ids.remove(parentNode.objects.get(i));
                }
            }

            if (ids.isEmpty())
                node.type = Type.OLD_FAD;
            else if (ids.size() != node.objects.size())
                node.type = Type.MIXED;
            else
                node.type = Type.NEW;
        }

    }

    private static class ClassNode {
        int classId;
        ArrayInt objects = new ArrayInt();
        int type = Type.NEW;

        String label;
        long shallowHeap;
        ClassNode parent;

        private ClassNode(int classId, ClassNode parent) {
            this.classId = classId;
            this.parent = parent;
        }
    }

}
