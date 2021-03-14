/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jdv1.gui.api.feature;


import java.util.Collection;
import java.util.concurrent.Future;

import org.jd.gui.api.model.Indexes;

public interface IndexesChangeListener {
    void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes);
}
