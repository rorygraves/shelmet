/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - additional resolvers and fixes
 *******************************************************************************/
package org.eclipse.mat.inspections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.*;

import java.lang.reflect.Modifier;

public class CommonNameResolver {
    @Subject("java.lang.String")
    public static class StringResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            return PrettyPrinter.objectAsString(obj, 1024);
        }
    }

    @Subjects({"java.lang.StringBuffer",
            "java.lang.StringBuilder"})
    public static class StringBufferResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            Integer count = (Integer) obj.resolveValue("count");
            if (count == null)
                return null;
            if (count == 0)
                return "";

            IPrimitiveArray charArray = (IPrimitiveArray) obj.resolveValue("value");
            if (charArray == null)
                return null;

            return PrettyPrinter.arrayAsString(charArray, 0, count, 1024);
        }
    }

    @Subject("java.lang.Thread")
    public static class ThreadResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            IObject name = (IObject) obj.resolveValue("name");
            return name != null ? name.getClassSpecificName() : null;
        }
    }

    @Subject("java.lang.ThreadGroup")
    public static class ThreadGroupResolver implements IClassSpecificNameResolver {
        public String resolve(IObject object) throws SnapshotException {
            IObject nameString = (IObject) object.resolveValue("name");
            if (nameString == null)
                return null;
            return nameString.getClassSpecificName();
        }
    }

    @Subjects({"java.lang.Byte",
            "java.lang.Character",
            "java.lang.Short",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Float",
            "java.lang.Double",
            "java.lang.Boolean"})
    public static class ValueResolver implements IClassSpecificNameResolver {
        public String resolve(IObject heapObject) throws SnapshotException {
            Object value = heapObject.resolveValue("value");
            return value != null ? String.valueOf(value) : null;
        }
    }

    @Subject("char[]")
    public static class CharArrayResolver implements IClassSpecificNameResolver {
        public String resolve(IObject heapObject) throws SnapshotException {
            IPrimitiveArray charArray = (IPrimitiveArray) heapObject;
            return PrettyPrinter.arrayAsString(charArray, 0, charArray.getLength(), 1024);
        }
    }

    @Subject("byte[]")
    public static class ByteArrayResolver implements IClassSpecificNameResolver {
        public String resolve(IObject heapObject) throws SnapshotException {
            IPrimitiveArray arr = (IPrimitiveArray) heapObject;
            byte[] value = (byte[]) arr.getValueArray(0, Math.min(arr.getLength(), 1024));
            if (value == null)
                return null;

            // must not modify the original byte array
            StringBuilder r = new StringBuilder(value.length);
            for (int i = 0; i < value.length; i++) {
                // ASCII/Unicode 127 is not printable
                if (value[i] < 32 || value[i] > 126)
                    r.append('.');
                else
                    r.append((char) value[i]);
            }
            return r.toString();
        }
    }

    /*
     * Contributed in bug 273915
     */
    @Subject("java.net.URL")
    public static class URLResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            StringBuilder builder = new StringBuilder();
            IObject protocol = (IObject) obj.resolveValue("protocol");
            if (protocol != null) {
                builder.append(protocol.getClassSpecificName());
                builder.append(":");
            }
            IObject authority = (IObject) obj.resolveValue("authority");
            if (authority != null) {
                builder.append("//");
                builder.append(authority.getClassSpecificName());
            }
            IObject path = (IObject) obj.resolveValue("path");
            if (path != null)
                builder.append(path.getClassSpecificName());
            IObject query = (IObject) obj.resolveValue("query");
            if (query != null) {
                builder.append("?");
                builder.append(query.getClassSpecificName());
            }
            IObject ref = (IObject) obj.resolveValue("ref");
            if (ref != null) {
                builder.append("#");
                builder.append(ref.getClassSpecificName());
            }
            return builder.length() > 0 ? builder.toString() : null;
        }
    }

    @Subject("java.lang.reflect.AccessibleObject")
    public static class AccessibleObjectResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            // Important fields
            // modifiers - not actually present, but present in all superclasses
            // clazz - not actually present, but present in all superclasses
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers");
            if (val instanceof Integer) {
                r.append(Modifier.toString((Integer) val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz");
            if (ref != null) {
                addClassName(snapshot, ref.getObjectAddress(), r);
            } else {
                return null;
            }
            return r.toString();
        }

        protected void addClassName(ISnapshot snapshot, long addr, StringBuilder r) throws SnapshotException {
            int id = snapshot.mapAddressToId(addr);
            IObject ox = snapshot.getObject(id);
            if (ox instanceof IClass) {
                IClass cls = (IClass) ox;
                r.append(cls.getName());
            }
        }
    }

    @Subject("java.lang.reflect.Field")
    public static class FieldResolver extends AccessibleObjectResolver {
        public String resolve(IObject obj) throws SnapshotException {
            // Important fields
            // modifiers
            // clazz
            // name
            // type
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers");
            if (val instanceof Integer) {
                r.append(Modifier.toString((Integer) val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("type");
            if (ref != null) {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz");
            if (ref != null) {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append('.');
            }
            ref = (IObject) obj.resolveValue("name");
            if (ref != null) {
                r.append(ref.getClassSpecificName());
            } else {
                // No method name so give up
                return null;
            }
            return r.toString();
        }
    }

    @Subject("java.lang.reflect.Method")
    public static class MethodResolver extends AccessibleObjectResolver {
        public String resolve(IObject obj) throws SnapshotException {
            // Important fields
            // modifiers
            // clazz
            // name
            // parameterTypes[]
            // exceptionTypes[]
            // returnType
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers");
            if (val instanceof Integer) {
                r.append(Modifier.toString((Integer) val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("returnType");
            if (ref != null) {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz");
            if (ref != null) {
                addClassName(snapshot, ref.getObjectAddress(), r);
                r.append('.');
            }
            ref = (IObject) obj.resolveValue("name");
            if (ref != null) {
                r.append(ref.getClassSpecificName());
            } else {
                // No method name so give up
                return null;
            }
            r.append('(');
            ref = (IObject) obj.resolveValue("parameterTypes");
            if (ref instanceof IObjectArray) {
                IObjectArray orefa = (IObjectArray) ref;
                long refs[] = orefa.getReferenceArray();
                for (int i = 0; i < orefa.getLength(); ++i) {
                    if (i > 0)
                        r.append(',');
                    long addr = refs[i];
                    addClassName(snapshot, addr, r);
                }
            }
            r.append(')');
            return r.toString();
        }
    }

    @Subject("java.lang.reflect.Constructor")
    public static class ConstructorResolver extends AccessibleObjectResolver {
        public String resolve(IObject obj) throws SnapshotException {
            // Important fields
            // modifiers
            // clazz
            // parameterTypes[]
            // exceptionTypes[]
            StringBuilder r = new StringBuilder();
            ISnapshot snapshot = obj.getSnapshot();
            IObject ref;
            Object val = obj.resolveValue("modifiers");
            if (val instanceof Integer) {
                r.append(Modifier.toString((Integer) val));
                if (r.length() > 0) r.append(' ');
            }
            ref = (IObject) obj.resolveValue("clazz");
            if (ref != null) {
                addClassName(snapshot, ref.getObjectAddress(), r);
            } else {
                // No class name so give up
                return null;
            }
            r.append('(');
            ref = (IObject) obj.resolveValue("parameterTypes");
            if (ref instanceof IObjectArray) {
                IObjectArray orefa = (IObjectArray) ref;
                long refs[] = orefa.getReferenceArray();
                for (int i = 0; i < orefa.getLength(); ++i) {
                    if (i > 0)
                        r.append(',');
                    long addr = refs[i];
                    addClassName(snapshot, addr, r);
                }
            }
            r.append(')');
            return r.toString();
        }
    }
}
