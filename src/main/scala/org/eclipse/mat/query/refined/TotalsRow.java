/*******************************************************************************
 * Copyright (c) 2008, 2013 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - localization of icons
 *******************************************************************************/
package org.eclipse.mat.query.refined;

import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public class TotalsRow {
    private static final NumberFormat fmt = DecimalFormat.getInstance();

    private Double[] totals;

    private int filteredItems;
    private int numberOfItems;
    private int visibleItems;

    public int getVisibleItems() {
        return visibleItems;
    }

    public void setVisibleItems(int visibleItems) {
        this.visibleItems = visibleItems;
    }

    public int getFilteredItems() {
        return filteredItems;
    }

    /* package */void setFilteredItems(int filteredItems) {
        this.filteredItems = filteredItems;
    }

    public int getNumberOfItems() {
        return numberOfItems;
    }

    /* package */void setNumberOfItems(int numberOfItems) {
        this.numberOfItems = numberOfItems;
    }

    /* package */void setTotals(Double[] totals) {
        this.totals = totals;
    }

    /**
     * returns true if the totals row should be shown
     */
    public boolean isVisible() {
        return (numberOfItems > 1)
                || filteredItems > 0
                || visibleItems < numberOfItems;
    }

    public String getLabel(int columnIndex) {
        if (columnIndex == 0) {
            return getFirstItemText();
        } else {
            // not calculated?
            if (totals == null)
                return "";

            // maybe for a row added later?
            if (columnIndex < 1 || columnIndex >= totals.length)
                return "";

            // no value present
            if (totals[columnIndex] == null)
                return "";

            return fmt.format(totals[columnIndex].doubleValue());
        }
    }

    private String getFirstItemText() {
        boolean hasMore = numberOfItems > visibleItems;
        boolean hasTotals = totals != null && totals[0] != null;
        boolean hasFiltered = filteredItems > 0;

        String msg = null;

        if (hasMore)
            msg = MessageUtil.format(Messages.TotalsRow_Label_TotalVisible, visibleItems, numberOfItems, (numberOfItems - visibleItems));
        else
            msg = MessageUtil.format(Messages.TotalsRow_Label_Total, numberOfItems);

        if (hasTotals)
            msg += " / " + fmt.format(totals[0].doubleValue());

        if (hasFiltered)
            msg += MessageUtil.format(" " + Messages.TotalsRow_Label_Filtered, filteredItems);

        return msg;
    }
}
