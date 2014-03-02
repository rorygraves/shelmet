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

import java.util.ArrayList;
import java.util.List;

/**
 * A description of the meta information attached to a class such as a query or heap dump provider.
 */
public class AnnotatedObjectDescriptor implements IAnnotatedObjectDescriptor {
    protected final String identifier;
    protected String name;

    protected final List<ArgumentDescriptor> arguments;

    public AnnotatedObjectDescriptor(String identifier, String name) {
        super();
        this.identifier = identifier;
        this.name = name;
        this.arguments = new ArrayList<ArgumentDescriptor>();
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public List<ArgumentDescriptor> getArguments() {
        return arguments;
    }

    public void addParameter(ArgumentDescriptor descriptor) {
        arguments.add(descriptor);
    }

}
