/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.util.net;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public final class InterProcessCommunicationUtil {

    private static final int PORT = 20156;

    private InterProcessCommunicationUtil() {
    }

    /**
     * When an additional instance of JD-GUI is launched, for example when the user clicks on
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
                         ObjectInputStream is = new ObjectInputStream(socket.getInputStream())) {
                        // Receive args from another JD-GUI instance
                        int length = is.readInt();
                        String[] args = new String[length];
                        for (int i = 0; i < args.length; i++) {
                            args[i] = is.readUTF();
                        }
                        consumer.accept(args);
                    } catch (IOException e) {
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
            oos.writeInt(args.length);
            for (String arg : args) {
                oos.writeUTF(arg);
            }
        } catch (IOException e) {
            assert ExceptionUtil.printStackTrace(e);
        }
    }
}
