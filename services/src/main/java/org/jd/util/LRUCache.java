package org.jd.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {

	private static final long serialVersionUID = 1L;

	private static final int DEFAULT_MAX_ENTRIES = 100;

	private int maxEntries;

	public LRUCache(int maxEntries) {
		super(maxEntries * 3 / 2, 0.7F, true);
		this.maxEntries = maxEntries;
	}

	public LRUCache() {
		this(DEFAULT_MAX_ENTRIES);
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		return size() > maxEntries;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		return prime * result + maxEntries;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj) || getClass() != obj.getClass()) {
			return false;
		}
		LRUCache<?, ?> other = (LRUCache<?, ?>) obj;
		return maxEntries == other.maxEntries;
	}
}