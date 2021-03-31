package org.jd.gui.util.decompiler.postprocess.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.DeobfuscationUtilities;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.assembler.metadata.MetadataParser;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.core.StringUtilities;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.ITextOutput;
import com.strobel.decompiler.languages.BytecodeLanguage;

public class JarEntryDecompiler {

	public List<TypeDefinition> decompileJarEntry(final URI jarFilePath, final String targetedEntry,
			final DecompilationOptions decompilationOptions, final ITextOutput output) throws IOException {

		List<TypeDefinition> types = new ArrayList<>();
		
		final File jarFile = new File(jarFilePath);

		if (!jarFile.exists()) {
			throw new FileNotFoundException("File not found: " + jarFilePath);
		}

		final DecompilerSettings settings = decompilationOptions.getSettings();
		final JarFile jar = new JarFile(jarFile);
		final Enumeration<JarEntry> entries = jar.entries();

		final boolean oldShowSyntheticMembers = settings.getShowSyntheticMembers();
		final ITypeLoader oldTypeLoader = settings.getTypeLoader();

		settings.setShowSyntheticMembers(false);
		settings.setTypeLoader(new CompositeTypeLoader(new JarTypeLoader(jar), oldTypeLoader));

		try {
			MetadataSystem metadataSystem = new MetadataSystem(settings.getTypeLoader());

			while (entries.hasMoreElements()) {
				final JarEntry entry = entries.nextElement();
				final String name = entry.getName();

				if (!name.endsWith(".class")) {
					continue;
				}

				final String internalName = StringUtilities.removeRight(name, ".class");
				final String intargetName = StringUtilities.removeRight(targetedEntry, ".class");

				if (!internalName.equals(intargetName) && !internalName.startsWith(intargetName + '$')) {
					continue;
				}

				TypeDefinition type = decompileType(metadataSystem, internalName, decompilationOptions, false, output);
				if (type != null) {
					types.add(type);
				}
			}
		} finally {
			settings.setShowSyntheticMembers(oldShowSyntheticMembers);
			settings.setTypeLoader(oldTypeLoader);
		}
		return types;
	}

	public TypeDefinition decompileType(final MetadataSystem metadataSystem, final String typeName,
			final DecompilationOptions options, final boolean includeNested, final ITextOutput output)
			throws IOException {

		final TypeReference type;
		final DecompilerSettings settings = options.getSettings();

		if (typeName.length() == 1) {
			//
			// Hack to get around classes whose descriptors clash with primitive types.
			//

			final MetadataParser parser = new MetadataParser(IMetadataResolver.EMPTY);
			final TypeReference reference = parser.parseTypeDescriptor(typeName);

			type = metadataSystem.resolve(reference);
		} else {
			type = metadataSystem.lookupType(typeName);
		}

		final TypeDefinition resolvedType;

		if (type == null || (resolvedType = type.resolve()) == null) {
			System.err.printf("!!! ERROR: Failed to load class %s.\n", typeName);
			return null;
		}

		DeobfuscationUtilities.processType(resolvedType);

		if (!includeNested && (resolvedType.isNested() || resolvedType.isAnonymous() || resolvedType.isSynthetic())) {
			return null;
		}

		if (settings.getLanguage() instanceof BytecodeLanguage) {
			output.setIndentToken("  ");
		}

		settings.getLanguage().decompileType(resolvedType, output, options);

		return resolvedType;
	}

}
