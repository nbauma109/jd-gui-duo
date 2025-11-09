/*******************************************************************************
 * Copyright (C) 2008-2025 Emmanuel Dupuy and other contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.jd.gui.util.maven.central.helper.model.responseheader;

import jakarta.json.bind.annotation.JsonbProperty;
import org.jd.gui.util.maven.central.helper.model.responseheader.params.Params;

public class ResponseHeader {
    private int status;
    @JsonbProperty("QTime")
    private long queryTime;
    private Params params;

    public int getStatus() {
        return status;
    }

    public void setStatus(final int status) {
        this.status = status;
    }

    public long getQueryTime() {
        return queryTime;
    }

    public void setQueryTime(final long queryTime) {
        this.queryTime = queryTime;
    }

    public Params getParams() {
        return params;
    }

    public void setParams(final Params params) {
        this.params = params;
    }

}
