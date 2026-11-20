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
            EventSet events = vm.eventQueue().remove(30_000);
            if (events == null) {
                vm.exit(1);
                throw new IllegalStateException("The debuggee never reached breakHere()");
            }
            for (Event event : events) {
                if (event instanceof MethodEntryEvent hit && "breakHere".equals(hit.method().name())) {
                    // the calls the evaluator makes in the debuggee must not stop it again
                    vm.eventRequestManager().deleteEventRequest(entry);
                    return new DebuggeeSession(vm, hit.thread(), 1);
                }
            }
            events.resume();
        }
    }

    private static ReferenceType waitForClass(VirtualMachine vm, String name) {
        for (int attempt = 0; attempt < 600; attempt++) {
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
