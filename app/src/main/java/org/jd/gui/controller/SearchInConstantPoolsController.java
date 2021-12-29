/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jd.gui.controller;

import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.api.model.Type;
import org.jd.gui.service.type.TypeFactoryService;
import org.jd.gui.spi.TypeFactory;
import org.jd.gui.util.function.TriConsumer;
import org.jd.gui.view.SearchInConstantPoolsView;
import org.jd.util.LRUCache;
import org.jdv1.gui.api.feature.IndexesChangeListener;
import org.jdv1.gui.model.container.DelegatingFilterContainer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.regex.Pattern;

import javax.swing.JFrame;

public class SearchInConstantPoolsController implements IndexesChangeListener {
	protected static final int CACHE_MAX_ENTRIES = 5 * 20 * 9;

	private final API api;
	private final ScheduledExecutorService executor;

	@SuppressWarnings("rawtypes")
	private final SearchInConstantPoolsView searchInConstantPoolsView;
	@SuppressWarnings("rawtypes")
	private final Map<String, Map<String, Collection>> cache;
	private final Set<DelegatingFilterContainer> delegatingFilterContainers = new HashSet<>();
	private Collection<Future<Indexes>> collectionOfFutureIndexes;
	private Consumer<URI> openCallback;
	private long indexesHashCode;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public SearchInConstantPoolsController(API api, ScheduledExecutorService executor, JFrame mainFrame) {
		this.api = api;
		this.executor = executor;
		// Create UI
		ObjIntConsumer<String> changedPatternCallback = this::updateTree;
		TriConsumer<URI, String, Integer> selectedTypeCallback = this::onTypeSelected;
		this.searchInConstantPoolsView = new SearchInConstantPoolsView(api, mainFrame, changedPatternCallback, selectedTypeCallback);
		// Create result cache
		this.cache = new LRUCache<>(CACHE_MAX_ENTRIES);
	}

	public void show(Collection<Future<Indexes>> collectionOfFutureIndexes, Consumer<URI> openCallback) {
		// Init attributes
		this.collectionOfFutureIndexes = collectionOfFutureIndexes;
		this.openCallback = openCallback;
		// Refresh view
		long hashCode = collectionOfFutureIndexes.hashCode();
		if (hashCode != indexesHashCode) {
			// List of indexes has changed
			updateTree(searchInConstantPoolsView.getPattern(), searchInConstantPoolsView.getFlags());
			indexesHashCode = hashCode;
		}
		// Show
		searchInConstantPoolsView.show();
	}

	@SuppressWarnings("unchecked")
	protected void updateTree(String pattern, int flags) {
		delegatingFilterContainers.clear();

		executor.execute(() -> {
			// Waiting the end of indexation...
			searchInConstantPoolsView.showWaitCursor();

			int matchingTypeCount = 0;
			int patternLength = pattern.length();

			if (patternLength > 0) {
				try {
					for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
						if (futureIndexes.isDone()) {
							Indexes indexes = futureIndexes.get();
							Set<Container.Entry> matchingEntries = new HashSet<>();
							// Find matched entries
							filter(indexes, pattern, flags, matchingEntries);

							if (!matchingEntries.isEmpty()) {
								// Search root container with first matching entry
								Container.Entry parentEntry = matchingEntries.iterator().next();
								Container container = null;

								while (parentEntry.getContainer().getRoot() != null) {
									container = parentEntry.getContainer();
									parentEntry = container.getRoot().getParent();
								}

								// TODO In a future release, display matching strings, types, inner-types,
								// fields and methods, not only matching files
								matchingEntries = getOuterEntries(matchingEntries);

								matchingTypeCount += matchingEntries.size();

								// Create a filtered container
								delegatingFilterContainers.add(new DelegatingFilterContainer(container, matchingEntries));
							}
						}
					}
				} catch (InterruptedException e) {
					assert ExceptionUtil.printStackTrace(e);
					// Restore interrupted state...
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					assert ExceptionUtil.printStackTrace(e);
				}
			}

			final int count = matchingTypeCount;

			searchInConstantPoolsView.hideWaitCursor();
			searchInConstantPoolsView.updateTree(delegatingFilterContainers, count);
		});
	}

	protected Set<Container.Entry> getOuterEntries(Set<Container.Entry> matchingEntries) {
		Map<Container.Entry, Container.Entry> innerTypeEntryToOuterTypeEntry = new HashMap<>();
		Set<Container.Entry> matchingOuterEntriesSet = new HashSet<>();

		TypeFactory typeFactory;
		for (Container.Entry entry : matchingEntries) {
			typeFactory = TypeFactoryService.getInstance().get(entry);

			if (typeFactory != null) {
				Type type = typeFactory.make(api, entry, null);

				if (type != null && type.getOuterName() != null) {
					Container.Entry outerTypeEntry = innerTypeEntryToOuterTypeEntry.get(entry);

					if (outerTypeEntry == null) {
						Map<String, Container.Entry> typeNameToEntry = new HashMap<>();
						Map<String, String> innerTypeNameToOuterTypeName = new HashMap<>();

						// Populate "typeNameToEntry" and "innerTypeNameToOuterTypeName"
						for (Container.Entry e : entry.getParent().getChildren().values()) {
							typeFactory = TypeFactoryService.getInstance().get(e);

							if (typeFactory != null) {
								type = typeFactory.make(api, e, null);

								if (type != null) {
									typeNameToEntry.put(type.getName(), e);
									if (type.getOuterName() != null) {
										innerTypeNameToOuterTypeName.put(type.getName(), type.getOuterName());
									}
								}
							}
						}

						Container.Entry innerTypeEntry;
						// Search outer type entries and populate "innerTypeEntryToOuterTypeEntry"
						for (Map.Entry<String, String> e : innerTypeNameToOuterTypeName.entrySet()) {
							innerTypeEntry = typeNameToEntry.get(e.getKey());

							if (innerTypeEntry != null) {
								String outerTypeName = e.getValue();

								String typeName;
								for (;;) {
									typeName = innerTypeNameToOuterTypeName.get(outerTypeName);
									if (typeName == null) {
										break;
									}
									outerTypeName = typeName;
								}

								outerTypeEntry = typeNameToEntry.get(outerTypeName);

								if (outerTypeEntry != null) {
									innerTypeEntryToOuterTypeEntry.put(innerTypeEntry, outerTypeEntry);
								}
							}
						}

						// Get outer type entry
						outerTypeEntry = innerTypeEntryToOuterTypeEntry.get(entry);

						if (outerTypeEntry == null) {
							outerTypeEntry = entry;
						}
					}

					matchingOuterEntriesSet.add(outerTypeEntry);
				} else {
					matchingOuterEntriesSet.add(entry);
				}
			}
		}

		return matchingOuterEntriesSet;
	}

	protected void filter(Indexes indexes, String pattern, int flags, Set<Container.Entry> matchingEntries) {
		boolean declarations = (flags & SearchInConstantPoolsView.SEARCH_DECLARATION) != 0;
		boolean references = (flags & SearchInConstantPoolsView.SEARCH_REFERENCE) != 0;

		if ((flags & SearchInConstantPoolsView.SEARCH_TYPE) != 0) {
			if (declarations) {
				match(indexes, "typeDeclarations", pattern, SearchInConstantPoolsController::matchTypeEntriesWithChar, SearchInConstantPoolsController::matchTypeEntriesWithString,
				        matchingEntries);
			}
			if (references) {
				match(indexes, "typeReferences", pattern, SearchInConstantPoolsController::matchTypeEntriesWithChar, SearchInConstantPoolsController::matchTypeEntriesWithString,
				        matchingEntries);
			}
		}

		if ((flags & SearchInConstantPoolsView.SEARCH_CONSTRUCTOR) != 0) {
			if (declarations) {
				match(indexes, "constructorDeclarations", pattern, SearchInConstantPoolsController::matchTypeEntriesWithChar,
				        SearchInConstantPoolsController::matchTypeEntriesWithString, matchingEntries);
			}
			if (references) {
				match(indexes, "constructorReferences", pattern, SearchInConstantPoolsController::matchTypeEntriesWithChar,
				        SearchInConstantPoolsController::matchTypeEntriesWithString, matchingEntries);
			}
		}

		if ((flags & SearchInConstantPoolsView.SEARCH_METHOD) != 0) {
			if (declarations) {
				match(indexes, "methodDeclarations", pattern, SearchInConstantPoolsController::matchWithChar, SearchInConstantPoolsController::matchWithString, matchingEntries);
			}
			if (references) {
				match(indexes, "methodReferences", pattern, SearchInConstantPoolsController::matchWithChar, SearchInConstantPoolsController::matchWithString, matchingEntries);
			}
		}

		if ((flags & SearchInConstantPoolsView.SEARCH_FIELD) != 0) {
			if (declarations) {
				match(indexes, "fieldDeclarations", pattern, SearchInConstantPoolsController::matchWithChar, SearchInConstantPoolsController::matchWithString, matchingEntries);
			}
			if (references) {
				match(indexes, "fieldReferences", pattern, SearchInConstantPoolsController::matchWithChar, SearchInConstantPoolsController::matchWithString, matchingEntries);
			}
		}

		if ((flags & SearchInConstantPoolsView.SEARCH_STRING) != 0 && (declarations || references)) {
			match(indexes, "strings", pattern, SearchInConstantPoolsController::matchWithChar, SearchInConstantPoolsController::matchWithString, matchingEntries);
		}

		if ((flags & SearchInConstantPoolsView.SEARCH_MODULE) != 0) {
			if (declarations) {
				match(indexes, "javaModuleDeclarations", pattern, SearchInConstantPoolsController::matchWithChar, SearchInConstantPoolsController::matchWithString,
				        matchingEntries);
			}
			if (references) {
				match(indexes, "javaModuleReferences", pattern, SearchInConstantPoolsController::matchWithChar, SearchInConstantPoolsController::matchWithString, matchingEntries);
			}
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void match(Indexes indexes, String indexName, String pattern, BiFunction<Character, Map<String, Collection>, Map<String, Collection>> matchWithCharFunction,
	        BiFunction<String, Map<String, Collection>, Map<String, Collection>> matchWithStringFunction, Set<Container.Entry> matchingEntries) {
		int patternLength = pattern.length();

		if (patternLength > 0) {
			String key = indexes.hashCode() + "***" + indexName + "***" + pattern;
			Map<String, Collection> matchedEntries = cache.computeIfAbsent(key, k -> {
				Map<String, Collection> index = indexes.getIndex(indexName);

				if (index != null) {
					if (patternLength == 1) {
						return matchWithCharFunction.apply(pattern.charAt(0), index);
					}
					String lastKey = key.substring(0, key.length() - 1);
					Map<String, Collection> lastMatchedTypes = cache.get(lastKey);
					if (lastMatchedTypes != null) {
						return matchWithStringFunction.apply(pattern, lastMatchedTypes);
					}
					return matchWithStringFunction.apply(pattern, index);
				}
				return null;
			});

			if (matchedEntries != null) {
				for (Collection<Container.Entry> entries : matchedEntries.values()) {
					matchingEntries.addAll(entries);
				}
			}
		}
	}

	@SuppressWarnings("rawtypes")
	protected static Map<String, Collection> matchTypeEntriesWithChar(char c, Map<String, Collection> index) {
		if (c == '*' || c == '?') {
			return index;
		}
		Map<String, Collection> map = new HashMap<>();

		String typeName;
		int lastPackageSeparatorIndex;
		int lastTypeNameSeparatorIndex;
		int lastIndex;
		for (Map.Entry<String, Collection> entry : index.entrySet()) {
			typeName = entry.getKey();
			// Search last package separator
			lastPackageSeparatorIndex = typeName.lastIndexOf('/') + 1;
			lastTypeNameSeparatorIndex = typeName.lastIndexOf('$') + 1;
			lastIndex = Math.max(lastPackageSeparatorIndex, lastTypeNameSeparatorIndex);

			if (lastIndex < typeName.length() && typeName.charAt(lastIndex) == c) {
				map.put(typeName, entry.getValue());
			}
		}

		return map;
	}

	@SuppressWarnings("rawtypes")
	protected static Map<String, Collection> matchTypeEntriesWithString(String pattern, Map<String, Collection> index) {
		Pattern p = createPattern(pattern);
		Map<String, Collection> map = new HashMap<>();

		String typeName;
		int lastPackageSeparatorIndex;
		int lastTypeNameSeparatorIndex;
		int lastIndex;
		for (Map.Entry<String, Collection> entry : index.entrySet()) {
			typeName = entry.getKey();
			// Search last package separator
			lastPackageSeparatorIndex = typeName.lastIndexOf('/') + 1;
			lastTypeNameSeparatorIndex = typeName.lastIndexOf('$') + 1;
			lastIndex = Math.max(lastPackageSeparatorIndex, lastTypeNameSeparatorIndex);

			if (p.matcher(typeName.substring(lastIndex)).matches()) {
				map.put(typeName, entry.getValue());
			}
		}

		return map;
	}

	@SuppressWarnings("rawtypes")
	protected static Map<String, Collection> matchWithChar(char c, Map<String, Collection> index) {
		if (c == '*' || c == '?') {
			return index;
		}
		Map<String, Collection> map = new HashMap<>();

		String key;
		for (Map.Entry<String, Collection> entry : index.entrySet()) {
			key = entry.getKey();
			if (!key.isEmpty() && key.charAt(0) == c) {
				map.put(key, entry.getValue());
			}
		}

		return map;
	}

	@SuppressWarnings("rawtypes")
	protected static Map<String, Collection> matchWithString(String pattern, Map<String, Collection> index) {
		Pattern p = createPattern(pattern);
		Map<String, Collection> map = new HashMap<>();

		String key;
		for (Map.Entry<String, Collection> entry : index.entrySet()) {
			key = entry.getKey();
			if (p.matcher(key).matches()) {
				map.put(key, entry.getValue());
			}
		}

		return map;
	}

	/**
	 * Create a simple regular expression
	 *
	 * Rules: '*' matchTypeEntries 0 ou N characters '?' matchTypeEntries 1
	 * character
	 */
	protected static Pattern createPattern(String pattern) {
		int patternLength = pattern.length();
		StringBuilder sbPattern = new StringBuilder(patternLength * 2);

		char c;
		for (int i = 0; i < patternLength; i++) {
			c = pattern.charAt(i);

			switch (c) {
			case '*':
				sbPattern.append(".*");
				break;
			case '?':
				sbPattern.append('.');
				break;
			case '.':
				sbPattern.append("\\.");
				break;
			default:
				sbPattern.append(c);
				break;
			}
		}

		sbPattern.append(".*");

		return Pattern.compile(sbPattern.toString());
	}

	protected void onTypeSelected(URI uri, String pattern, int flags) {
		// Open the single entry uri
		Container.Entry entry = null;

		for (DelegatingFilterContainer container : delegatingFilterContainers) {
			entry = container.getEntry(uri);
			if (entry != null) {
				break;
			}
		}

		if (entry != null) {
			StringBuilder sbPattern = new StringBuilder(200 + pattern.length());

			sbPattern.append("highlightPattern=");
			sbPattern.append(pattern);
			sbPattern.append("&highlightFlags=");

			if ((flags & SearchInConstantPoolsView.SEARCH_DECLARATION) != 0) {
				sbPattern.append('d');
			}
			if ((flags & SearchInConstantPoolsView.SEARCH_REFERENCE) != 0) {
				sbPattern.append('r');
			}
			if ((flags & SearchInConstantPoolsView.SEARCH_TYPE) != 0) {
				sbPattern.append('t');
			}
			if ((flags & SearchInConstantPoolsView.SEARCH_CONSTRUCTOR) != 0) {
				sbPattern.append('c');
			}
			if ((flags & SearchInConstantPoolsView.SEARCH_METHOD) != 0) {
				sbPattern.append('m');
			}
			if ((flags & SearchInConstantPoolsView.SEARCH_FIELD) != 0) {
				sbPattern.append('f');
			}
			if ((flags & SearchInConstantPoolsView.SEARCH_STRING) != 0) {
				sbPattern.append('s');
			}
			if ((flags & SearchInConstantPoolsView.SEARCH_MODULE) != 0) {
				sbPattern.append('M');
			}

			// TODO In a future release, add 'highlightScope' to display search results in
			// correct type and inner-type
			// def type = TypeFactoryService.instance.get(entry)?.make(api, entry, null)
			// if (type) {
			// sbPattern.append('&highlightScope=')
			// sbPattern.append(type.name)
			// def query = sbPattern.toString()
			// def outerPath = UriUtil.getOuterPath(collectionOfFutureIndexes, entry, type)
			// openClosure(new URI(entry.uri.scheme, entry.uri.host, outerPath, query,
			// null))
			// } else {
			String query = sbPattern.toString();
			URI u = entry.getUri();

			try {
				openCallback.accept(new URI(u.getScheme(), u.getHost(), u.getPath(), query, null));
			} catch (URISyntaxException e) {
				assert ExceptionUtil.printStackTrace(e);
			}
			// }
		}
	}

	/** --- IndexesChangeListener --- */
	@Override
	public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
		if (searchInConstantPoolsView.isVisible()) {
			// Update the list of containers
			this.collectionOfFutureIndexes = collectionOfFutureIndexes;
			// And refresh
			updateTree(searchInConstantPoolsView.getPattern(), searchInConstantPoolsView.getFlags());
		}
	}
}
