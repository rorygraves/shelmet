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
package org.eclipse.mat.snapshot.extension;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Used to tag resolvers with the name of the class that they handle.
 * Can be used as follows:
 * <code>
 * <pre>
 *
 * @Subject("com.example.class1") </pre>
 * </code>
 * See {@link Subjects} for multiple class names.
 * <p>Experimental: can also be used to tag queries which only make sense when the class
 * is present in the snapshot.
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface Subject {
    String value();
}
