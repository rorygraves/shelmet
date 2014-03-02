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
package org.eclipse.mat.inspections;

import org.eclipse.mat.inspections.collections.HashEntriesQuery;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.SnapshotQuery;
import org.eclipse.mat.util.IProgressListener;

import java.util.Collection;

@Subject("java.lang.System")
@CommandName("system_properties")
public class SystemPropertiesQuery implements IQuery {
    @Argument
    public ISnapshot snapshot;

    public HashEntriesQuery.Result execute(IProgressListener listener) throws Exception {
        Collection<IClass> classes = snapshot.getClassesByName("java.lang.System", false);
        if (classes == null || classes.isEmpty())
            return null;
        IClass systemClass = classes.iterator().next();

        IObject properties = (IObject) systemClass.resolveValue("props");
        if (properties == null)
            properties = (IObject) systemClass.resolveValue("systemProperties");
        if (properties == null)
            return null;

        return (HashEntriesQuery.Result) SnapshotQuery.lookup("hash_entries", snapshot)
                .setArgument("objects", properties)
                .execute(listener);
    }

}
