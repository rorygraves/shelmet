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
package org.eclipse.mat.internal.snapshot;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.internal.Messages;
import org.eclipse.mat.query.registry.CommandLine;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;

import java.math.BigInteger;
import java.text.ParsePosition;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ArgumentParser {
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x\\p{XDigit}+$");

    public static HeapObjectParamArgument consumeHeapObjects(ISnapshot snapshot, String line) throws SnapshotException {
        String[] args = CommandLine.tokenize(line);
        ParsePosition pos = new ParsePosition(0);
        HeapObjectParamArgument hopa = consumeHeapObjects(snapshot, args, pos);

        if (pos.getIndex() < args.length) {
            StringBuilder buf = new StringBuilder();
            while (pos.getIndex() < args.length) {
                buf.append(args[pos.getIndex()]).append(" ");
                pos.setIndex(pos.getIndex() + 1);
            }

            throw new SnapshotException(MessageUtil.format(Messages.ArgumentParser_ErrorMsg_Unparsed, new Object[]{buf
                    .toString()}));
        }

        return hopa;
    }

    public static HeapObjectParamArgument consumeHeapObjects(ISnapshot snapshot, String[] args, ParsePosition pos)
            throws SnapshotException {
        if (pos.getIndex() >= args.length)
            return null;

        HeapObjectParamArgument proxy = new HeapObjectParamArgument(snapshot);

        while (pos.getIndex() < args.length) {
            String arg = args[pos.getIndex()];

            if (arg == null) {
                pos.setIndex(pos.getIndex() + 1);
            } else if (HeapObjectParamArgument.Flags.VERBOSE.equals(arg)) {
                pos.setIndex(pos.getIndex() + 1);

                proxy.setVerbose(true);
            } else if (HeapObjectParamArgument.Flags.INCLUDE_SUBCLASSES.equals(arg)) {
                pos.setIndex(pos.getIndex() + 1);

                proxy.setIncludeSubclasses(true);
            } else if (HeapObjectParamArgument.Flags.INCLUDE_CLASS_INSTANCE.equals(arg)) {
                pos.setIndex(pos.getIndex() + 1);

                proxy.setIncludeClassInstance(true);
            } else if (HeapObjectParamArgument.Flags.INCLUDE_LOADED_INSTANCES.equals(arg)) {
                pos.setIndex(pos.getIndex() + 1);

                proxy.setIncludeLoadedInstances(true);
            } else if (HeapObjectParamArgument.Flags.RETAINED.equals(arg)) {
                pos.setIndex(pos.getIndex() + 1);

                proxy.setRetained(true);
            } else if (arg.charAt(0) == '-') {
                break;
            } else if (ADDRESS_PATTERN.matcher(arg).matches()) {
                long address = new BigInteger(arg.substring(2), 16).longValue();
                proxy.addObjectAddress(address);
                pos.setIndex(pos.getIndex() + 1);
            } else if ("select".equalsIgnoreCase(arg)) {
                StringBuilder query = new StringBuilder(128);
                query.append(arg);
                pos.setIndex(pos.getIndex() + 1);

                while (pos.getIndex() < args.length) {
                    arg = args[pos.getIndex()];
                    if (arg.length() == 1 && arg.charAt(0) == ';') {
                        pos.setIndex(pos.getIndex() + 1);
                        break;
                    } else if (arg.charAt(arg.length() - 1) == ';') {
                        query.append(" ").append(arg.substring(0, arg.length() - 1));
                        pos.setIndex(pos.getIndex() + 1);
                        break;
                    } else if (arg.charAt(0) == '-') {
                        break;
                    }
                    query.append(" ").append(arg);
                    pos.setIndex(pos.getIndex() + 1);
                }
                proxy.addOql(query.toString());
            } else {
                try {
                    Pattern pattern = Pattern.compile(PatternUtil.smartFix(arg, false));
                    proxy.addPattern(pattern);
                    pos.setIndex(pos.getIndex() + 1);
                } catch (PatternSyntaxException e) {
                    throw new SnapshotException(MessageUtil.format(Messages.ArgumentParser_ErrorMsg_ParsingError, arg, e.getMessage()),
                            e);
                }
            }
        }

        return proxy;
    }
}
