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
package org.eclipse.mat.report;

import org.eclipse.mat.report.internal.Messages;
import org.eclipse.mat.util.MessageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A container for multiple results.
 * The results are merged if possible.
 */
public class SectionSpec extends Spec {
    private ITestResult.Status status;
    private List<Spec> children = new ArrayList<Spec>();

    @Deprecated
    public SectionSpec() {
    }

    public SectionSpec(String name) {
        super(name);
    }

    public ITestResult.Status getStatus() {
        return status;
    }

    public void setStatus(ITestResult.Status status) {
        this.status = status;
    }

    public List<Spec> getChildren() {
        return children;
    }

    public void add(Spec child) {
        this.children.add(child);
    }

    @Override
    public synchronized void merge(Spec otherSpec) {
        if (!(otherSpec instanceof SectionSpec))
            throw new RuntimeException(MessageUtil.format(Messages.QuerySpec_Error_IncompatibleTypes, otherSpec
                    .getName(), getName()));

        super.merge(otherSpec);

        SectionSpec other = (SectionSpec) otherSpec;

        this.status = ITestResult.Status.max(this.status, other.status);

        if (children.isEmpty()) {
            children.addAll(other.children);
        } else if (!other.children.isEmpty()) {
            Map<String, Spec> name2spec = new HashMap<String, Spec>();
            for (Spec child : children)
                name2spec.put(child.getName(), child);

            List<Spec> merged = new ArrayList<Spec>();
            for (Spec spec : other.children) {
                Spec mine = name2spec.get(spec.getName());
                if (mine != null) {
                    mine.merge(spec);
                    merged.add(mine);
                    children.remove(mine);
                } else {
                    merged.add(spec);
                }
            }

            merged.addAll(children);
            children = merged;
        }
    }
}
