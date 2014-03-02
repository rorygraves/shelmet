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
package org.eclipse.mat.parser.internal.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.SnapshotImpl;

/**
 * @deprecated Use {@link HistogramBuilder#toHistogram(SnapshotImpl, boolean)} instead
 */
@Deprecated
public interface IHistogramBuilderResolver {
    public HistogramBuilderResolverData resolve(int classId) throws SnapshotException;
}
