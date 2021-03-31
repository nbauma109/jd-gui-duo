/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */
package org.jdv1.gui.view.component;

import java.awt.Point;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fife.ui.rsyntaxtextarea.DocumentRange;
import org.fife.ui.rtextarea.Marker;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.ExceptionUtil;
import org.jd.gui.api.API;
import org.jd.gui.api.feature.FocusedTypeGettable;
import org.jd.gui.api.feature.UriGettable;
import org.jd.gui.api.feature.UriOpenable;
import org.jd.gui.api.model.Container;
import org.jd.gui.api.model.Indexes;
import org.jd.gui.api.model.Type;
import org.jd.gui.util.matcher.DescriptorMatcher;
import org.jd.gui.util.parser.jdt.ASTParserFactory;
import org.jd.gui.util.parser.jdt.core.DeclarationData;
import org.jd.gui.util.parser.jdt.core.HyperlinkData;
import org.jd.gui.util.parser.jdt.core.HyperlinkReferenceData;
import org.jd.gui.util.parser.jdt.core.ReferenceData;
import org.jd.gui.util.parser.jdt.core.StringData;
import org.jd.gui.view.component.CustomLineNumbersPage;
import org.jd.gui.view.component.ReferenceListener;
import org.jdv1.gui.api.feature.IndexesChangeListener;
import org.jdv1.gui.util.index.IndexesUtil;

public abstract class TypePage extends CustomLineNumbersPage
		implements UriGettable, UriOpenable, IndexesChangeListener, FocusedTypeGettable {

	private static final long serialVersionUID = 1L;

	protected static HashMap<String, ReferenceData> referencesCache = new HashMap<>();

	protected API api;
	protected Container.Entry entry;
	protected Collection<Future<Indexes>> collectionOfFutureIndexes = Collections.emptyList();

	protected ReferenceListener listener;

	public TypePage(API api, Container.Entry entry) {
		// Init attributes
		this.api = api;
		this.entry = entry;
		this.listener = new ReferenceListener(entry);
		this.listener.setHyperlinks(hyperlinks);
	}

	@Override
	protected boolean isHyperlinkEnabled(HyperlinkData hyperlinkData) {
		return ((HyperlinkReferenceData) hyperlinkData).getReference().isEnabled();
	}

	@Override
	protected void openHyperlink(int x, int y, HyperlinkData hyperlinkData) {
		HyperlinkReferenceData hyperlinkReferenceData = (HyperlinkReferenceData) hyperlinkData;

		if (hyperlinkReferenceData.getReference().isEnabled()) {
			try {
				// Save current position in history
				Point location = textArea.getLocationOnScreen();
				int offset = textArea.viewToModel(new Point(x - location.x, y - location.y));
				URI uri = entry.getUri();
				api.addURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), "position=" + offset, null));

				// Open link
				ReferenceData reference = hyperlinkReferenceData.getReference();
				String typeName = reference.getTypeName();
				List<Container.Entry> entries = IndexesUtil.findInternalTypeName(collectionOfFutureIndexes, typeName);
				String fragment = typeName;

				if (reference.getName() != null) {
					fragment += '-' + reference.getName();
				}
				if (reference.getDescriptor() != null) {
					fragment += '-' + reference.getDescriptor();
				}

				if (entries.contains(entry)) {
					api.openURI(new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), fragment));
				} else {
					String rootUri = entry.getContainer().getRoot().getUri().toString();
					ArrayList<Container.Entry> sameContainerEntries = new ArrayList<>();

					for (Container.Entry entry : entries) {
						if (entry.getUri().toString().startsWith(rootUri)) {
							sameContainerEntries.add(entry);
						}
					}

					if (sameContainerEntries.size() > 0) {
						api.openURI(x, y, sameContainerEntries, null, fragment);
					} else if (entries.size() > 0) {
						api.openURI(x, y, entries, null, fragment);
					}
				}
			} catch (URISyntaxException e) {
				assert ExceptionUtil.printStackTrace(e);
			}
		}
	}

	// --- UriGettable --- //
	@Override
	public URI getUri() {
		return entry.getUri();
	}

	// --- UriOpenable --- //
	/**
	 * @param uri for URI format, @see jd.gui.api.feature.UriOpenable
	 */
	@Override
	public boolean openUri(URI uri) {
		ArrayList<DocumentRange> ranges = new ArrayList<>();
		String fragment = uri.getFragment();
		String query = uri.getQuery();

		Marker.clearMarkAllHighlights(textArea);

		if (fragment != null) {
			matchFragmentAndAddDocumentRange(fragment, listener.getDeclarations(), ranges);
		}

		if (query != null) {
			Map<String, String> parameters = parseQuery(query);

			if (parameters.containsKey("lineNumber")) {
				String lineNumber = parameters.get("lineNumber");

				try {
					goToLineNumber(Integer.parseInt(lineNumber));
					return true;
				} catch (NumberFormatException e) {
					assert ExceptionUtil.printStackTrace(e);
				}
			} else if (parameters.containsKey("position")) {
				String position = parameters.get("position");

				try {
					int pos = Integer.parseInt(position);
					if (textArea.getDocument().getLength() > pos) {
						ranges.add(new DocumentRange(pos, pos));
					}
				} catch (NumberFormatException e) {
					assert ExceptionUtil.printStackTrace(e);
				}
			} else {
				matchQueryAndAddDocumentRange(parameters, listener.getDeclarations(), hyperlinks, listener.getStrings(),
						ranges);
			}
		}

		if ((ranges != null) && !ranges.isEmpty()) {
			textArea.setMarkAllHighlightColor(SELECT_HIGHLIGHT_COLOR);
			Marker.markAll(textArea, ranges);
			ranges.sort(null);
			setCaretPositionAndCenter(ranges.get(0));
		}

		return true;
	}

	public static void matchFragmentAndAddDocumentRange(String fragment, Map<String, DeclarationData> declarations,
			List<DocumentRange> ranges) {
		if ((fragment.indexOf('?') != -1) || (fragment.indexOf('*') != -1)) {
			// Unknown type and/or descriptor ==> Select all and scroll to the first one
			int lastDash = fragment.lastIndexOf('-');

			if (lastDash == -1) {
				// Search types
				String slashAndTypeName = fragment.substring(1);
				String typeName = fragment.substring(2);

				for (Map.Entry<String, DeclarationData> entry : declarations.entrySet()) {
					if (entry.getKey().endsWith(slashAndTypeName) || entry.getKey().equals(typeName)) {
						ranges.add(new DocumentRange(entry.getValue().getStartPosition(),
								entry.getValue().getEndPosition()));
					}
				}
			} else {
				String prefix = fragment.substring(0, lastDash + 1);
				String suffix = fragment.substring(lastDash + 1);
				BiFunction<String, String, Boolean> matchDescriptors;

				if (suffix.charAt(0) == '(') {
					matchDescriptors = DescriptorMatcher::matchMethodDescriptors;
				} else {
					matchDescriptors = DescriptorMatcher::matchFieldDescriptors;
				}

				if (fragment.charAt(0) == '*') {
					// Unknown type
					String slashAndTypeNameAndName = prefix.substring(1);
					String typeNameAndName = prefix.substring(2);

					for (Map.Entry<String, DeclarationData> entry : declarations.entrySet()) {
						String key = entry.getKey();
						if ((key.indexOf(slashAndTypeNameAndName) != -1) || (key.startsWith(typeNameAndName))) {
							int index = key.lastIndexOf('-') + 1;
							if (matchDescriptors.apply(suffix, key.substring(index))) {
								ranges.add(new DocumentRange(entry.getValue().getStartPosition(),
										entry.getValue().getEndPosition()));
							}
						}
					}
				} else {
					// Known type
					for (Map.Entry<String, DeclarationData> entry : declarations.entrySet()) {
						String key = entry.getKey();
						if (key.startsWith(prefix)) {
							int index = key.lastIndexOf('-') + 1;
							if (matchDescriptors.apply(suffix, key.substring(index))) {
								ranges.add(new DocumentRange(entry.getValue().getStartPosition(),
										entry.getValue().getEndPosition()));
							}
						}
					}
				}
			}
		} else {
			// Known type and descriptor ==> Search and high light item
			DeclarationData data = declarations.get(fragment);
			if (data != null) {
				ranges.add(new DocumentRange(data.getStartPosition(), data.getEndPosition()));
			} else if (fragment.endsWith("-<clinit>-()V")) {
				// 'static' bloc not found ==> Select type declaration
				String typeName = fragment.substring(0, fragment.indexOf('-'));
				data = declarations.get(typeName);
				ranges.add(new DocumentRange(data.getStartPosition(), data.getEndPosition()));
			}
		}
	}

	public static void matchQueryAndAddDocumentRange(Map<String, String> parameters,
			Map<String, DeclarationData> declarations, Map<Integer, HyperlinkData> hyperlinks, List<StringData> strings,
			List<DocumentRange> ranges) {

		String highlightFlags = parameters.get("highlightFlags");
		String highlightPattern = parameters.get("highlightPattern");

		if ((highlightFlags != null) && (highlightPattern != null)) {
			String highlightScope = parameters.get("highlightScope");
			String regexp = createRegExp(highlightPattern);
			Pattern pattern = Pattern.compile(regexp + ".*");

			if (highlightFlags.indexOf('s') != -1) {
				// Highlight strings
				Pattern patternForString = Pattern.compile(regexp);

				for (StringData data : strings) {
					if (matchScope(highlightScope, data.getOwner())) {
						Matcher matcher = patternForString.matcher(data.getText());
						int offset = data.getStartPosition();

						while (matcher.find()) {
							ranges.add(new DocumentRange(offset + matcher.start(), offset + matcher.end()));
						}
					}
				}
			}

			boolean t = (highlightFlags.indexOf('t') != -1); // Highlight types
			boolean f = (highlightFlags.indexOf('f') != -1); // Highlight fields
			boolean m = (highlightFlags.indexOf('m') != -1); // Highlight methods
			boolean c = (highlightFlags.indexOf('c') != -1); // Highlight constructors

			if (highlightFlags.indexOf('d') != -1) {
				// Highlight declarations
				for (Map.Entry<String, DeclarationData> entry : declarations.entrySet()) {
					DeclarationData declaration = entry.getValue();

					if (matchScope(highlightScope, declaration.getTypeName())) {
						if ((t && declaration.isAType()) || (c && declaration.isAConstructor())) {
							matchAndAddDocumentRange(pattern, getMostInnerTypeName(declaration.getTypeName()),
									declaration.getStartPosition(), declaration.getEndPosition(), ranges);
						}
						if ((f && declaration.isAField()) || (m && declaration.isAMethod())) {
							matchAndAddDocumentRange(pattern, declaration.getName(), declaration.getStartPosition(),
									declaration.getEndPosition(), ranges);
						}
					}
				}
			}

			if (highlightFlags.indexOf('r') != -1) {
				// Highlight references
				for (Map.Entry<Integer, HyperlinkData> entry : hyperlinks.entrySet()) {
					HyperlinkData hyperlink = entry.getValue();
					ReferenceData reference = ((HyperlinkReferenceData) hyperlink).getReference();

					if (matchScope(highlightScope, reference.getOwner())) {
						if ((t && reference.isAType()) || (c && reference.isAConstructor())) {
							matchAndAddDocumentRange(pattern, getMostInnerTypeName(reference.getTypeName()),
									hyperlink.getStartPosition(), hyperlink.getEndPosition(), ranges);
						}
						if ((f && reference.isAField()) || (m && reference.isAMethod())) {
							matchAndAddDocumentRange(pattern, reference.getName(), hyperlink.getStartPosition(),
									hyperlink.getEndPosition(), ranges);
						}
					}
				}
			}
		}
	}

	public static boolean matchScope(String scope, String type) {
		if ((scope == null) || scope.isEmpty()) {
			return true;
		}
		if (scope.charAt(0) == '*') {
			return type.endsWith(scope.substring(1)) || type.equals(scope.substring(2));
		}
		return type.equals(scope);
	}

	public static void matchAndAddDocumentRange(Pattern pattern, String text, int start, int end,
			List<DocumentRange> ranges) {
		if (pattern.matcher(text).matches()) {
			ranges.add(new DocumentRange(start, end));
		}
	}

	public static String getMostInnerTypeName(String typeName) {
		int lastPackageSeparatorIndex = typeName.lastIndexOf('/') + 1;
		int lastTypeNameSeparatorIndex = typeName.lastIndexOf('$') + 1;
		int lastIndex = Math.max(lastPackageSeparatorIndex, lastTypeNameSeparatorIndex);
		return typeName.substring(lastIndex);
	}

	// --- FocusedTypeGettable --- //
	@Override
	public String getFocusedTypeName() {
		Map.Entry<Integer, DeclarationData> entry = listener.getTypeDeclarations()
				.floorEntry(textArea.getCaretPosition());

		if (entry != null) {
			DeclarationData data = entry.getValue();
			if (data != null) {
				return data.getTypeName();
			}
		}

		return null;
	}

	@Override
	public Container.Entry getEntry() {
		return entry;
	}

	// --- IndexesChangeListener --- //
	@Override
	public void indexesChanged(Collection<Future<Indexes>> collectionOfFutureIndexes) {
		// Update the list of containers
		this.collectionOfFutureIndexes = collectionOfFutureIndexes;
		// Refresh links
		boolean refresh = false;

		for (ReferenceData reference : listener.getReferences()) {
			String typeName = reference.getTypeName();
			boolean enabled;

			if (reference.getName() == null) {
				enabled = false;

				try {
					for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
						if (futureIndexes.isDone()) {
							Map<String, Collection> index = futureIndexes.get().getIndex("typeDeclarations");
							if ((index != null) && (index.get(typeName) != null)) {
								enabled = true;
								break;
							}
						}
					}
				} catch (Exception e) {
					assert ExceptionUtil.printStackTrace(e);
				}
			} else {
				try {
					// Recursive search
					typeName = searchTypeHavingMember(typeName, reference.getName(), reference.getDescriptor(), entry);
					if (typeName != null) {
						// Replace type with the real type having the referenced member
						reference.setTypeName(typeName);
						enabled = true;
					} else {
						enabled = false;
					}
				} catch (Error e) {
					// Catch StackOverflowError or OutOfMemoryError
					assert ExceptionUtil.printStackTrace(e);
					enabled = false;
				}
			}

			if (reference.isEnabled() != enabled) {
				reference.setEnabled(enabled);
				refresh = true;
			}
		}

		if (refresh) {
			textArea.repaint();
		}
	}

	@SuppressWarnings("unchecked")
	protected String searchTypeHavingMember(String typeName, String name, String descriptor, Container.Entry entry) {
		ArrayList<Container.Entry> entries = new ArrayList<>();

		try {
			for (Future<Indexes> futureIndexes : collectionOfFutureIndexes) {
				if (futureIndexes.isDone()) {
					Map<String, Collection> index = futureIndexes.get().getIndex("typeDeclarations");
					if (index != null) {
						Collection<Container.Entry> collection = index.get(typeName);
						if (collection != null) {
							entries.addAll(collection);
						}
					}
				}
			}
		} catch (Exception e) {
			assert ExceptionUtil.printStackTrace(e);
		}

		String rootUri = entry.getContainer().getRoot().getUri().toString();
		ArrayList<Container.Entry> sameContainerEntries = new ArrayList<>();

		for (Container.Entry e : entries) {
			if (e.getUri().toString().startsWith(rootUri)) {
				sameContainerEntries.add(e);
			}
		}

		if (sameContainerEntries.size() > 0) {
			return searchTypeHavingMember(typeName, name, descriptor, sameContainerEntries);
		} else {
			return searchTypeHavingMember(typeName, name, descriptor, entries);
		}
	}

	protected String searchTypeHavingMember(String typeName, String name, String descriptor,
			List<Container.Entry> entries) {
		for (Container.Entry entry : entries) {
			Type type = api.getTypeFactory(entry).make(api, entry, typeName);

			if (type != null) {
				if (descriptor.indexOf('(') == -1) {
					// Search a field
					for (Type.Field field : type.getFields()) {
						if (field.getName().equals(name)
								&& DescriptorMatcher.matchFieldDescriptors(field.getDescriptor(), descriptor)) {
							// Field found
							return typeName;
						}
					}
				} else {
					// Search a method
					for (Type.Method method : type.getMethods()) {
						if (method.getName().equals(name)
								&& DescriptorMatcher.matchMethodDescriptors(method.getDescriptor(), descriptor)) {
							// Method found
							return typeName;
						}
					}
				}

				// Not found -> Search in super type
				String typeOwnerName = searchTypeHavingMember(type.getSuperName(), name, descriptor, entry);
				if (typeOwnerName != null) {
					return typeOwnerName;
				}
			}
		}

		return null;
	}

	public void parseAndSetText(String text) {
		listener.clearData();
		// Parse
		char[] source = text.toCharArray();
		URI jarURI = entry.getContainer().getRoot().getParent().getUri();
		String unitName = entry.getPath();
		// 1st pass for declarations
		ASTParserFactory.getInstance().newASTParser(source, unitName, jarURI, listener.getDeclarationListener());
		listener.init();
		// 2nd pass for references
		ASTParserFactory.getInstanceWithBindings().newASTParser(source, unitName, jarURI, listener);
		// Display
		setText(text);
		initLineNumbers();
	}

}
