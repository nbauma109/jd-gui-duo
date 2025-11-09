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
package org.jd.gui.util.maven.central.helper.model.responseheader.params;

import jakarta.json.bind.annotation.JsonbProperty;

public class Params {
    @JsonbProperty("q")
    private String query;
    private String core;
    private String defType;
    private String qf;
    private boolean spellcheck;
    private String indent;
    private String fl;
    private int start;
    @JsonbProperty("spellcheck.count")
    private int spellCheckCont;
    private String sort;
    private int rows;
    private String wt;
    private String version;

    public String getQuery() {
        return query;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public String getCore() {
        return core;
    }

    public void setCore(final String core) {
        this.core = core;
    }

    public String getDefType() {
        return defType;
    }

    public void setDefType(final String defType) {
        this.defType = defType;
    }

    public String getQf() {
        return qf;
    }

    public void setQf(final String qf) {
        this.qf = qf;
    }

    public boolean isSpellcheck() {
        return spellcheck;
    }

    public void setSpellcheck(final boolean spellcheck) {
        this.spellcheck = spellcheck;
    }

    public String getIndent() {
        return indent;
    }

    public void setIndent(final String indent) {
        this.indent = indent;
    }

    public String getFl() {
        return fl;
    }

    public void setFl(final String fl) {
        this.fl = fl;
    }

    public int getStart() {
        return start;
    }

    public void setStart(final int start) {
        this.start = start;
    }

    public int getSpellCheckCont() {
        return spellCheckCont;
    }

    public void setSpellCheckCont(final int spellCheckCont) {
        this.spellCheckCont = spellCheckCont;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(final String sort) {
        this.sort = sort;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(final int rows) {
        this.rows = rows;
    }

    public String getWt() {
        return wt;
    }

    public void setWt(final String wt) {
        this.wt = wt;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

}
