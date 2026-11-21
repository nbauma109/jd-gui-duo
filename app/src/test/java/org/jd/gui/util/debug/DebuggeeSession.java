/*
 * © 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.debug;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;

import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.MethodEntryRequest;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Launches {@link EvalTarget} in a real JVM under JDI and keeps its main thread suspended in {@code main}. */
final class DebuggeeSession implements AutoCloseable {

    private final VirtualMachine vm;
    private final ThreadReference thread;
    private final int mainFrame;

    private DebuggeeSession(VirtualMachine vm, ThreadReference thread, int mainFrame) {
        this.vm = vm;
        this.thread = thread;
        this.mainFrame = mainFrame;
    }

    /** A formatted value without the object id ("text" (id=12) becomes "text"): ids change from one run to another. */
    static String withoutId(String shown) {
        int id = shown.lastIndexOf("(id=");
        return id < 0 ? shown : shown.substring(0, id).stripTrailing();
    }

    /** The index of the frame of the method with the given name (0 is the top of the stack), or -1. */
    int frameOf(String method) {
        try {
            int index = 0;
            for (com.sun.jdi.StackFrame frame : thread.frames()) {
                if (method.equals(frame.location().method().name())) {
                    return index;
                }
                index++;
            }
        } catch (com.sun.jdi.IncompatibleThreadStateException e) {
            throw new IllegalStateException(e);
        }
        return -1;
    }

    ThreadReference thread() {
        return thread;
    }

    /** The frame of {@code main}, which holds the locals. */
    int mainFrame() {
        return mainFrame;
    }

    static DebuggeeSession start(String mainClass) throws Exception {
        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("main").setValue(mainClass);
        String classes = Paths.get(EvalTarget.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        arguments.get("options").setValue("-cp \"" + classes + "\"");
        VirtualMachine vm = connector.launch(arguments);
        vm.resume();

        // The program prepares its locals, then loops over breakHere(). Method calls can only be made on a thread
        // that an event stopped (not on one that vm.suspend() stopped), so stop it on the entry of breakHere().
        ReferenceType type = waitForClass(vm, mainClass);
        MethodEntryRequest entry = vm.eventRequestManager().createMethodEntryRequest();
        entry.addClassFilter(type);
        entry.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        entry.enable();
        while (true) {
            EventSet events = vm.eventQueue().remove(120_000);
            if (events == null) {
                vm.exit(1);
                throw new IllegalStateException("The debuggee never reached breakHere()");
            }
            for (Event event : events) {
                if (event instanceof MethodEntryEvent hit && "breakHere".equals(hit.method().name())) {
                    int main = frameIndexOfMain(hit.thread()); // before the request goes: deleting it may let the thread run
                    // the calls the evaluator makes in the debuggee must not stop it again
                    vm.eventRequestManager().deleteEventRequest(entry);
                    return new DebuggeeSession(vm, hit.thread(), main);
                }
            }
            events.resume();
        }
    }

    /** The frame of main: the stack is breakHere, then hold or main (the program calls hold first, then breakHere itself). */
    private static int frameIndexOfMain(ThreadReference thread) throws com.sun.jdi.IncompatibleThreadStateException {
        int index = 0;
        for (com.sun.jdi.StackFrame frame : thread.frames()) {
            if ("main".equals(frame.location().method().name())) {
                return index;
            }
            index++;
        }
        throw new IllegalStateException("main is not on the stack");
    }

    /** Waits up to two minutes: the macOS runners can be very slow to start a JVM. */
    private static ReferenceType waitForClass(VirtualMachine vm, String name) {
        for (int attempt = 0; attempt < 2400; attempt++) {
            List<ReferenceType> types = vm.classesByName(name);
            if (!types.isEmpty()) {
                return types.get(0);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
        }
        throw new IllegalStateException("The debuggee never loaded " + name);
    }

    @Override
    public void close() {
        try {
            vm.exit(0);
        } catch (RuntimeException _) {
            // already gone
        }
    }
}
