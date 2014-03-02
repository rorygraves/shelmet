/*******************************************************************************
 * Copyright (c) 2008, 2009 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.internal.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.ArgumentDescriptor;
import org.eclipse.mat.query.registry.ArgumentFactory;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.VoidProgressListener;

import java.io.File;

public class SnapshotArgument implements ArgumentFactory {
    String filename;

    public SnapshotArgument(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void appendUsage(StringBuilder buf) {
        buf.append(filename);
    }

    public Object build(ArgumentDescriptor descriptor) throws SnapshotException {
        if (!ISnapshot.class.isAssignableFrom(descriptor.getType()))
            throw new SnapshotException();

        return SnapshotFactory.openSnapshot(new File(filename), new VoidProgressListener());
    }

}
