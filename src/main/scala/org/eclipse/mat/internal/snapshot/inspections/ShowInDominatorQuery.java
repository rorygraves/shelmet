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
package org.eclipse.mat.internal.snapshot.inspections;

import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.query.IHeapObjectArgument;
import org.eclipse.mat.util.IProgressListener;

@CommandName("show_dominator_tree")
public class ShowInDominatorQuery extends DominatorQuery {
    @Argument(flag = Argument.UNFLAGGED)
    public IHeapObjectArgument objects;

    public DominatorQuery.Tree execute(IProgressListener listener) throws Exception {
        return create(snapshot.getTopAncestorsInDominatorTree(objects.getIds(listener), listener), listener);
    }

}