/*******************************************************************************
 * Copyright (c) 2010,2011 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - documentation
 *******************************************************************************/
package org.eclipse.mat.query.registry;

import org.eclipse.mat.query.annotations.descriptors.IAnnotatedObjectDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Container class for annotated objects and arguments.
 * Holds information about an annotated object (e.g. a query) and all its
 * parameters and the current values of the arguments.
 */
public class AnnotatedObjectArgumentsSet {
    private IAnnotatedObjectDescriptor descriptor;
    private Map<ArgumentDescriptor, Object> values;

    public AnnotatedObjectArgumentsSet(IAnnotatedObjectDescriptor descriptor) {
        this.descriptor = descriptor;
        this.values = new HashMap<ArgumentDescriptor, Object>();
    }

    public void setArgumentValue(ArgumentDescriptor arg, Object value) {
        values.put(arg, value);
    }

    /**
     * Are all the required arguments set explicitly or with a default value?
     *
     * @return true if the query is read to go
     */
    public boolean isExecutable() {
        // all mandatory parameters must be set
        for (ArgumentDescriptor parameter : descriptor.getArguments()) {
            if (parameter.isMandatory() && !values.containsKey(parameter) && parameter.getDefaultValue() == null)
                return false;
        }

        return true;
    }

    public void removeArgumentValue(ArgumentDescriptor arg) {
        values.remove(arg);
    }

    public Object getArgumentValue(ArgumentDescriptor desc) {
        return values.get(desc);
    }

    public IAnnotatedObjectDescriptor getDescriptor() {
        return descriptor;
    }

    public Map<ArgumentDescriptor, Object> getValues() {
        return values;
    }


}
