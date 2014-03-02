/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Chris Grindstaff
 *******************************************************************************/
package org.eclipse.mat.inspections.eclipse;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.snapshot.extension.IClassSpecificNameResolver;
import org.eclipse.mat.snapshot.extension.Subject;
import org.eclipse.mat.snapshot.extension.Subjects;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.MessageUtil;

public class EclipseNameResolver {
    @Subject("org.eclipse.core.runtime.adaptor.EclipseClassLoader")
    public static class EclipseClassLoaderResolver implements IClassSpecificNameResolver {

        public String resolve(IObject obj) throws SnapshotException {
            IObject s = (IObject) obj.resolveValue("hostdata.symbolicName");
            return s != null ? s.getClassSpecificName() : null;
        }

    }

    @Subject("org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader")
    public static class EclipseDefaultClassLoaderResolver implements IClassSpecificNameResolver {

        public String resolve(IObject obj) throws SnapshotException {
            IObject s = (IObject) obj.resolveValue("manager.data.symbolicName");
            return s != null ? s.getClassSpecificName() : null;
        }

    }

    @Subject("org.eclipse.equinox.launcher.Main$StartupClassLoader")
    public static class StartupClassLoaderResolver implements IClassSpecificNameResolver {

        public String resolve(IObject obj) throws SnapshotException {
            return Messages.EclipseNameResolver_EquinoxStartupClassLoader;
        }

    }

    @Subject("org.eclipse.swt.graphics.RGB")
    public static class RGBResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            Integer red = (Integer) obj.resolveValue("red");
            Integer green = (Integer) obj.resolveValue("green");
            Integer blue = (Integer) obj.resolveValue("blue");

            if (red == null || green == null || blue == null)
                return null;

            return MessageUtil.format(Messages.EclipseNameResolver_RGB, red, green, blue);
        }
    }

    /*
     * Contributed in bug 273915
     */
    @Subjects({"org.eclipse.swt.graphics.Point",
            "java.awt.Point"})
    public static class PointResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            Object x = obj.resolveValue("x");
            Object y = obj.resolveValue("y");
            return MessageUtil.format(Messages.EclipseNameResolver_Point, x, y);
        }
    }

    /*
     * Contributed in bug 273915
     */
    @Subjects({"org.eclipse.swt.graphics.Rectangle",
            "java.awt.Rectangle"})
    public static class RectangleResolver implements IClassSpecificNameResolver {
        public String resolve(IObject obj) throws SnapshotException {
            Object x = obj.resolveValue("x");
            Object y = obj.resolveValue("y");
            Object width = obj.resolveValue("width");
            Object height = obj.resolveValue("height");
            return MessageUtil.format(Messages.EclipseNameResolver_Rectangle, x, y, width, height);
        }
    }
}