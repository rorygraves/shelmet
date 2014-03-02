/*******************************************************************************
 * Copyright (c) 2008, 2012 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Stefan Eggerstorfer - JDK7u6 doesn't have count/offset fields
 *******************************************************************************/
package org.eclipse.mat.snapshot.model;

import org.eclipse.mat.SnapshotException;

/**
 * Utility class to extract String representations of heap dump objects.
 */
public final class PrettyPrinter {
    /**
     * Convert a <code>java.lang.String</code> object into a String.
     *
     * @param stringObject the String object in the dump
     * @param limit        maximum number of characters to return
     * @return the value of the string from the dump, as a String
     * @throws SnapshotException
     */
    public static String objectAsString(IObject stringObject, int limit) throws SnapshotException {
        Object valueObj = stringObject.resolveValue("value");
        if (!(valueObj instanceof IPrimitiveArray))
            return null;
        IPrimitiveArray charArray = (IPrimitiveArray) valueObj;

        Object countObj = stringObject.resolveValue("count");
        // count and offset fields were removed with JDK7u6
        if (countObj == null) {
            return arrayAsString(charArray, 0, charArray.getLength(), limit);
        } else {
            if (!(countObj instanceof Integer))
                return null;
            Integer count = (Integer) countObj;
            if (count.intValue() == 0)
                return "";

            Object offsetObj = stringObject.resolveValue("offset");
            if (!(offsetObj instanceof Integer))
                return null;
            Integer offset = (Integer) offsetObj;

            return arrayAsString(charArray, offset, count, limit);
        }
    }

    /**
     * Convert a <code>char[]</code> object into a String.
     * Unprintable characters are returned as \\unnnn values
     *
     * @param charArray
     * @param offset    where to start
     * @param count     how many characters to read
     * @param limit     the maximum number of characters to read
     * @return the characters as a string
     */
    public static String arrayAsString(IPrimitiveArray charArray, int offset, int count, int limit) {
        if (charArray.getType() != IObject.Type.CHAR)
            return null;

        int length = charArray.getLength();

        int contentToRead = count <= limit ? count : limit;
        if (contentToRead > length - offset)
            contentToRead = length - offset;

        char[] value;
        if (offset == 0 && length == contentToRead)
            value = (char[]) charArray.getValueArray();
        else
            value = (char[]) charArray.getValueArray(offset, contentToRead);

        if (value == null)
            return null;

        StringBuilder result = new StringBuilder(value.length);
        for (int ii = 0; ii < value.length; ii++) {
            char val = value[ii];
            if (val >= 32 && val < 127)
                result.append(val);
            else
                result.append("\\u").append(String.format("%04x", 0xFFFF & val));
        }
        if (limit < count)
            result.append("...");
        return result.toString();
    }

    private PrettyPrinter() {
    }
}
