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
package org.eclipse.mat.parser.internal.oql.compiler;

public class Attribute {
    String name;
    boolean isNative;
    boolean isEnvVar;

    public Attribute(String name, boolean isNative, boolean isEnvVar) {
        this.name = name;
        this.isNative = isNative;
        this.isEnvVar = isEnvVar;
    }

    public String getName() {
        return name;
    }

    public boolean isNative() {
        return isNative;
    }

    public boolean isEnvVar() {
        return isEnvVar;
    }

    @Override
    public String toString() {
        return isNative ? "@" + name : isEnvVar ? "${" + name + "}" : name;
    }

}
