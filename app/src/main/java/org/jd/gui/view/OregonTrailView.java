/*
 * © 2021-2025 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.view;

import org.jd.gui.game.oregon.Keyboard;
import org.jd.gui.game.oregon.NoInputException;
import org.jd.gui.game.oregon.OregonBasicGame;
import org.jd.gui.util.ImageUtil;
import org.jd.gui.util.swing.SwingUtil;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Plays the Oregon Trail text game (originally bundled with PlantUML) in a green-on-black terminal dialog.
 */
public class OregonTrailView {

    private static final String PROMPT_COLOR = "#33ff33";
    private static final String END_OF_GAME = "<b>--- End of game. Use Restart to play again. ---</b>";

    private JDialog dialog;
    private JEditorPane screenPane;
    private JTextField inputField;
    private GameSession session;

    public OregonTrailView(JFrame mainFrame) {
        SwingUtil.invokeLater(() -> {
            dialog = new JDialog(mainFrame, "The Oregon Trail", false);
            ImageUtil.addJDIconsToFrame(dialog);

            screenPane = new JEditorPane();
            screenPane.setContentType("text/html");
            screenPane.setEditable(false);
            screenPane.setBackground(Color.BLACK);
            JScrollPane scrollPane = new JScrollPane(screenPane);

            inputField = new JTextField();
            inputField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            inputField.setBackground(Color.BLACK);
            inputField.setForeground(Color.decode(PROMPT_COLOR));
            inputField.setCaretColor(Color.decode(PROMPT_COLOR));
            inputField.addActionListener(e -> submitInput());

            JButton restartButton = new JButton("Restart");
            restartButton.setFocusable(false);
            restartButton.addActionListener(e -> restart());

            JPanel bottom = new JPanel(new BorderLayout(5, 0));
            bottom.setBorder(new EmptyBorder(5, 5, 5, 5));
            bottom.add(inputField, BorderLayout.CENTER);
            bottom.add(restartButton, BorderLayout.EAST);

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(scrollPane, BorderLayout.CENTER);
            panel.add(bottom, BorderLayout.SOUTH);
            dialog.add(panel);

            dialog.getRootPane().registerKeyboardAction(
                    new AbstractAction() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void actionPerformed(ActionEvent e) {
                            dialog.setVisible(false);
                        }
                    },
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    stopSession();
                }
            });
            dialog.setSize(760, 560);
        });
    }

    public void show() {
        SwingUtil.invokeLater(() -> {
            if (dialog.isVisible()) {
                dialog.toFront();
                return;
            }
            dialog.setLocationRelativeTo(dialog.getOwner());
            dialog.setVisible(true);
            if (session == null) {
                restart();
            }
            inputField.requestFocusInWindow();
        });
    }

    private void restart() {
        stopSession();
        render(new ArrayList<>());
        session = new GameSession();
        session.start();
        inputField.requestFocusInWindow();
    }

    private void stopSession() {
        if (session != null) {
            session.stop();
            session = null;
        }
    }

    private void submitInput() {
        String text = inputField.getText().trim();
        inputField.setText("");
        if (session != null && !text.isEmpty()) {
            session.keyboard.send(text);
        }
    }

    private void render(List<String> lines) {
        StringBuilder html = new StringBuilder("<html><body style=\"background-color:black;color:" + PROMPT_COLOR
                + ";font-family:monospaced;font-size:14pt\"><pre>");
        for (String line : lines) {
            html.append(toHtml(line)).append('\n');
        }
        html.append("</pre></body></html>");
        screenPane.setText(html.toString());
        screenPane.setCaretPosition(screenPane.getDocument().getLength());
    }

    /** Escapes the line but keeps the &lt;b&gt; and &lt;i&gt; tags the game uses for emphasis. */
    static String toHtml(String line) {
        return line.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replaceAll("&lt;(/?[bi])>", "<$1>");
    }

    private class GameSession {
        private final BlockingKeyboard keyboard = new BlockingKeyboard();
        private final OregonBasicGame game = new OregonBasicGame();
        private final Thread thread = new Thread(this::play, "oregon-trail");

        void start() {
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            keyboard.close();
            thread.interrupt();
        }

        private void play() {
            try {
                game.run(keyboard);
            } catch (NoInputException e) {
                // game over, or the dialog was closed
            }
            if (!keyboard.closed) {
                List<String> lines = new ArrayList<>(game.getScreen().getLines());
                lines.add(END_OF_GAME);
                SwingUtilities.invokeLater(() -> render(lines));
            }
        }

        /** Blocks the game thread until the player types something, refreshing the screen first. */
        private class BlockingKeyboard implements Keyboard {
            private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            private volatile boolean closed;

            @Override
            public String input() throws NoInputException {
                List<String> lines = new ArrayList<>(game.getScreen().getLines());
                SwingUtilities.invokeLater(() -> {
                    if (!closed) {
                        render(lines);
                    }
                });
                try {
                    String s = queue.take();
                    if (closed) {
                        throw new NoInputException();
                    }
                    return s;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new NoInputException();
                }
            }

            @Override
            public boolean hasMore() {
                return !closed;
            }

            void send(String s) {
                queue.offer(s);
            }

            void close() {
                closed = true;
                queue.offer("");
            }
        }
    }
}
