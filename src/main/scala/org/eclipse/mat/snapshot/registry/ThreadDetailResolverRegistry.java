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
package org.eclipse.mat.snapshot.registry;

import org.eclipse.mat.snapshot.extension.IThreadDetailsResolver;
import org.eclipse.mat.util.RegistryReader;

public final class ThreadDetailResolverRegistry extends RegistryReader<IThreadDetailsResolver> {
    private static final ThreadDetailResolverRegistry INSTANCE = new ThreadDetailResolverRegistry();

    public static ThreadDetailResolverRegistry instance() {
        return INSTANCE;
    }

    private ThreadDetailResolverRegistry() {
    }
}
