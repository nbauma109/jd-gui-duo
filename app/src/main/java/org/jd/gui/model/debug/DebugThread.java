/*
 * Copyright 2026 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 */

package org.jd.gui.model.debug;

import java.util.List;

/**
 * Snapshot of one JVM thread taken at a debug event.
 *
 * @param name          thread name (e.g. "worker-1"); not unique
 * @param id            unique id of the JVM thread: identifies the thread when several share a name
 * @param isEventThread true when this is the thread that triggered the breakpoint/step event
 * @param isSuspended   true when this thread is actually suspended (frames are available);
 *                      false when the thread is still running (SUSPEND_EVENT_THREAD policy)
 * @param frames        ordered list of frame descriptions, index 0 = top (innermost) frame;
 *                      empty when isSuspended is false
 */
public record DebugThread(String name, long id, boolean isEventThread, boolean isSuspended, List<String> frames) {
}
