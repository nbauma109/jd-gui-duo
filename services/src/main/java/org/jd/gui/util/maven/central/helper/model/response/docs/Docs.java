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
package org.jd.gui.util.maven.central.helper.model.response.docs;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Docs {
    private String id;
    private String g;
    private String a;
    private String v;
    private String latestVersion;
    private String repositoryId;
    private String p;
    private long timestamp;
    private int versionCount;
    private List<String> text;
    private List<String> ec;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(final String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    public int getVersionCount() {
        return versionCount;
    }

    public void setVersionCount(final int versionCount) {
        this.versionCount = versionCount;
    }

    public List<String> getText() {
        return text;
    }

    public void setText(final List<String> text) {
        this.text = text;
    }

    public List<String> getEc() {
        return ec;
    }

    public void setEc(final List<String> ec) {
        this.ec = ec;
    }

    public LocalDate getDate() {
    	return LocalDate.ofEpochDay(TimeUnit.MILLISECONDS.toDays(timestamp));
    }

    public String getG() {
		return g;
	}

	public void setG(String g) {
		this.g = g;
	}

	public String getA() {
		return a;
	}

	public void setA(String a) {
		this.a = a;
	}

	public String getV() {
		return v;
	}

	public void setV(String v) {
		this.v = v;
	}

	public String getLatestVersion() {
		return latestVersion;
	}

	public void setLatestVersion(String latestVersion) {
		this.latestVersion = latestVersion;
	}

	public String getP() {
		return p;
	}

	public void setP(String p) {
		this.p = p;
	}

	@Override
    public String toString() {
        return String.join(":", g, a, v);
    }

}
