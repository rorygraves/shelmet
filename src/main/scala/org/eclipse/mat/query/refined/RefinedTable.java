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
package org.eclipse.mat.query.refined;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IResultTable;

import java.util.ArrayList;
import java.util.List;

/**
 * The result from refining a table.
 */
public class RefinedTable extends RefinedStructuredResult implements IResultTable {
    protected List<?> rows;

    /* package */RefinedTable() {
    }

    public int getRowCount() {
        if (rows == null)
            reread();
        return rows.size();
    }

    public List<?> getRows() {
        if (rows == null)
            reread();
        return rows;
    }

    public Object getRow(int rowId) {
        if (rows == null)
            reread();
        return rows.get(rowId);
    }

    public synchronized void refresh() {
        rows = null;
    }

    // //////////////////////////////////////////////////////////////
    // private parts
    // //////////////////////////////////////////////////////////////

    private void reread() {
        try {
            rows = refine(asList());
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    private List<?> asList() {
        IResultTable table = (IResultTable) subject;

        int rowCount = table.getRowCount();
        List<Object> l = new ArrayList<Object>(rowCount);
        for (int ii = 0; ii < rowCount; ii++)
            l.add(table.getRow(ii));
        return l;
    }

    @Override
    public void filterChanged(Filter filter) {
        this.rows = null;
    }
}
