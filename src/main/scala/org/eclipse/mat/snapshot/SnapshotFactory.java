/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - enhancements and fixes
 *******************************************************************************/
package org.eclipse.mat.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.SnapshotFactoryImpl;
import org.eclipse.mat.util.IProgressListener;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ISnapshot} factory
 */
public final class SnapshotFactory {
    /**
     * Describes the snapshot factory implementation.
     * Implemented in the parser plugin.
     * Implementations of this interface need to be
     * registered using the <code>org.eclipse.mat.api.factory</code> extension point.
     *
     * @noimplement
     */
    public interface Implementation {
        /**
         * Opens a snapshot
         *
         * @param file      the dump file
         * @param arguments extra arguments to change the indexing of the dump
         * @param listener  to show progress and errors
         * @return the snapshot
         * @throws SnapshotException
         */
        ISnapshot openSnapshot(File file, Map<String, String> arguments, IProgressListener listener)
                throws SnapshotException;

        /**
         * Free resources when the snapshot is no longer needed.
         *
         * @param snapshot
         */
        void dispose(ISnapshot snapshot);

        /**
         * Run an OQL query
         *
         * @param queryString the OQL query
         * @return the result
         * @throws OQLParseException
         * @throws SnapshotException
         */
        IOQLQuery createQuery(String queryString) throws OQLParseException, SnapshotException;

    }

    private static Implementation factory = new SnapshotFactoryImpl();

    /**
     * Create a snapshot Object from a file representation of a snapshot.
     *
     * @param file     file from which the snapshot will be constructed (type will be
     *                 derived from the file name extension)
     * @param listener progress listener informing about the current state of
     *                 execution
     * @return snapshot
     * @throws SnapshotException
     */
    public static ISnapshot openSnapshot(File file, IProgressListener listener) throws SnapshotException {
        return openSnapshot(file, new HashMap<String, String>(0), listener);
    }

    /**
     * Create a snapshot Object from a file representation of a snapshot.
     *
     * @param file      file from which the snapshot will be constructed (type will be
     *                  derived from the file name extension)
     * @param arguments parsing arguments
     * @param listener  progress listener informing about the current state of
     *                  execution
     * @return snapshot
     * @throws SnapshotException
     */
    public static ISnapshot openSnapshot(File file, Map<String, String> arguments, IProgressListener listener)
            throws SnapshotException {
        return factory.openSnapshot(file, arguments, listener);
    }

    /**
     * Dispose the whole snapshot.
     * <p/>
     * Please call this method prior to dropping the last reference to the
     * snapshot as this method ensures the proper return of all resources (e.g.
     * main memory, file and socket handles...) when the last user has disposed
     * it through the snapshot factory. After calling this method the snapshot
     * can't be used anymore.
     *
     * @param snapshot snapshot which should be disposed
     */
    public static void dispose(ISnapshot snapshot) {
        factory.dispose(snapshot);
    }

    /**
     * Factory to create an OQL Query.
     *
     * @throws OQLParseException if the OQL contains parsing errors
     */
    public static IOQLQuery createQuery(String queryString) throws OQLParseException, SnapshotException {
        return factory.createQuery(queryString);
    }

    private SnapshotFactory() {
    }
}
