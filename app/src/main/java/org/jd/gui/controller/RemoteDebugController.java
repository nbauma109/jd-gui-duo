/*
 * Copyright 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 */

package org.jd.gui.controller;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.StepRequest;
import com.sun.jdi.request.ThreadDeathRequest;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.BreakpointCapablePage;
import org.jd.gui.model.configuration.Configuration;
import org.jd.gui.model.debug.DebugThread;
import org.jd.gui.model.debug.DebugVariableNode;
import org.jd.gui.util.debug.JdiExpressionEvaluator;
import org.jd.gui.util.debug.JdiValues;
import org.jd.gui.util.swing.AbstractSwingWorker;
import org.jd.gui.util.swing.SwingUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.text.NumberFormatter;

public class RemoteDebugController {
    private static final String HOST_PREFERENCE = "UIRemoteDebug.host";
    private static final String PORT_PREFERENCE = "UIRemoteDebug.port";
    private static final String BREAKPOINTS_PREFERENCE = "UIRemoteDebug.breakpoints";
    private static final String EXCEPTIONS_PREFERENCE = "UIRemoteDebug.exceptionBreakpoints";
    /** Prefix of the Breakpoints view key of an exception breakpoint ({@code exception:java.lang.Error}). */
    public static final String EXCEPTION_KEY_PREFIX = "exception:";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5005;
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    private final Configuration configuration;
    private final MainController controller;
    private final JFrame mainFrame;
    private final Set<BreakpointKey> breakpoints = new HashSet<>();
    private final Map<BreakpointKey, List<BreakpointRequest>> installedBreakpoints = new HashMap<>();

    private transient VirtualMachine virtualMachine;
    /**
     * Threads stopped by an event. With SUSPEND_EVENT_THREAD several threads can be stopped at once
     * (e.g. three workers hitting the same breakpoint). Like Eclipse, the UI stays bound to one
     * "active" thread; the others wait in {@link #queuedThreads} until the active one is resumed.
     */
    private final transient Object threadLock = new Object();
    private transient ThreadReference activeThread;
    private transient boolean activeRunning; // a step is in flight on activeThread
    private final transient Map<ThreadReference, Location> queuedThreads = new LinkedHashMap<>();
    private transient int selectedFrameIndex; // frame shown in Variables/Expressions, 0 = top frame
    private final List<String> watchExpressions = new CopyOnWriteArrayList<>();
    private final Object evaluationLock = new Object();
    private final Set<BreakpointKey> disabledBreakpoints = new HashSet<>();
    /** Library classes that are never shown: stepping into them returns to the caller (like Eclipse's step filters). */
    private static final String[] STEP_FILTER_PREFIXES = { "java.", "javax.", "jdk.", "sun.", "com.sun." };
    /** Step to issue once a step through library code is back in application code (thread -> StepRequest depth). */
    private final transient Map<ThreadReference, Integer> pendingSteps = new ConcurrentHashMap<>();
    /** Temporary breakpoint(s) of a pending "Run to Line"; removed as soon as the thread stops anywhere. */
    private final transient List<BreakpointRequest> runToLineRequests = new CopyOnWriteArrayList<>();
    private volatile boolean connected;
    private volatile boolean skipAllBreakpoints;
    /** Method descriptions ({@code main(String[])}) read from the class files, so labels also work while detached. */
    private final Map<BreakpointKey, String> methodDescriptions = new ConcurrentHashMap<>();
    private final Map<String, BreakpointCapablePage> pagesByType = new ConcurrentHashMap<>();
    private final Map<String, ExceptionBreakpoint> exceptionBreakpoints = new ConcurrentHashMap<>();
    private final Set<String> disabledExceptions = ConcurrentHashMap.newKeySet();
    private final Map<String, List<ExceptionRequest>> installedExceptions = new ConcurrentHashMap<>();

    public RemoteDebugController(Configuration configuration, MainController controller, JFrame mainFrame) {
        this.configuration = configuration;
        this.controller = controller;
        this.mainFrame = mainFrame;
    }

    /** Restores the breakpoints saved in the configuration by a previous session. */
    public void loadPersistedBreakpoints() {
        String savedExceptions = configuration.getPreferences().get(EXCEPTIONS_PREFERENCE);
        if (savedExceptions != null && !savedExceptions.isBlank()) {
            for (String entry : savedExceptions.split(";")) {
                String[] parts = entry.split(":");
                if (parts.length == 4) {
                    exceptionBreakpoints.put(parts[0], new ExceptionBreakpoint(parts[0], "1".equals(parts[1]), "1".equals(parts[2])));
                    if ("0".equals(parts[3])) {
                        disabledExceptions.add(parts[0]);
                    }
                }
            }
        }
        String saved = configuration.getPreferences().get(BREAKPOINTS_PREFERENCE);
        if (saved == null || saved.isBlank()) {
            publishBreakpoints();
            return;
        }
        for (String entry : saved.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length < 2) {
                continue;
            }
            try {
                BreakpointKey key = new BreakpointKey(parts[0], Integer.parseInt(parts[1]));
                breakpoints.add(key);
                if (parts.length > 2 && "0".equals(parts[2])) {
                    disabledBreakpoints.add(key);
                }
            } catch (NumberFormatException ignored) {
                // corrupted entry — skip it
            }
        }
        publishBreakpoints();
    }

    private void persistBreakpoints() {
        String value = breakpoints.stream()
            .sorted(Comparator.comparing(BreakpointKey::typeName).thenComparingInt(BreakpointKey::lineNumber))
            .map(key -> key.typeName() + ":" + key.lineNumber() + ":" + (disabledBreakpoints.contains(key) ? "0" : "1"))
            .collect(java.util.stream.Collectors.joining(";"));
        configuration.getPreferences().put(BREAKPOINTS_PREFERENCE, value);
        configuration.getPreferences().put(EXCEPTIONS_PREFERENCE, exceptionBreakpoints.values().stream()
            .sorted(Comparator.comparing(ExceptionBreakpoint::typeName))
            .map(e -> e.typeName() + ":" + (e.caught() ? "1" : "0") + ":" + (e.uncaught() ? "1" : "0") + ":" + (disabledExceptions.contains(e.typeName()) ? "0" : "1"))
            .collect(java.util.stream.Collectors.joining(";")));
    }

    /** "Add Java Exception Breakpoint": asks for the exception class and when to stop. */
    public void showAddExceptionBreakpointDialog() {
        JTextField nameField = new JTextField(32);
        JCheckBox caughtBox = new JCheckBox("Caught", true);
        JCheckBox uncaughtBox = new JCheckBox("Uncaught", true);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new java.awt.Insets(2, 2, 2, 2);
        panel.add(new JLabel("Exception class (fully qualified, e.g. java.lang.NullPointerException):"), constraints);
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(nameField, constraints);
        constraints.gridy = 2;
        panel.add(caughtBox, constraints);
        constraints.gridy = 3;
        panel.add(uncaughtBox, constraints);
        while (JOptionPane.showConfirmDialog(mainFrame, panel, "Add Java Exception Breakpoint", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (!name.matches("[\\p{L}_$][\\p{L}\\p{N}_$]*(\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*")) {
                JOptionPane.showMessageDialog(mainFrame, "Enter a fully qualified class name.", "Add Java Exception Breakpoint", JOptionPane.WARNING_MESSAGE);
            } else if (!caughtBox.isSelected() && !uncaughtBox.isSelected()) {
                JOptionPane.showMessageDialog(mainFrame, "Select Caught, Uncaught or both.", "Add Java Exception Breakpoint", JOptionPane.WARNING_MESSAGE);
            } else {
                addExceptionBreakpoint(name, caughtBox.isSelected(), uncaughtBox.isSelected());
                return;
            }
        }
    }

    public void addExceptionBreakpoint(String typeName, boolean caught, boolean uncaught) {
        uninstallException(typeName);
        exceptionBreakpoints.put(typeName, new ExceptionBreakpoint(typeName, caught, uncaught));
        disabledExceptions.remove(typeName);
        installException(exceptionBreakpoints.get(typeName));
        publishBreakpoints();
    }

    private void installException(ExceptionBreakpoint breakpoint) {
        VirtualMachine vm = virtualMachine;
        if (vm == null) {
            return;
        }
        for (ReferenceType referenceType : vm.classesByName(breakpoint.typeName())) {
            installException(referenceType, breakpoint);
        }
    }

    private void installException(ReferenceType referenceType, ExceptionBreakpoint breakpoint) {
        VirtualMachine vm = virtualMachine;
        if (vm == null) {
            return;
        }
        try {
            ExceptionRequest request = vm.eventRequestManager().createExceptionRequest(referenceType, breakpoint.caught(), breakpoint.uncaught());
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            request.setEnabled(!skipAllBreakpoints && !disabledExceptions.contains(breakpoint.typeName()));
            installedExceptions.computeIfAbsent(breakpoint.typeName(), _ -> new CopyOnWriteArrayList<>()).add(request);
        } catch (RuntimeException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    private void uninstallException(String typeName) {
        VirtualMachine vm = virtualMachine;
        List<ExceptionRequest> requests = installedExceptions.remove(typeName);
        if (vm != null && requests != null) {
            vm.eventRequestManager().deleteEventRequests(new ArrayList<>(requests));
        }
    }

    private String exceptionLabel(ExceptionBreakpoint breakpoint) {
        String when = breakpoint.caught() && breakpoint.uncaught() ? "caught and uncaught" : breakpoint.caught() ? "caught" : "uncaught";
        return JdiValues.simpleName(breakpoint.typeName()) + " [" + when + "]";
    }

    public void show() {
        JTextField hostField = new JTextField(configuration.getPreferences().getOrDefault(HOST_PREFERENCE, DEFAULT_HOST), 28);
        JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(getSavedPort(), 1, 65535, 1));
        JFormattedTextField portField = ((JSpinner.NumberEditor) portSpinner.getEditor()).getTextField();
        ((NumberFormatter) portField.getFormatter()).setAllowsInvalid(false);

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.LINE_END;
        fields.add(new JLabel("Host:"), constraints);

        constraints.gridx = 1;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        fields.add(hostField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.LINE_END;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.0;
        fields.add(new JLabel("Port:"), constraints);

        constraints.gridx = 1;
        constraints.anchor = GridBagConstraints.LINE_START;
        fields.add(portSpinner, constraints);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Attach to a JVM started with JDWP remote debugging enabled."), BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(mainFrame, panel, "Remote Debug", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String host = hostField.getText().trim();
            if (host.isEmpty()) {
                host = DEFAULT_HOST;
            }
            int port = ((Number) portSpinner.getValue()).intValue();
            configuration.getPreferences().put(HOST_PREFERENCE, host);
            configuration.getPreferences().put(PORT_PREFERENCE, Integer.toString(port));
            new AttachWorker(controller, mainFrame, host, port).execute();
        }
    }

    public boolean toggleBreakpoint(String typeName, int lineNumber) {
        BreakpointKey key = new BreakpointKey(typeName, lineNumber);
        if (breakpoints.remove(key)) {
            uninstallBreakpoint(key);
            publishBreakpoints();
            return false;
        }

        breakpoints.add(key);
        BreakpointCapablePage page = pagesByType.get(typeName);
        String description = page == null ? null : page.getBreakpointMethodDescription(lineNumber);
        if (description != null) {
            methodDescriptions.put(key, description);
        }
        installBreakpoint(key);
        publishBreakpoints();
        return true;
    }

    public void sync(BreakpointCapablePage page) {
        String typeName = page.getDebugTypeName();
        page.setBreakpointToggleHandler(this::toggleBreakpoint);
        pagesByType.put(typeName, page);

        // Adopt breakpoints the user set locally before any debug session started
        for (int lineNumber : page.getBreakpointLines()) {
            BreakpointKey key = new BreakpointKey(typeName, lineNumber);
            if (breakpoints.add(key)) {
                installBreakpoint(key);
            }
        }

        // Push all controller-known breakpoints to the page's visual state
        for (BreakpointKey breakpoint : breakpoints) {
            if (breakpoint.typeName().equals(typeName)) {
                page.setBreakpointLine(breakpoint.lineNumber(), true);
                String description = page.getBreakpointMethodDescription(breakpoint.lineNumber());
                if (description != null) {
                    methodDescriptions.put(breakpoint, description);
                }
            }
        }

        publishBreakpoints();
    }

    public void disconnect() {
        VirtualMachine vm = virtualMachine;
        if (vm != null) {
            try {
                vm.dispose();
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
        if (connected) {
            disconnected();
        }
    }

    public void stepInto() {
        step(StepRequest.STEP_INTO);
    }

    public void stepOver() {
        step(StepRequest.STEP_OVER);
    }

    public void stepReturn() {
        step(StepRequest.STEP_OUT);
    }

    public void resume() {
        ThreadReference thread;
        synchronized (threadLock) {
            if (activeThread == null || activeRunning || virtualMachine == null) {
                return;
            }
            thread = activeThread;
            activeThread = null;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    pendingSteps.remove(thread);
                    deleteStepRequests(thread);
                    thread.resume();
                } catch (RuntimeException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
                showNextQueuedThread();
                return null;
            }
        }.execute();
    }

    /** After a resume: bind the UI to the next stopped thread, or show the running state. */
    private void showNextQueuedThread() {
        ThreadReference next = null;
        Location location = null;
        synchronized (threadLock) {
            if (activeThread != null) {
                return; // the resumed thread already stopped again; onSuspended took over
            }
            Iterator<Map.Entry<ThreadReference, Location>> it = queuedThreads.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<ThreadReference, Location> entry = it.next();
                it.remove();
                next = entry.getKey();
                location = entry.getValue();
                activeThread = next;
                activeRunning = false;
            }
        }
        if (next != null) {
            showSuspended(next, location);
        } else {
            SwingUtil.invokeLater(() -> {
                synchronized (threadLock) {
                    if (activeThread != null) {
                        return;
                    }
                }
                controller.setDebugStepActionsEnabled(false);
                controller.clearDebugExecutionLine();
            });
            publishExpressions();
        }
    }

    public boolean canStep() {
        synchronized (threadLock) {
            return connected && activeThread != null && !activeRunning;
        }
    }

    private int getSavedPort() {
        try {
            return Integer.parseInt(configuration.getPreferences().getOrDefault(PORT_PREFERENCE, Integer.toString(DEFAULT_PORT)));
        } catch (NumberFormatException e) {
            assert ExceptionUtil.printStackTrace(e);
            return DEFAULT_PORT;
        }
    }

    private void attached(VirtualMachine vm, String host, int port) {
        this.virtualMachine = vm;
        connected = true;
        resetThreads();
        controller.setDebugStepActionsEnabled(false);
        controller.setDebugConnected(true);
        controller.showDebugVariables(null, java.util.Collections.emptyList());
        publishBreakpoints();

        EventRequestManager requestManager = vm.eventRequestManager();
        ClassPrepareRequest prepareRequest = requestManager.createClassPrepareRequest();
        prepareRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        prepareRequest.enable();

        // A stepping thread that ends (e.g. stepping out of run()) must not leave the buttons disabled.
        ThreadDeathRequest deathRequest = requestManager.createThreadDeathRequest();
        deathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
        deathRequest.enable();

        for (BreakpointKey breakpoint : breakpoints) {
            installBreakpoint(breakpoint);
        }
        for (ExceptionBreakpoint exception : exceptionBreakpoints.values()) {
            installException(exception);
        }
        publishBreakpoints();

        Thread eventThread = new Thread(this::runEventLoop, "JD-GUI remote debug events");
        eventThread.setDaemon(true);
        eventThread.start();

        // Safe even when the VM is already running (suspend=n): JDI resume() is a no-op if the VM is not suspended.
        // Required when the VM was started with suspend=y to let it run past the VMStartEvent.
        vm.resume();
    }

    private void runEventLoop() {
        try {
            EventQueue queue = virtualMachine.eventQueue();
            while (connected) {
                EventSet eventSet = queue.remove();
                boolean resume = true;

                for (Event event : eventSet) {
                    if (event instanceof ClassPrepareEvent classPrepareEvent) {
                        installBreakpoints(classPrepareEvent.referenceType());
                        ExceptionBreakpoint exception = exceptionBreakpoints.get(classPrepareEvent.referenceType().name());
                        if (exception != null) {
                            installException(classPrepareEvent.referenceType(), exception);
                        }
                        publishBreakpoints();
                    } else if (event instanceof ExceptionEvent exceptionEvent) {
                        clearRunToLine();
                        pendingSteps.remove(exceptionEvent.thread());
                        deleteStepRequests(exceptionEvent.thread());
                        onSuspended(exceptionEvent.thread(), exceptionEvent.location());
                        resume = false;
                    } else if (event instanceof BreakpointEvent breakpointEvent) {
                        clearRunToLine();
                        pendingSteps.remove(breakpointEvent.thread());
                        deleteStepRequests(breakpointEvent.thread());
                        onSuspended(breakpointEvent.thread(), breakpointEvent.location());
                        resume = false;
                    } else if (event instanceof StepEvent stepEvent) {
                        int depth = ((StepRequest) stepEvent.request()).depth();
                        deleteStepRequests(stepEvent.thread());
                        if (!continueThroughLibraryCode(stepEvent.thread(), stepEvent.location(), depth)) {
                            onSuspended(stepEvent.thread(), stepEvent.location());
                        }
                        resume = false;
                    } else if (event instanceof ThreadDeathEvent deathEvent) {
                        threadDied(deathEvent.thread());
                    } else if (event instanceof VMDisconnectEvent || event instanceof VMDeathEvent) {
                        disconnected();
                        resume = false;
                    }
                }

                if (resume) {
                    eventSet.resume();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (connected) {
                assert ExceptionUtil.printStackTrace(e);
                disconnected();
            }
        }
    }

    /**
     * Called for a step that stopped in a library class, or that completes a step through one:
     * leaves the library code (and then repeats the requested step) instead of showing it.
     * Returns true when another step was issued, so the thread must not be reported as stopped.
     */
    private boolean continueThroughLibraryCode(ThreadReference thread, Location location, int depth) {
        VirtualMachine vm = virtualMachine;
        if (vm == null) {
            return false;
        }
        try {
            if (location.lineNumber() < 0 || isLibraryClass(location.declaringType().name())) {
                if (depth == StepRequest.STEP_INTO) {
                    pendingSteps.putIfAbsent(thread, StepRequest.STEP_INTO);
                }
                issueStep(vm, thread, StepRequest.STEP_OUT);
                return true;
            }
            Integer pending = pendingSteps.remove(thread);
            // Returning from library code can land exactly on the first instruction of the next line: stop there.
            if (pending != null && !isAtLineStart(location)) {
                issueStep(vm, thread, pending);
                return true;
            }
        } catch (RuntimeException e) {
            assert ExceptionUtil.printStackTrace(e);
            pendingSteps.remove(thread);
        }
        return false;
    }

    private static boolean isAtLineStart(Location location) {
        try {
            for (Location candidate : location.method().locationsOfLine(location.lineNumber())) {
                if (candidate.codeIndex() == location.codeIndex()) {
                    return true;
                }
            }
        } catch (AbsentInformationException | RuntimeException e) {
            // no line table: treat as mid-line
        }
        return false;
    }

    private static boolean isLibraryClass(String typeName) {
        if (typeName.contains("$$Lambda")) {
            return true; // JVM-generated lambda forwarders have no source
        }
        for (String prefix : STEP_FILTER_PREFIXES) {
            if (typeName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void threadDied(ThreadReference thread) {
        pendingSteps.remove(thread);
        clearRunToLine();
        boolean wasActive;
        synchronized (threadLock) {
            queuedThreads.remove(thread);
            wasActive = thread.equals(activeThread);
            if (wasActive) {
                activeThread = null;
                activeRunning = false;
            }
        }
        if (wasActive) {
            showNextQueuedThread();
        }
    }

    private void clearRunToLine() {
        VirtualMachine vm = virtualMachine;
        if (vm != null && !runToLineRequests.isEmpty()) {
            try {
                vm.eventRequestManager().deleteEventRequests(new ArrayList<>(runToLineRequests));
            } catch (RuntimeException e) {
                assert ExceptionUtil.printStackTrace(e);
            }
        }
        runToLineRequests.clear();
    }

    private void resetThreads() {
        runToLineRequests.clear();
        pendingSteps.clear();
        synchronized (threadLock) {
            activeThread = null;
            activeRunning = false;
            queuedThreads.clear();
        }
    }

    private void onSuspended(ThreadReference thread, Location location) {
        boolean takeOver;
        ThreadReference active;
        synchronized (threadLock) {
            takeOver = activeThread == null || activeThread.equals(thread);
            if (takeOver) {
                activeThread = thread;
                activeRunning = false;
                queuedThreads.remove(thread);
            } else {
                queuedThreads.put(thread, location);
            }
            active = activeThread;
        }
        if (takeOver) {
            showSuspended(thread, location);
        } else {
            // Another thread is being debugged: only refresh the thread list, keep the current location.
            List<DebugThread> threads = collectAllThreadsFrames(active);
            SwingUtil.invokeLater(() -> controller.showDebugStackFrames(threads));
        }
    }

    private void showSuspended(ThreadReference thread, Location location) {
        int frameIndex = firstApplicationFrame(thread, location);
        synchronized (threadLock) {
            selectedFrameIndex = frameIndex;
        }
        showFrame(thread, frameIndex, true);
    }

    /** An exception can be thrown deep inside library code that has no source: show the nearest application frame. */
    private int firstApplicationFrame(ThreadReference thread, Location location) {
        if (location.lineNumber() >= 0 && !isLibraryClass(location.declaringType().name())) {
            return 0;
        }
        try {
            List<StackFrame> frames = thread.frames();
            for (int i = 0; i < frames.size(); i++) {
                Location candidate = frames.get(i).location();
                if (candidate.lineNumber() >= 0 && !isLibraryClass(candidate.declaringType().name())) {
                    return i;
                }
            }
        } catch (IncompatibleThreadStateException | RuntimeException e) {
            // keep the top frame
        }
        return 0;
    }

    /**
     * Binds Variables, Expressions and the editor to one frame of a stopped thread.
     * The stack-frame tree is only refreshed when the active thread changed.
     */
    private void showFrame(ThreadReference thread, int frameIndex, boolean refreshThreads) {
        Location location;
        try {
            location = thread.frame(frameIndex).location();
        } catch (IncompatibleThreadStateException | RuntimeException e) {
            return; // thread resumed or frame vanished in the meantime
        }
        List<DebugVariableNode> variables = readVariableNodes(thread, frameIndex);
        List<DebugThread> threads = refreshThreads ? collectAllThreadsFrames(thread) : null;
        List<DebugVariableNode> expressions = evaluateExpressions();
        SwingUtil.invokeLater(() -> {
            controller.setDebugStepActionsEnabled(true);
            controller.openDebugLocation(location.declaringType().name(), location.lineNumber());
            controller.showDebugVariables(location.declaringType().name() + ":" + location.lineNumber(), variables);
            controller.showDebugExpressions(expressions);
            if (threads != null) {
                controller.showDebugStackFrames(threads);
            }
        });
    }

    /** Called when the user selects a stack frame (of any stopped thread) in the Debug tree. */
    public void selectFrame(String threadName, int frameIndex) {
        if (virtualMachine == null) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ThreadReference target = null;
                    boolean switched = false;
                    synchronized (threadLock) {
                        if (activeRunning) {
                            return null;
                        }
                        if (activeThread != null && activeThread.name().equals(threadName)) {
                            target = activeThread;
                        } else {
                            for (ThreadReference candidate : queuedThreads.keySet()) {
                                if (candidate.name().equals(threadName)) {
                                    target = candidate;
                                    break;
                                }
                            }
                            if (target == null) {
                                return null; // running thread: nothing to show
                            }
                            if (activeThread != null) {
                                queuedThreads.put(activeThread, activeThread.frame(0).location());
                            }
                            queuedThreads.remove(target);
                            activeThread = target;
                            switched = true;
                        }
                        selectedFrameIndex = frameIndex;
                    }
                    showFrame(target, frameIndex, switched);
                } catch (IncompatibleThreadStateException | RuntimeException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
                return null;
            }
        }.execute();
    }

    // --- Watch expressions --- //

    public void addWatchExpression(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        if (!trimmed.isEmpty() && !watchExpressions.contains(trimmed)) {
            watchExpressions.add(trimmed);
            publishExpressionsAsync();
        }
    }

    public void removeWatchExpression(String expression) {
        if (watchExpressions.remove(expression)) {
            publishExpressionsAsync();
        }
    }

    public void removeAllWatchExpressions() {
        watchExpressions.clear();
        publishExpressionsAsync();
    }

    private void publishExpressionsAsync() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                publishExpressions();
                return null;
            }
        }.execute();
    }

    private void publishExpressions() {
        List<DebugVariableNode> nodes = evaluateExpressions();
        SwingUtil.invokeLater(() -> controller.showDebugExpressions(nodes));
    }

    /** Evaluates every watch expression in the selected frame; blank values when nothing is stopped. */
    private List<DebugVariableNode> evaluateExpressions() {
        // Evaluations invoke methods in the debuggee thread: they must not overlap.
        synchronized (evaluationLock) {
            return evaluateExpressionsLocked();
        }
    }

    private List<DebugVariableNode> evaluateExpressionsLocked() {
        ThreadReference thread;
        int frameIndex;
        synchronized (threadLock) {
            thread = activeRunning ? null : activeThread;
            frameIndex = selectedFrameIndex;
        }
        List<DebugVariableNode> nodes = new ArrayList<>();
        for (String expression : watchExpressions) {
            if (thread == null) {
                nodes.add(new DebugVariableNode(expression, ""));
                continue;
            }
            try {
                nodes.add(JdiValues.toNode(expression, JdiExpressionEvaluator.evaluate(expression, thread, frameIndex)));
            } catch (JdiExpressionEvaluator.EvaluationException e) {
                nodes.add(new DebugVariableNode(expression, "<" + e.getMessage() + ">"));
            }
        }
        return nodes;
    }

    /**
     * Collects frames for every non-system thread.
     * With SUSPEND_EVENT_THREAD policy only the event thread is suspended;
     * other threads are still running and their frames are unavailable.
     */
    private List<DebugThread> collectAllThreadsFrames(ThreadReference eventThread) {
        VirtualMachine vm = virtualMachine;
        if (vm == null) {
            return List.of();
        }
        List<DebugThread> result = new ArrayList<>();
        for (ThreadReference t : vm.allThreads()) {
            // Skip low-level JVM threads (finalizer, GC, etc.)
            com.sun.jdi.ThreadGroupReference tg = t.threadGroup();
            if (tg != null && "system".equalsIgnoreCase(tg.name())) {
                continue;
            }
            try {
                List<String> frames = t.frames().stream()
                    .map(f -> {
                        Location loc = f.location();
                        return loc.declaringType().name() + "." + loc.method().name() + "() line: " + loc.lineNumber();
                    })
                    .toList();
                result.add(new DebugThread(t.name(), t.equals(eventThread), true, frames));
            } catch (IncompatibleThreadStateException e) {
                // Thread is running (SUSPEND_EVENT_THREAD — only event thread is suspended)
                result.add(new DebugThread(t.name(), false, false, List.of()));
            } catch (com.sun.jdi.ObjectCollectedException e) {
                // Thread terminated while we were listing them: skip it
            }
        }
        return result;
    }

    private void step(int depth) {
        VirtualMachine vm = virtualMachine;
        ThreadReference thread;
        synchronized (threadLock) {
            if (activeThread == null || activeRunning || vm == null) {
                return;
            }
            thread = activeThread;
            activeRunning = true;
        }
        // Disable step/resume buttons immediately (on EDT) to block double-clicking
        // while the step is in flight. showSuspended re-enables them when the thread stops.
        controller.setDebugStepActionsEnabled(false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    pendingSteps.remove(thread);
                    issueStep(vm, thread, depth);
                } catch (RuntimeException e) {
                    assert ExceptionUtil.printStackTrace(e);
                    synchronized (threadLock) {
                        activeRunning = false;
                    }
                    SwingUtil.invokeLater(() -> controller.setDebugStepActionsEnabled(true));
                }
                return null;
            }
        }.execute();
    }

    /**
     * Run to Line: resumes the stopped thread until it reaches the given source line (temporary breakpoint).
     * Other breakpoints still stop it on the way.
     */
    public void runToLine(String typeName, int lineNumber) {
        VirtualMachine vm;
        ThreadReference thread;
        synchronized (threadLock) {
            vm = virtualMachine;
            if (activeThread == null || activeRunning || vm == null) {
                return;
            }
            thread = activeThread;
            activeRunning = true;
        }
        controller.setDebugStepActionsEnabled(false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    pendingSteps.remove(thread);
                    deleteStepRequests(thread);
                    clearRunToLine();
                    for (ReferenceType referenceType : vm.classesByName(typeName)) {
                        for (Location location : referenceType.locationsOfLine(lineNumber)) {
                            BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(location);
                            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                            request.addThreadFilter(thread);
                            request.enable();
                            runToLineRequests.add(request);
                        }
                    }
                    if (runToLineRequests.isEmpty()) {
                        throw new IllegalStateException("No executable code on line " + lineNumber);
                    }
                    thread.resume();
                } catch (AbsentInformationException | RuntimeException e) {
                    clearRunToLine();
                    synchronized (threadLock) {
                        activeRunning = false;
                    }
                    String message = "Cannot run to line " + lineNumber + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    SwingUtil.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainFrame, message, "Run to Line", JOptionPane.WARNING_MESSAGE);
                        controller.setDebugStepActionsEnabled(true);
                    });
                }
                return null;
            }
        }.execute();
    }

    /** Resumes one thread until it completes a line step of the given depth. */
    private void issueStep(VirtualMachine vm, ThreadReference thread, int depth) {
        deleteStepRequests(thread);
        StepRequest request = vm.eventRequestManager().createStepRequest(thread, StepRequest.STEP_LINE, depth);
        request.addCountFilter(1);
        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        request.enable();
        // Only this thread runs; the other stopped threads stay stopped.
        thread.resume();
    }

    /**
     * Drop to Frame: pops the selected stack frame (and the frames above it) and steps into the method
     * again, so it stops on the first line of the method the selected frame was executing.
     */
    public void dropToFrame() {
        VirtualMachine vm;
        ThreadReference thread;
        int frameIndex;
        synchronized (threadLock) {
            vm = virtualMachine;
            if (activeThread == null || activeRunning || vm == null) {
                return;
            }
            thread = activeThread;
            frameIndex = selectedFrameIndex;
            activeRunning = true;
        }
        if (!vm.canPopFrames()) {
            synchronized (threadLock) {
                activeRunning = false;
            }
            JOptionPane.showMessageDialog(mainFrame, "The target VM does not support dropping stack frames.", "Drop to Frame", JOptionPane.WARNING_MESSAGE);
            return;
        }
        controller.setDebugStepActionsEnabled(false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    pendingSteps.remove(thread);
                    deleteStepRequests(thread);
                    thread.popFrames(thread.frame(frameIndex));
                    issueStep(vm, thread, StepRequest.STEP_INTO);
                } catch (IncompatibleThreadStateException | RuntimeException e) {
                    synchronized (threadLock) {
                        activeRunning = false;
                        selectedFrameIndex = 0;
                    }
                    String message = "Cannot drop to the selected frame: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    SwingUtil.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainFrame, message, "Drop to Frame", JOptionPane.WARNING_MESSAGE);
                        controller.setDebugStepActionsEnabled(true);
                    });
                }
                return null;
            }
        }.execute();
    }

    public void removeBreakpoint(String locationKey) {
        if (locationKey.startsWith(EXCEPTION_KEY_PREFIX)) {
            String typeName = locationKey.substring(EXCEPTION_KEY_PREFIX.length());
            if (exceptionBreakpoints.remove(typeName) != null) {
                disabledExceptions.remove(typeName);
                uninstallException(typeName);
                publishBreakpoints();
            }
            return;
        }
        // locationKey format: "className:lineNumber"
        int colon = locationKey.lastIndexOf(':');
        if (colon < 0) {
            return;
        }
        try {
            String typeName = locationKey.substring(0, colon);
            int lineNumber = Integer.parseInt(locationKey.substring(colon + 1));
            BreakpointKey key = new BreakpointKey(typeName, lineNumber);
            if (breakpoints.remove(key)) {
                disabledBreakpoints.remove(key);
                uninstallBreakpoint(key);
                publishBreakpoints();
            }
        } catch (NumberFormatException ignored) {
            // malformed key — ignore
        }
    }

    public void removeAllBreakpoints() {
        for (BreakpointKey key : new java.util.ArrayList<>(breakpoints)) {
            uninstallBreakpoint(key);
        }
        breakpoints.clear();
        disabledBreakpoints.clear();
        for (String typeName : new ArrayList<>(exceptionBreakpoints.keySet())) {
            uninstallException(typeName);
        }
        exceptionBreakpoints.clear();
        disabledExceptions.clear();
        publishBreakpoints();
    }

    /** "Skip All Breakpoints": keeps every breakpoint but stops none of them from suspending. */
    public void setSkipAllBreakpoints(boolean skip) {
        skipAllBreakpoints = skip;
        for (Map.Entry<String, List<ExceptionRequest>> entry : installedExceptions.entrySet()) {
            for (ExceptionRequest request : entry.getValue()) {
                try {
                    request.setEnabled(!skip && !disabledExceptions.contains(entry.getKey()));
                } catch (RuntimeException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
        }
        for (Map.Entry<BreakpointKey, List<BreakpointRequest>> entry : installedBreakpoints.entrySet()) {
            for (BreakpointRequest request : entry.getValue()) {
                try {
                    request.setEnabled(!skip && !disabledBreakpoints.contains(entry.getKey()));
                } catch (RuntimeException e) {
                    assert ExceptionUtil.printStackTrace(e);
                }
            }
        }
    }

    /** Enables or disables a breakpoint without removing it (the checkbox of the Breakpoints view). */
    public void setBreakpointEnabled(String locationKey, boolean enabled) {
        if (locationKey.startsWith(EXCEPTION_KEY_PREFIX)) {
            String typeName = locationKey.substring(EXCEPTION_KEY_PREFIX.length());
            if (!exceptionBreakpoints.containsKey(typeName)) {
                return;
            }
            if (enabled) {
                disabledExceptions.remove(typeName);
            } else {
                disabledExceptions.add(typeName);
            }
            for (ExceptionRequest request : installedExceptions.getOrDefault(typeName, List.of())) {
                request.setEnabled(enabled && !skipAllBreakpoints);
            }
            publishBreakpoints();
            return;
        }
        int colon = locationKey.lastIndexOf(':');
        if (colon < 0) {
            return;
        }
        try {
            BreakpointKey key = new BreakpointKey(locationKey.substring(0, colon), Integer.parseInt(locationKey.substring(colon + 1)));
            if (!breakpoints.contains(key)) {
                return;
            }
            if (enabled) {
                disabledBreakpoints.remove(key);
            } else {
                disabledBreakpoints.add(key);
            }
            List<BreakpointRequest> requests = installedBreakpoints.get(key);
            if (requests != null) {
                for (BreakpointRequest request : requests) {
                    request.setEnabled(enabled && !skipAllBreakpoints);
                }
            }
            publishBreakpoints();
        } catch (NumberFormatException ignored) {
            // malformed key — ignore
        }
    }

    /** Eclipse-style label: {@code Type [line: 28] - method(ArgType)}. */
    private String breakpointLabel(BreakpointKey breakpoint) {
        StringBuilder label = new StringBuilder(JdiValues.simpleName(breakpoint.typeName()))
            .append(" [line: ").append(breakpoint.lineNumber()).append(']');
        List<BreakpointRequest> requests = installedBreakpoints.get(breakpoint);
        if (requests != null && !requests.isEmpty()) {
            try {
                com.sun.jdi.Method method = requests.get(0).location().method();
                // Lambda bodies are compiled to lambda$name$N: Eclipse names the enclosing source method
                String name = method.name();
                java.util.regex.Matcher lambda = java.util.regex.Pattern.compile("lambda\\$(.+)\\$\\d+").matcher(name);
                if (lambda.matches()) {
                    for (com.sun.jdi.Method candidate : method.declaringType().methodsByName(lambda.group(1))) {
                        if (!candidate.isSynthetic()) {
                            method = candidate;
                            break;
                        }
                    }
                }
                label.append(" - ").append(method.name()).append('(');
                List<String> arguments = method.argumentTypeNames();
                for (int i = 0; i < arguments.size(); i++) {
                    label.append(i > 0 ? ", " : "").append(JdiValues.simpleName(arguments.get(i)));
                }
                label.append(')');
            } catch (RuntimeException e) {
                // location no longer valid: keep the short label
            }
        } else if (methodDescriptions.containsKey(breakpoint)) {
            label.append(" - ").append(methodDescriptions.get(breakpoint));
        }
        return label.toString();
    }

    private void installBreakpoint(BreakpointKey breakpoint) {
        VirtualMachine vm = virtualMachine;
        if (vm == null) {
            return;
        }

        for (ReferenceType referenceType : vm.classesByName(breakpoint.typeName())) {
            installBreakpoint(referenceType, breakpoint);
        }
    }

    private void installBreakpoints(ReferenceType referenceType) {
        for (BreakpointKey breakpoint : breakpoints) {
            if (breakpoint.typeName().equals(referenceType.name())) {
                installBreakpoint(referenceType, breakpoint);
            }
        }
    }

    private void installBreakpoint(ReferenceType referenceType, BreakpointKey breakpoint) {
        VirtualMachine vm = virtualMachine;
        if (vm == null) {
            return;
        }

        try {
            for (Location location : referenceType.locationsOfLine(breakpoint.lineNumber())) {
                BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(location);
                request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                request.setEnabled(!skipAllBreakpoints && !disabledBreakpoints.contains(breakpoint));
                installedBreakpoints.computeIfAbsent(breakpoint, _ -> new ArrayList<>()).add(request);
            }
        } catch (AbsentInformationException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }

    private void uninstallBreakpoint(BreakpointKey breakpoint) {
        VirtualMachine vm = virtualMachine;
        List<BreakpointRequest> requests = installedBreakpoints.remove(breakpoint);
        if (vm != null && requests != null) {
            vm.eventRequestManager().deleteEventRequests(new ArrayList<>(requests));
        }
    }

    private void deleteStepRequests(ThreadReference thread) {
        VirtualMachine vm = virtualMachine;
        if (vm == null) {
            return;
        }

        EventRequestManager manager = vm.eventRequestManager();
        List<StepRequest> requests = manager.stepRequests().stream()
            .filter(request -> Objects.equals(request.thread(), thread))
            .toList();
        if (!requests.isEmpty()) {
            manager.deleteEventRequests(new ArrayList<>(requests));
        }
    }

    private List<DebugVariableNode> readVariableNodes(ThreadReference thread, int frameIndex) {
        List<DebugVariableNode> nodes = new ArrayList<>();
        try {
            StackFrame frame = thread.frame(frameIndex);
            ObjectReference thisObject = frame.thisObject();
            if (thisObject != null) {
                nodes.add(JdiValues.toNode("this", thisObject));
            }
            // visibleVariables() is in declaration order; getValues() returns an unordered map.
            for (LocalVariable variable : frame.visibleVariables()) {
                nodes.add(JdiValues.toNode(variable.name(), frame.getValue(variable)));
            }
        } catch (AbsentInformationException e) {
            nodes.add(new DebugVariableNode("<debug info>", "Local variable table is not available"));
        } catch (IncompatibleThreadStateException | RuntimeException e) {
            String msg = e.getMessage();
            nodes.add(new DebugVariableNode("<unavailable>", msg == null ? e.getClass().getSimpleName() : msg));
        }
        return nodes;
    }

    private void disconnected() {
        connected = false;
        virtualMachine = null;
        resetThreads();
        installedBreakpoints.clear();
        installedExceptions.clear();
        SwingUtil.invokeLater(() -> {
            controller.setDebugStepActionsEnabled(false);
            controller.setDebugConnected(false);
            controller.clearDebugExecutionLine();
            controller.showDebugVariables(null, java.util.Collections.emptyList());
            controller.showDebugStackFrames(java.util.Collections.emptyList());
            publishBreakpoints();
        });
        publishExpressionsAsync();
    }

    private void publishBreakpoints() {
        persistBreakpoints();
        VirtualMachine vm = virtualMachine;
        List<String[]> exceptionRows = exceptionBreakpoints.values().stream()
            .sorted(Comparator.comparing(ExceptionBreakpoint::typeName))
            .map(exception -> new String[] { EXCEPTION_KEY_PREFIX + exception.typeName(),
                vm == null ? "Pending" : installedExceptions.containsKey(exception.typeName()) ? "Installed" : "Unresolved",
                exceptionLabel(exception), Boolean.toString(!disabledExceptions.contains(exception.typeName())) })
            .toList();
        List<String[]> lineRows = breakpoints.stream()
            .sorted(Comparator.comparing(BreakpointKey::typeName).thenComparingInt(BreakpointKey::lineNumber))
            .map(breakpoint -> {
                String status;
                if (vm == null) {
                    status = "Pending";
                } else if (installedBreakpoints.containsKey(breakpoint)) {
                    status = "Installed";
                } else {
                    status = "Unresolved";
                }
                return new String[] { breakpoint.typeName() + ":" + breakpoint.lineNumber(), status,
                    breakpointLabel(breakpoint), Boolean.toString(!disabledBreakpoints.contains(breakpoint)) };
            })
            .toList();
        List<String[]> rows = new ArrayList<>(exceptionRows);
        rows.addAll(lineRows);
        controller.showDebugBreakpoints(rows);
    }

    private static VirtualMachine attach(String host, int port) throws IOException, IllegalConnectorArgumentsException {
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
            .filter(c -> "com.sun.jdi.SocketAttach".equals(c.name()))
            .findFirst()
            .orElseThrow(() -> new IOException("JDI socket attach connector is not available in this runtime."));

        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue(host);
        arguments.get("port").setValue(Integer.toString(port));
        Connector.Argument timeoutArgument = arguments.get("timeout");
        if (timeoutArgument != null) {
            timeoutArgument.setValue(Integer.toString(CONNECT_TIMEOUT_MILLIS));
        }
        return connector.attach(arguments);
    }

    private final class AttachWorker extends AbstractSwingWorker<VirtualMachine, Void> {
        private final Component parent;
        private final String host;
        private final int port;

        private AttachWorker(API api, Component parent, String host, int port) {
            super(api, parent, "Attaching remote debugger ...");
            this.parent = parent;
            this.host = host;
            this.port = port;
        }

        @Override
        protected VirtualMachine doInBackground() throws Exception {
            setProgressPercentage(20);
            VirtualMachine vm = attach(host, port);
            setProgressPercentage(100);
            return vm;
        }

        @Override
        protected void done() {
            super.done();
            try {
                attached(get(), host, port);
            } catch (Exception e) {
                assert ExceptionUtil.printStackTrace(e);
                JOptionPane.showMessageDialog(parent, getRootMessage(e), "Remote Debug", JOptionPane.ERROR_MESSAGE);
            }
        }

        private String getRootMessage(Throwable throwable) {
            Throwable cause = throwable;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            String message = cause.getMessage();
            return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
        }
    }

    private record ExceptionBreakpoint(String typeName, boolean caught, boolean uncaught) {
    }

    private record BreakpointKey(String typeName, int lineNumber) {
    }
}
