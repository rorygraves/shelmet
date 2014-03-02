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
package org.eclipse.mat.parser.internal.oql.compiler;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class PathExpression extends Expression {
    private List<Object> attributes;

    public PathExpression(List<Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public Object compute(EvaluationContext ctx) throws SnapshotException {
        try {
            Object current = null;
            int index = 0;

            // check for alias
            Object firstItem = attributes.get(0);
            if (firstItem instanceof Attribute) {
                Attribute firstAttribute = (Attribute) firstItem;
                current = !firstAttribute.isNative() ? ctx.getAlias(firstAttribute.getName()) : null;
            }

            if (current == null)
                current = ctx.getSubject();
            else
                index++;

            for (; index < this.attributes.size(); index++) {
                Object element = this.attributes.get(index);

                if (element != null && element.getClass().isArray())
                    element = asList(element);

                if (element instanceof Attribute) {
                    Attribute attribute = (Attribute) element;
                    if (attribute.isNative() || !(current instanceof IObject)) {
                        // special: we support the 'length' property for arrays
                        if (current.getClass().isArray()) {
                            if ("length".equals(attribute.getName())) {
                                current = Array.getLength(current);
                            } else {
                                throw new SnapshotException(MessageUtil.format(
                                        Messages.PathExpression_Error_ArrayHasNoProperty, new Object[]{
                                        current.getClass().getComponentType().getName(),
                                        attribute.name}));
                            }
                        } else {
                            boolean didFindProperty = false;

                            BeanInfo info = Introspector.getBeanInfo(current.getClass());
                            PropertyDescriptor[] descriptors = info.getPropertyDescriptors();

                            for (PropertyDescriptor descriptor : descriptors) {
                                if (attribute.getName().equals(descriptor.getName())) {
                                    current = descriptor.getReadMethod().invoke(current, (Object[]) null);
                                    didFindProperty = true;
                                    break;
                                }
                            }

                            if (!didFindProperty) {
                                throw new SnapshotException(MessageUtil.format(
                                        Messages.PathExpression_Error_TypeHasNoProperty, new Object[]{
                                        current.getClass().getName(), attribute.name}));
                            }
                        }

                    } else {
                        IObject c = (IObject) current;
                        current = c.resolveValue(attribute.getName());
                    }

                } else if (element instanceof Expression) {
                    EvaluationContext methodCtx = new EvaluationContext(ctx);
                    methodCtx.setSubject(current);
                    current = ((Expression) element).compute(methodCtx);
                } else {
                    throw new SnapshotException(MessageUtil.format(Messages.PathExpression_Error_UnknownElementInPath,
                            new Object[]{element}));
                }

                if (current == null)
                    break;
            }

            return current;
        } catch (Exception e) {
            throw SnapshotException.rethrow(e);
        }
    }

    protected static List<?> asList(Object element) {
        int size = Array.getLength(element);

        List<Object> answer = new ArrayList<Object>(size);
        for (int ii = 0; ii < size; ii++)
            answer.add(Array.get(element, ii));

        return answer;
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx) {
        Object firstItem = attributes.get(0);
        if (firstItem instanceof Attribute) {
            Attribute firstAttribute = (Attribute) firstItem;
            if (!firstAttribute.isNative() && ctx.isAlias(firstAttribute.getName()))
                return true;
        } else if (firstItem instanceof Expression) {
            if (((Expression) firstItem).isContextDependent(ctx))
                return true;
        }
        for (int i = 1; i < attributes.size(); ++i) {
            Object nextItem = attributes.get(i);
            if (nextItem instanceof Expression)
                if (((Expression) nextItem).isContextDependent(ctx))
                    return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(256);

        boolean first = true;
        for (Iterator<Object> iter = this.attributes.iterator(); iter.hasNext(); ) {
            Object element = iter.next();
            if (!first && !(element instanceof ArrayIndexExpression))
                buf.append(".");
            first = false;
            buf.append(element);
        }

        return buf.toString();
    }

}
