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
package org.eclipse.mat.util;

import org.eclipse.mat.report.internal.Messages;

import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Class used as progress listener for the console. You can obtain one instance
 * via the {@link org.eclipse.mat.query.IQuery#execute} method if the query is run from
 * Memory Analyzer run in batch mode.
 */
public class ConsoleProgressListener implements IProgressListener {
    private PrintWriter out;
    private boolean isDone = false;
    private int workPerDot;
    private int workAccumulated;
    private int dotsPrinted;

    public ConsoleProgressListener(OutputStream out) {
        this(new PrintWriter(out));
    }

    public ConsoleProgressListener(PrintWriter out) {
        this.out = out;
    }

    public void beginTask(String name, int totalWork) {
        out.write(Messages.ConsoleProgressListener_Label_Task + " " + name + "\n");
        out.write("[");
        workPerDot = totalWork > 80 ? (totalWork / 80) : 1;
        workAccumulated = 0;
        dotsPrinted = 0;
        out.flush();
    }

    public void done() {
        if (!isDone) {
            out.write("]\n");
            out.flush();
            isDone = true;
        }

    }

    public boolean isCanceled() {
        return false;
    }

    public void setCanceled(boolean value) {
        throw new UnsupportedOperationException();
    }

    public void subTask(String name) {
        out.write("\n" + Messages.ConsoleProgressListener_Label_Subtask + " " + name + "\n[");
        for (int ii = 0; ii < dotsPrinted; ii++)
            out.write(".");
        out.flush();
    }

    public void worked(int work) {
        workAccumulated += work;

        int dotsToPrint = workAccumulated / workPerDot;
        if (dotsToPrint > 0) {
            dotsPrinted += dotsToPrint;
            for (int ii = 0; ii < dotsToPrint; ii++)
                out.write(".");
            workAccumulated -= (dotsToPrint * workPerDot);
            out.flush();
        }
    }

    public void sendUserMessage(Severity severity, String message, Throwable exception) {
        out.write("\n");

        switch (severity) {
            case INFO:
                out.write("[INFO] ");
                break;
            case WARNING:
                out.write("[WARNING] ");
                break;
            case ERROR:
                out.write("[ERROR] ");
                break;
            default:
                out.write("[UNKNOWN] ");
        }

        out.write(message);

        if (exception != null) {
            out.write("\n");
            exception.printStackTrace(out);
        }

        out.write("\n[");
        for (int ii = 0; ii < dotsPrinted; ii++)
            out.write(".");
    }

}
