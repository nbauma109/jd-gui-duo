/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.net;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class InterProcessCommunicationUtil {

    protected static final int PORT = 20156;

    private InterProcessCommunicationUtil() {
    }

    /**
     * When an additional instance of JD-GUI launched, for example when the user clicks on
     * another file, instead of opening the new file in the new JD-GUI instance, the arguments,
     * which hold the file to be opened, will be transmitted to the existing instance,
     * allowing the new instance to shutdown.
     *
     * @param consumer
     * @throws IOException
     */
    public static void listen(final Consumer<String[]> consumer) throws IOException {
        @SuppressWarnings("all")
        // Resource leak : The socket cannot be closed until the application is shutdown
        final ServerSocket listener = new ServerSocket(PORT);
        new Thread(() -> {
            synchronized (listener) {
                while (true) {
                    try (Socket socket = listener.accept();
                         ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
                        // Receive args from another JD-GUI instance
                        String[] args = (String[])ois.readObject();
                        consumer.accept(args);
                    } catch (IOException|ClassNotFoundException e) {
                        assert ExceptionUtil.printStackTrace(e);
                    }
                }
            }
        }).start();
    }

    public static void send(String[] args) {
        try (Socket socket = new Socket(InetAddress.getLocalHost(), PORT);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            // Send args to the main JD-GUI instance
            oos.writeObject(args);
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
