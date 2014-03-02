/*******************************************************************************
 * Copyright (c) 2012, 2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import org.eclipse.mat.SnapshotException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Multiple snapshots found in a dump when no particular dump has been requested.
 * Experimental - the form and name of this class is subject to change
 * Not an API.
 *
 * @since 1.3
 */
public class MultipleSnapshotsException extends SnapshotException {
    private static final long serialVersionUID = 1L;

    /**
     * Experimental - the form and name of this class is subject to change
     */
    public static class Context implements Serializable {
        private static final long serialVersionUID = 1L;
        private String runtimeId;
        private String description;
        private String version;
        private List<String> options = new ArrayList<String>();

        public Context(String runtimeId) {
            setRuntimeId(runtimeId);
        }

        public String getRuntimeId() {
            return runtimeId;
        }

        public void setRuntimeId(String runtimeId) {
            this.runtimeId = runtimeId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public void addOption(String optionString) {
            options.add(optionString);
        }

        public List<String> getOptions() {
            return options;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    List<Context> contexts = new ArrayList<Context>();

    public List<Context> getRuntimes() {
        return contexts;
    }

    public void addContext(Context runtime) {
        contexts.add(runtime);
    }

    public MultipleSnapshotsException() {
        super();
    }

    public MultipleSnapshotsException(String msg) {
        super(msg);
    }
}
