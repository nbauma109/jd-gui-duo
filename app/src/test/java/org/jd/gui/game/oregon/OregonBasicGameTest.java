/*
 * © 2021-2025 Nicolas Baumann (@nbauma109)
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui.game.oregon;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OregonBasicGameTest {

    @Test
    void gameStartsAndStopsWhenInputIsExhausted() {
        Deque<String> inputs = new ArrayDeque<>(List.of("2", "1", "1", "1"));
        Keyboard keyboard = new Keyboard() {
            @Override
            public String input() throws NoInputException {
                if (inputs.isEmpty()) {
                    throw new NoInputException();
                }
                return inputs.poll();
            }

            @Override
            public boolean hasMore() {
                return !inputs.isEmpty();
            }
        };
        OregonBasicGame game = new OregonBasicGame();
        try {
            game.run(keyboard);
        } catch (NoInputException e) {
            // expected: the scripted inputs ran out
        }
        assertFalse(game.getScreen().getLines().isEmpty());
        assertTrue(game.getScreen().getLines().stream().anyMatch(l -> l.contains("? ")));
    }
}
