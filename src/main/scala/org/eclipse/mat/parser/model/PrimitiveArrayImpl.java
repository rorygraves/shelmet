/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.parser.model;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.ArrayLong;
import org.eclipse.mat.snapshot.model.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a primitive array of type
 * byte[], short[], int[], long[],
 * boolean, char[], float[], double[].
 */
public class PrimitiveArrayImpl extends AbstractArrayImpl implements IPrimitiveArray {
    private static final long serialVersionUID = 2L;

    private int type;

    /**
     * Constructs a primitive array
     *
     * @param objectId      the id of the array
     * @param address       the address of the array
     * @param classInstance the type (class) of the array
     * @param length        the length in elements
     * @param type          the actual type {@link org.eclipse.mat.snapshot.model.IObject.Type}
     */
    public PrimitiveArrayImpl(int objectId, long address, ClassImpl classInstance, int length, int type) {
        super(objectId, address, classInstance, length);
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public Class<?> getComponentType() {
        return COMPONENT_TYPE[type];
    }

    public Object getValueAt(int index) {
        Object data = getValueArray(index, 1);
        return data != null ? Array.get(data, 0) : null;
    }

    public Object getValueArray() {
        try {
            return source.getHeapObjectReader().readPrimitiveArrayContent(this, 0, getLength());
        } catch (SnapshotException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String valueString(boolean bigLimit) {
        if (getType() == IObject.Type.CHAR)
            return "\"" + new String((char[]) getValueArray()) + "\"";
        else {
            StringBuilder result = new StringBuilder("{");
            Object data = getValueArray();
            int length = Array.getLength(data);
            int limit;
            if(bigLimit)
                limit = Math.min(length,1000);
            else
                limit = Math.min(length,8);

            for(int i=0;i<limit ; i++) {
                if(i > 0)
                    result.append(", ");
                Object value = Array.get(data,i);
                if(value instanceof Byte)
                    result.append(String.format("0x%02X", (Byte) value));
                else
                    result.append(value.toString());

            }
            if(length > limit)
                result.append("... ");
            result.append("}");
            return result.toString();
        }
    }


    public Object getValueArray(int offset, int length) {
        try {
            return source.getHeapObjectReader().readPrimitiveArrayContent(this, offset, length);
        } catch (SnapshotException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Field internalGetField(String name) {
        return null;
    }

    @Override
    public ArrayLong getReferences() {
        ArrayLong references = new ArrayLong(1);
        references.add(classInstance.getObjectAddress());
        return references;
    }

    public List<NamedReference> getOutboundReferences() {
        List<NamedReference> references = new ArrayList<>(1);
        references.add(new PseudoReference(source, classInstance.getObjectAddress(), "<class>"));
        return references;
    }

    @Override
    protected StringBuffer appendFields(StringBuffer buf) {
        return super.appendFields(buf).append(";size=").append(getUsedHeapSize());
    }

    @Override
    public long getUsedHeapSize() {
        try {
            return getSnapshot().getHeapSize(getObjectId());
        } catch (SnapshotException e) {
            return doGetUsedHeapSize(classInstance, length, type);
        }
    }

    /**
     * Calculates the size of a primitive array
     *
     * @param clazz  the type
     * @param length the length in elements
     * @param type   the actual type {@link org.eclipse.mat.snapshot.model.IObject.Type}
     * @return the size in bytes
     * @since 1.0
     */
    public static long doGetUsedHeapSize(ClassImpl clazz, int length, int type) {
        return alignUpTo8(2 * clazz.getHeapSizePerInstance() + 4 + length * (long) ELEMENT_SIZE[type]);
    }

}
