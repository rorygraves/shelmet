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
package org.eclipse.mat.parser.index;

import org.eclipse.mat.parser.internal.snapshot.RetainedSizeCache;
import org.eclipse.mat.util.MessageUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Handles the indexes into the snapshot.
 */
public class IndexManager {
    /**
     * The different index types.
     */
    public enum Index {
        /**
         * Inbounds: object id to N outbound object ids
         */
        INBOUND("inbound", IndexReader.InboundReader.class),
        /**
         * Outbounds: object id to N inbound object ids
         */
        OUTBOUND("outbound", IndexReader.IntIndex1NSortedReader.class),
        /**
         * Object to class: object id to 1 class id
         */
        O2CLASS("o2c", IndexReader.IntIndexReader.class),
        /**
         * Index to address: object id to address (as a long)
         */
        IDENTIFIER("idx", IndexReader.LongIndexReader.class),
        /**
         * Array to size: array (or non-default sized object) id to size (as an encoded int)
         */
        A2SIZE("a2s", IndexReader.SizeIndexReader.class),
        /**
         * Dominated: object id to N dominated object ids
         */
        DOMINATED("domOut", IndexReader.IntIndex1NReader.class),
        /**
         * Object to retained size: object in dominator tree to retained size (as a long)
         */
        O2RETAINED("o2ret", IndexReader.LongIndexReader.class),
        /**
         * Dominator of: object id to the id of its dominator
         */
        DOMINATOR("domIn", IndexReader.IntIndexReader.class),
        /**
         * Retained size cache.
         * Retained size cache for a class: class+all instances.
         * Retained size cache for a class loader: loader+all classes+all instances.
         *
         * @since 1.2
         */
        I2RETAINED("i2sv2", RetainedSizeCache.class);
        /*
         * Other indexes:
         * i2s
         *  Old version of class retained size cache
         * threads
         *  text file holding details of thread stacks and local variables
         * index - master index
         */

        public String filename;
        Class<? extends IIndexReader> impl;

        private Index(String filename, Class<? extends IIndexReader> impl) {
            this.filename = filename;
            this.impl = impl;
        }

        public File getFile(String prefix) {
            return new File(new StringBuilder(prefix).append(filename).append(".index").toString());
        }

    }

    public IIndexReader.IOne2ManyObjectsIndex inbound;
    public IIndexReader.IOne2ManyIndex outbound;
    public IIndexReader.IOne2OneIndex o2c;
    public IIndexReader.IOne2LongIndex idx;
    public IIndexReader.IOne2SizeIndex a2s;
    public IIndexReader.IOne2ManyIndex domOut;
    public IIndexReader.IOne2LongIndex o2ret;
    public IIndexReader.IOne2OneIndex domIn;
    /**
     * @noreference This field is not intended to be referenced by clients.
     */
    public RetainedSizeCache i2sv2;

    public void setReader(final Index index, final IIndexReader reader) {
        try {
            this.getClass().getField(index.filename).set(this, reader);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public IIndexReader getReader(final Index index) {
        try {
            return (IIndexReader) this.getClass().getField(index.filename).get(this);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void init(final String prefix) throws IOException {
        new Visitor() {

            @Override
            void visit(Index index, IIndexReader reader) throws IOException {
                if (reader != null)
                    return;

                try {
                    File indexFile = index.getFile(prefix);
                    if (indexFile.exists()) {
                        Constructor<?> constructor = index.impl.getConstructor(new Class[]{File.class});
                        reader = (IIndexReader) constructor.newInstance(new Object[]{indexFile});
                        setReader(index, reader);
                    }
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    IOException ioe = new IOException(MessageUtil.format("{0}: {1}", cause.getClass().getName(),
                            cause.getMessage()));
                    ioe.initCause(cause);
                    throw ioe;
                } catch (RuntimeException e) {
                    // re-wrap runtime exceptions caught during index processing
                    // into IOExceptions -> trigger reparsing of hprof dump
                    IOException ioe = new IOException();
                    ioe.initCause(e);
                    throw ioe;
                }
            }

        }.doIt();
    }

    public IIndexReader.IOne2ManyIndex inbound() {
        return inbound;
    }

    public IIndexReader.IOne2ManyIndex outbound() {
        return outbound;
    }

    public IIndexReader.IOne2OneIndex o2class() {
        return o2c;
    }

    public IIndexReader.IOne2ManyObjectsIndex c2objects() {
        return inbound;
    }

    public IIndexReader.IOne2LongIndex o2address() {
        return idx;
    }

    /**
     * @since 1.0
     */
    public IIndexReader.IOne2SizeIndex a2size() {
        return a2s;
    }

    public IIndexReader.IOne2ManyIndex dominated() {
        return domOut;
    }

    public IIndexReader.IOne2LongIndex o2retained() {
        return o2ret;
    }

    public IIndexReader.IOne2OneIndex dominator() {
        return domIn;
    }

    public void close() throws IOException {
        new Visitor() {

            @Override
            void visit(Index index, IIndexReader reader) throws IOException {
                if (reader == null)
                    return;

                reader.close();
                setReader(index, null);
            }

        }.doIt();
    }

    public void delete() throws IOException {
        new Visitor() {

            @Override
            void visit(Index index, IIndexReader reader) throws IOException {
                if (reader == null)
                    return;

                reader.close();
                reader.delete();
                setReader(index, null);
            }

        }.doIt();
    }

    private abstract class Visitor {
        abstract void visit(Index index, IIndexReader reader) throws IOException;

        void doIt() throws IOException {
            try {
                for (Index index : Index.values()) {
                    IIndexReader reader = getReader(index);
                    visit(index, reader);
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
