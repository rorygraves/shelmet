/*******************************************************************************
 * Copyright (c) 2008, 2013 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    IBM Corporation - additional debug information
 *******************************************************************************/
package org.eclipse.mat.hprof;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.hprof.ui.HprofPreferences;
import org.eclipse.mat.parser.io.PositionInputStream;
import org.eclipse.mat.parser.model.ClassImpl;
import org.eclipse.mat.snapshot.MultipleSnapshotsException;
import org.eclipse.mat.snapshot.model.*;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.IProgressListener.Severity;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.SimpleMonitor;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pass1Parser extends AbstractParser {
    private static final Pattern PATTERN_OBJ_ARRAY = Pattern.compile("^(\\[+)L(.*);$");
    private static final Pattern PATTERN_PRIMITIVE_ARRAY = Pattern.compile("^(\\[+)(.)$");

    private HashMapLongObject<String> class2name = new HashMapLongObject<>();
    private HashMapLongObject<Long> thread2id = new HashMapLongObject<>();
    private HashMapLongObject<StackFrame> id2frame = new HashMapLongObject<>();
    private HashMapLongObject<StackTrace> serNum2stackTrace = new HashMapLongObject<>();
    private HashMapLongObject<Long> classSerNum2id = new HashMapLongObject<>();
    private HashMapLongObject<List<JavaLocal>> thread2locals = new HashMapLongObject<>();
    private IHprofParserHandler handler;
    private SimpleMonitor.Listener monitor;
    private long previousArrayStart;
    private long previousArrayUncompressedEnd;
    private boolean foundCompressed;

    public Pass1Parser(IHprofParserHandler handler, SimpleMonitor.Listener monitor,
                       HprofPreferences.HprofStrictness strictnessPreference) {
        super(strictnessPreference);
        this.handler = handler;
        this.monitor = monitor;
    }

    public void read(File file, String dumpNrToRead) throws SnapshotException, IOException {
        // See http://java.net/downloads/heap-snapshot/hprof-binary-format.html
        in = new PositionInputStream(new BufferedInputStream(new FileInputStream(file)));

        int currentDumpNr = 0;
        List<MultipleSnapshotsException.Context> ctxs = new ArrayList<>();
        boolean foundDump = false;

        try {
            // header & version
            version = readVersion(in);
            handler.addProperty(IHprofParserHandler.VERSION, version.toString());

            // identifierSize (32 or 64 bit)
            idSize = in.readInt();
            if (idSize != 4 && idSize != 8)
                throw new SnapshotException(Messages.Pass1Parser_Error_SupportedDumps);
            handler.addProperty(IHprofParserHandler.IDENTIFIER_SIZE, String.valueOf(idSize));

            // creation date
            long date = in.readLong();

            long prevTimeOffset = 0;
            long timeWrap = 0;

            long fileSize = file.length();
            long curPos = in.position();

            while (curPos < fileSize) {
                if (monitor.isProbablyCanceled())
                    throw new IProgressListener.OperationCanceledException();
                monitor.totalWorkDone(curPos / 1000);

                int record = in.readUnsignedByte();

                long timeOffset = readUnsignedInt(); // time stamp in microseconds
                if (timeOffset < prevTimeOffset) {
                    // Wrap after 4294 seconds
                    timeWrap += 1L << 32;
                }
                prevTimeOffset = timeOffset;

                long length = readUnsignedInt();

                length = updateLengthIfNecessary(fileSize, curPos, record, length, monitor);

                if (length < 0)
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_IllegalRecordLength,
                            length, in.position(), record));

                if (curPos + length - 9 > fileSize) {
                    switch (strictnessPreference) {
                        case STRICTNESS_STOP:
                            throw new SnapshotException(Messages.HPROFStrictness_Stopped, new SnapshotException(
                                    MessageUtil.format(Messages.Pass1Parser_Error_invalidHPROFFile, length,
                                            fileSize - curPos - 9)));
                        case STRICTNESS_WARNING:
                        case STRICTNESS_PERMISSIVE:
                            monitor.sendUserMessage(Severity.WARNING,
                                    MessageUtil.format(Messages.Pass1Parser_Error_invalidHPROFFile, length,
                                            fileSize - curPos - 9), null);
                            break;
                        default:
                            throw new SnapshotException(Messages.HPROFStrictness_Unhandled_Preference);
                    }
                }

                switch (record) {
                    case Constants.Record.STRING_IN_UTF8:
                        if (((int) (length - idSize) < 0))
                            throw new SnapshotException(MessageUtil.format(
                                    Messages.Pass1Parser_Error_IllegalRecordLength, length, in.position(),
                                    record));
                        readString(length);
                        break;
                    case Constants.Record.LOAD_CLASS:
                        readLoadClass();
                        break;
                    case Constants.Record.STACK_FRAME:
                        readStackFrame(length);
                        break;
                    case Constants.Record.STACK_TRACE:
                        readStackTrace(length);
                        break;
                    case Constants.Record.HEAP_DUMP:
                    case Constants.Record.HEAP_DUMP_SEGMENT:
                        long dumpTime = date + (timeWrap + timeOffset) / 1000;
                        if (dumpMatches(currentDumpNr, dumpNrToRead)) {
                            if (!foundDump) {
                                handler.addProperty(IHprofParserHandler.CREATION_DATE, String.valueOf(dumpTime));
                                foundDump = true;
                            }
                            readDumpSegments(length);
                        } else
                            in.skipBytes(length);
                        if (ctxs.size() < currentDumpNr + 1) {
                            MultipleSnapshotsException.Context ctx = new MultipleSnapshotsException.Context(dumpIdentifier(currentDumpNr));
                            ctx.setDescription(MessageUtil.format(Messages.Pass1Parser_HeapDumpCreated, new Date(dumpTime)));
                            ctxs.add(ctx);
                        }

                        if (record == Constants.Record.HEAP_DUMP)
                            currentDumpNr++;

                        break;
                    case Constants.Record.HEAP_DUMP_END:
                        currentDumpNr++;
                        in.skipBytes(length);
                        break;
                    case Constants.Record.UNLOAD_CLASS:
                    case Constants.Record.ALLOC_SITES:
                    case Constants.Record.HEAP_SUMMARY:
                    case Constants.Record.START_THREAD:
                    case Constants.Record.END_THREAD:
                    case Constants.Record.CPU_SAMPLES:
                    case Constants.Record.CONTROL_SETTINGS:
                        in.skipBytes(length);
                        break;
                    default:
                        switch (strictnessPreference) {
                            case STRICTNESS_STOP:
                                throw new SnapshotException(Messages.HPROFStrictness_Stopped, new SnapshotException(
                                        MessageUtil.format(Messages.Pass1Parser_UnexpectedRecord,
                                                Integer.toHexString(record), length)));
                            case STRICTNESS_WARNING:
                            case STRICTNESS_PERMISSIVE:
                                monitor.sendUserMessage(
                                        Severity.WARNING,
                                        MessageUtil.format(Messages.Pass1Parser_UnexpectedRecord,
                                                Integer.toHexString(record), length), null);
                                in.skipBytes(length);
                                break;
                            default:
                                throw new SnapshotException(Messages.HPROFStrictness_Unhandled_Preference);
                        }
                        break;
                }

                curPos = in.position();
            }
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
            }
        }

        if (!foundDump)
            throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_NoHeapDumpIndexFound,
                    currentDumpNr, file.getName(), dumpNrToRead));

        if (currentDumpNr > 1) {
            if (dumpNrToRead == null) {
                MultipleSnapshotsException mse = new MultipleSnapshotsException(MessageUtil.format(Messages.Pass1Parser_HeapDumpsFound, currentDumpNr));
                for (MultipleSnapshotsException.Context runtime : ctxs) {
                    mse.addContext(runtime);
                }
                throw mse;
            }
            monitor.sendUserMessage(IProgressListener.Severity.INFO, MessageUtil.format(
                    Messages.Pass1Parser_Info_UsingDumpIndex, currentDumpNr, file.getName(), dumpNrToRead),
                    null);
        }

        if (serNum2stackTrace.size() > 0)
            dumpThreads();

    }

    private void readString(long length) throws IOException {
        long id = readID();
        byte[] chars = new byte[(int) (length - idSize)];
        in.readFully(chars);
        handler.getConstantPool().put(id, new String(chars, "UTF-8"));
    }

    private void readLoadClass() throws IOException {
        long classSerNum = readUnsignedInt(); // used in stacks frames
        long classID = readID();
        in.skipBytes(4);
        long nameID = readID();

        String className = getStringConstant(nameID).replace('/', '.');
        class2name.put(classID, className);
        classSerNum2id.put(classSerNum, classID);
    }

    private void readStackFrame(long length) throws IOException {
        long frameId = readID();
        long methodName = readID();
        long methodSig = readID();
        long srcFile = readID();
        long classSerNum = readUnsignedInt();
        int lineNr = in.readInt(); // can be negative
        StackFrame frame = new StackFrame(frameId, lineNr, getStringConstant(methodName), getStringConstant(methodSig),
                getStringConstant(srcFile), classSerNum);
        id2frame.put(frameId, frame);
    }

    private void readStackTrace(long length) throws IOException {
        long stackTraceNr = readUnsignedInt();
        long threadNr = readUnsignedInt();
        long frameCount = readUnsignedInt();
        long[] frameIds = new long[(int) frameCount];
        for (int i = 0; i < frameCount; i++) {
            frameIds[i] = readID();
        }
        StackTrace stackTrace = new StackTrace(stackTraceNr, threadNr, frameIds);
        serNum2stackTrace.put(stackTraceNr, stackTrace);
    }

    private void readDumpSegments(long length) throws IOException, SnapshotException {
        long segmentStartPos = in.position();
        long segmentsEndPos = segmentStartPos + length;

        while (segmentStartPos < segmentsEndPos) {
            long workDone = segmentStartPos / 1000;
            if (this.monitor.getWorkDone() < workDone) {
                if (this.monitor.isProbablyCanceled())
                    throw new IProgressListener.OperationCanceledException();
                this.monitor.totalWorkDone(workDone);
            }

            int segmentType = in.readUnsignedByte();
            switch (segmentType) {
                case Constants.DumpSegment.ROOT_UNKNOWN:
                    readGC(GCRootInfo.Type.UNKNOWN, 0);
                    break;
                case Constants.DumpSegment.ROOT_THREAD_OBJECT:
                    readGCThreadObject(GCRootInfo.Type.THREAD_OBJ);
                    break;
                case Constants.DumpSegment.ROOT_JNI_GLOBAL:
                    readGC(GCRootInfo.Type.NATIVE_STACK, idSize);
                    break;
                case Constants.DumpSegment.ROOT_JNI_LOCAL:
                    readGCWithThreadContext(GCRootInfo.Type.NATIVE_LOCAL, true);
                    break;
                case Constants.DumpSegment.ROOT_JAVA_FRAME:
                    readGCWithThreadContext(GCRootInfo.Type.JAVA_LOCAL, true);
                    break;
                case Constants.DumpSegment.ROOT_NATIVE_STACK:
                    readGCWithThreadContext(GCRootInfo.Type.NATIVE_STACK, false);
                    break;
                case Constants.DumpSegment.ROOT_STICKY_CLASS:
                    readGC(GCRootInfo.Type.SYSTEM_CLASS, 0);
                    break;
                case Constants.DumpSegment.ROOT_THREAD_BLOCK:
                    readGC(GCRootInfo.Type.THREAD_BLOCK, 4);
                    break;
                case Constants.DumpSegment.ROOT_MONITOR_USED:
                    readGC(GCRootInfo.Type.BUSY_MONITOR, 0);
                    break;
                case Constants.DumpSegment.CLASS_DUMP:
                    readClassDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.INSTANCE_DUMP:
                    readInstanceDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.OBJECT_ARRAY_DUMP:
                    readObjectArrayDump(segmentStartPos);
                    break;
                case Constants.DumpSegment.PRIMITIVE_ARRAY_DUMP:
                    readPrimitiveArrayDump(segmentStartPos);
                    break;
                default:
                    throw new SnapshotException(MessageUtil.format(Messages.Pass1Parser_Error_InvalidHeapDumpFile,
                            segmentType, segmentStartPos));
            }

            segmentStartPos = in.position();
        }
        if (segmentStartPos != segmentsEndPos) {
            switch (strictnessPreference) {
                case STRICTNESS_STOP:
                    throw new SnapshotException(Messages.HPROFStrictness_Stopped,
                            new SnapshotException(
                                    MessageUtil.format(Messages.Pass1Parser_UnexpectedEndPosition,
                                            Long.toHexString(segmentsEndPos - length), length,
                                            Long.toHexString(segmentStartPos),
                                            Long.toHexString(segmentsEndPos))));
                case STRICTNESS_WARNING:
                case STRICTNESS_PERMISSIVE:
                    monitor.sendUserMessage(Severity.WARNING, MessageUtil.format(
                            Messages.Pass1Parser_UnexpectedEndPosition,
                            Long.toHexString(segmentsEndPos - length), length,
                            Long.toHexString(segmentStartPos), Long.toHexString(segmentsEndPos)), null);
                    break;
                default:
                    throw new SnapshotException(Messages.HPROFStrictness_Unhandled_Preference);
            }
        }
    }

    private void readGCThreadObject(int gcType) throws IOException {
        long id = readID();
        int threadSerialNo = in.readInt();
        thread2id.put(threadSerialNo, id);
        handler.addGCRoot(id, 0, gcType);

        in.skipBytes(4);
    }

    private void readGC(int gcType, int skip) throws IOException {
        long id = readID();
        handler.addGCRoot(id, 0, gcType);

        if (skip > 0)
            in.skipBytes(skip);
    }

    private void readGCWithThreadContext(int gcType, boolean hasLineInfo) throws IOException {
        long id = readID();
        int threadSerialNo = in.readInt();
        Long tid = thread2id.get(threadSerialNo);
        if (tid != null) {
            handler.addGCRoot(id, tid, gcType);
        } else {
            handler.addGCRoot(id, 0, gcType);
        }

        if (hasLineInfo) {
            int lineNumber = in.readInt();
            List<JavaLocal> locals = thread2locals.get(threadSerialNo);
            if (locals == null) {
                locals = new ArrayList<JavaLocal>();
                thread2locals.put(threadSerialNo, locals);
            }
            locals.add(new JavaLocal(id, lineNumber, gcType));
        }
    }

    private void readClassDump(long segmentStartPos) throws IOException {
        long address = readID();
        in.skipBytes(4); // stack trace serial number
        long superClassObjectId = readID();
        long classLoaderObjectId = readID();

        // skip signers, protection domain, reserved ids (2), instance size
        in.skipBytes(this.idSize * 4 + 4);

        // constant pool: u2 ( u2 u1 value )*
        int constantPoolSize = in.readUnsignedShort();
        for (int ii = 0; ii < constantPoolSize; ii++) {
            in.skipBytes(2); // index
            skipValue(); // value
        }

        // static fields: u2 num ( name ID, u1 type, value)
        int numStaticFields = in.readUnsignedShort();
        Field[] statics = new Field[numStaticFields];

        for (int ii = 0; ii < numStaticFields; ii++) {
            long nameId = readID();
            String name = getStringConstant(nameId);

            byte type = in.readByte();

            Object value = readValue(null, type);
            statics[ii] = new Field(name, type, value);
        }

        // instance fields: u2 num ( name ID, u1 type )
        int numInstanceFields = in.readUnsignedShort();
        FieldDescriptor[] fields = new FieldDescriptor[numInstanceFields];

        for (int ii = 0; ii < numInstanceFields; ii++) {
            long nameId = readID();
            String name = getStringConstant(nameId);

            byte type = in.readByte();
            fields[ii] = new FieldDescriptor(name, type);
        }

        // get name
        String className = class2name.get(address);
        if (className == null)
            className = "unknown-name@0x" + Long.toHexString(address);

        if (className.charAt(0) == '[') // quick check if array at hand
        {
            // fix object class names
            Matcher matcher = PATTERN_OBJ_ARRAY.matcher(className);
            if (matcher.matches()) {
                int l = matcher.group(1).length();
                className = matcher.group(2);
                for (int ii = 0; ii < l; ii++)
                    className += "[]";
            }

            // primitive arrays
            matcher = PATTERN_PRIMITIVE_ARRAY.matcher(className);
            if (matcher.matches()) {
                int count = matcher.group(1).length() - 1;
                className = "unknown[]";

                char signature = matcher.group(2).charAt(0);
                for (int ii = 0; ii < IPrimitiveArray.SIGNATURES.length; ii++) {
                    if (IPrimitiveArray.SIGNATURES[ii] == (byte) signature) {
                        className = IPrimitiveArray.TYPE[ii];
                        break;
                    }
                }

                for (int ii = 0; ii < count; ii++)
                    className += "[]";
            }
        }

        ClassImpl clazz = new ClassImpl(address, className, superClassObjectId, classLoaderObjectId, statics, fields);
        handler.addClass(clazz, segmentStartPos);
    }

    private void readInstanceDump(long segmentStartPos) throws IOException {
        long address = readID();
        handler.reportInstance(address, segmentStartPos);
        in.skipBytes(idSize + 4);
        int payload = in.readInt();
        in.skipBytes(payload);
    }

    private void readObjectArrayDump(long segmentStartPos) throws IOException {
        long address = readID();
        if (!foundCompressed && idSize == 8 && address > previousArrayStart && address < previousArrayUncompressedEnd) {
            monitor.sendUserMessage(
                    Severity.INFO,
                    MessageUtil.format(Messages.Pass1Parser_DetectedCompressedReferences,
                            Long.toHexString(address), Long.toHexString(previousArrayStart)), null);
            handler.addProperty(IHprofParserHandler.REFERENCE_SIZE, "4");
            foundCompressed = true;
        }

        handler.reportInstance(address, segmentStartPos);

        in.skipBytes(4);
        int size = in.readInt();
        if(size > 10000)
            System.out.println("LargeObj; " + address + "  " + size);
        long arrayClassObjectID = readID();

        // check if class needs to be created
        IClass arrayType = handler.lookupClass(arrayClassObjectID);
        if (arrayType == null)
            handler.reportRequiredObjectArray(arrayClassObjectID);

        in.skipBytes((long) size * idSize);
        previousArrayStart = address;
        previousArrayUncompressedEnd = address + 16 + (long) size * 8;
    }

    private void readPrimitiveArrayDump(long segmentStartPos) throws SnapshotException, IOException {
        long address = readID();
        handler.reportInstance(address, segmentStartPos);

        in.skipBytes(4);
        int size = in.readInt();
        byte elementType = in.readByte();

        if ((elementType < IPrimitiveArray.Type.BOOLEAN) || (elementType > IPrimitiveArray.Type.LONG))
            throw new SnapshotException(Messages.Pass1Parser_Error_IllegalType);

        // check if class needs to be created
        String name = IPrimitiveArray.TYPE[elementType];
        IClass clazz = handler.lookupClassByName(name, true);
        if (clazz == null)
            handler.reportRequiredPrimitiveArray(elementType);

        int elementSize = IPrimitiveArray.ELEMENT_SIZE[elementType];
        in.skipBytes((long) elementSize * size);
    }

    private String getStringConstant(long address) {
        if (address == 0L)
            return "";

        String result = handler.getConstantPool().get(address);
        return result == null ? Messages.Pass1Parser_Error_UnresolvedName + Long.toHexString(address) : result;
    }

    private void dumpThreads() {
        // noticed that one stack trace with empty stack is always reported,
        // even if the dump has no call stacks info
        if (serNum2stackTrace == null || serNum2stackTrace.size() <= 1)
            return;

        PrintWriter out = null;
        String outputName = handler.getSnapshotInfo().getPrefix() + "threads";
        try {
            out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputName), "UTF-8"));

            Iterator<StackTrace> it = serNum2stackTrace.values();
            while (it.hasNext()) {
                StackTrace stack = it.next();
                Long tid = thread2id.get(stack.threadSerialNr);
                if (tid == null)
                    continue;
                String threadId = tid == null ? "<unknown>" : "0x" + Long.toHexString(tid);
                out.println("Thread " + threadId);
                out.println(stack);
                out.println("  locals:");
                List<JavaLocal> locals = thread2locals.get(stack.threadSerialNr);
                if (locals != null) {
                    for (JavaLocal javaLocal : locals) {
                        out.println("    objectId=0x" + Long.toHexString(javaLocal.objectId) + ", line=" + javaLocal.lineNumber);

                    }
                }
                out.println();
            }
            out.flush();
            this.monitor.sendUserMessage(Severity.INFO,
                    MessageUtil.format(Messages.Pass1Parser_Info_WroteThreadsTo, outputName), null);
        } catch (IOException e) {
            this.monitor.sendUserMessage(Severity.WARNING,
                    MessageUtil.format(Messages.Pass1Parser_Error_WritingThreadsInformation), e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignore) {
                    // $JL-EXC$
                }
            }
        }

    }

    private class StackFrame {
        long frameId;
        String method;
        String methodSignature;
        String sourceFile;
        long classSerNum;

        /*
         * > 0 line number 0 no line info -1 unknown location -2 compiled method
         * -3 native method
         */
        int lineNr;

        public StackFrame(long frameId, int lineNr, String method, String methodSignature, String sourceFile,
                          long classSerNum) {
            super();
            this.frameId = frameId;
            this.lineNr = lineNr;
            this.method = method;
            this.methodSignature = methodSignature;
            this.sourceFile = sourceFile;
            this.classSerNum = classSerNum;
        }

        @Override
        public String toString() {
            String className = null;
            Long classId = classSerNum2id.get(classSerNum);
            if (classId == null) {
                className = "<UNKNOWN CLASS>";
            } else {
                className = class2name.get(classId);
            }

            String sourceLocation = "";
            if (lineNr > 0) {
                sourceLocation = "(" + sourceFile + ":" + String.valueOf(lineNr) + ")";
            } else if (lineNr == 0 || lineNr == -1) {
                sourceLocation = "(Unknown Source)";
            } else if (lineNr == -2) {
                sourceLocation = "(Compiled method)";
            } else if (lineNr == -3) {
                sourceLocation = "(Native Method)";
            }

            return "  at " + className + "." + method + methodSignature + " " + sourceLocation;
        }

    }

    private class StackTrace {
        private long threadSerialNr;
        private long[] frameIds;

        public StackTrace(long serialNr, long threadSerialNr, long[] frameIds) {
            super();
            this.frameIds = frameIds;
            this.threadSerialNr = threadSerialNr;
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            for (long frameId : frameIds) {
                StackFrame frame = id2frame.get(frameId);
                if (frame != null) {
                    b.append(frame.toString());
                    b.append("\r\n");
                }

            }
            return b.toString();
        }

    }

    private class JavaLocal {
        private long objectId;
        private int lineNumber;
        private int type;

        public JavaLocal(long objectId, int lineNumber, int type) {
            super();
            this.lineNumber = lineNumber;
            this.objectId = objectId;
            this.type = type;
        }

        public int getType() {
            return type;
        }
    }
}
