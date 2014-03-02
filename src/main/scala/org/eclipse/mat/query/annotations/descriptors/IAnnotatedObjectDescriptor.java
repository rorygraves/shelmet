/*******************************************************************************
 * Copyright (c) 2010,2011 SAP AG and IBM Corporation..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - documentation
 *******************************************************************************/
package org.eclipse.mat.query.annotations.descriptors;

import org.eclipse.mat.query.registry.ArgumentDescriptor;

import java.util.List;

/**
 * A descriptor which allows to inspect an annotated object, e.g. a IQuery
 *
 * @since 1.0
 */
public interface IAnnotatedObjectDescriptor {
    /**
     * Get the identifier for the annotated object, for example provided by the annotation {@link org.eclipse.mat.query.annotations.CommandName}
     * or {@link #getName}.
     *
     * @return the identifier
     */
    public String getIdentifier();

    /**
     * Get the name, for example provided by the annotation {@link org.eclipse.mat.query.annotations.Name}.
     *
     * @return the name
     */
    public String getName();

    /**
     * Get descriptors for the fields annotated by the annotation {@link org.eclipse.mat.query.annotations.Argument}.
     * TODO Should this have been IArgumentDescriptor ?
     *
     * @return the list of annotated arguments, see {@link ArgumentDescriptor}
     */
    public List<ArgumentDescriptor> getArguments();
}