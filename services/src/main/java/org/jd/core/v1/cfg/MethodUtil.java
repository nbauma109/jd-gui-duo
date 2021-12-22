package org.jd.core.v1.cfg;

import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.loader.LoaderException;
import org.jd.core.v1.model.classfile.ClassFile;
import org.jd.core.v1.model.classfile.Method;
import org.jd.core.v1.model.javasyntax.CompilationUnit;
import org.jd.core.v1.model.javasyntax.declaration.*;
import org.jd.core.v1.model.message.DecompileContext;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.declaration.*;
import org.jd.core.v1.service.converter.classfiletojavasyntax.processor.ConvertClassFileProcessor;
import org.jd.core.v1.service.converter.classfiletojavasyntax.util.TypeMaker;
import org.jd.core.v1.service.deserializer.classfile.ClassFileDeserializer;

import java.io.IOException;

public class MethodUtil {

    private MethodUtil() {
    }

    public static Method searchMethod(Loader loader, TypeMaker typeMaker, String internalTypeName, String methodName, String methodDescriptor) throws LoaderException, IOException {
        ClassFileDeserializer deserializer = new ClassFileDeserializer();
        ConvertClassFileProcessor converter = new ConvertClassFileProcessor();
        DecompileContext decompileContext = new DecompileContext();
        decompileContext.setMainInternalTypeName(internalTypeName);
        decompileContext.setLoader(loader);
        decompileContext.setTypeMaker(typeMaker);

        ClassFile classFile = deserializer.loadClassFile(loader, internalTypeName);
        decompileContext.setClassFile(classFile);

        CompilationUnit compilationUnit = converter.process(classFile, typeMaker, decompileContext);

        BaseTypeDeclaration typeDeclarations = compilationUnit.getTypeDeclarations();
        BodyDeclaration bodyDeclaration = null;

        if (typeDeclarations instanceof EnumDeclaration ed) {
            bodyDeclaration = ed.getBodyDeclaration();
        } else if (typeDeclarations instanceof AnnotationDeclaration ad) {
            bodyDeclaration = ad.getBodyDeclaration();
        } else if (typeDeclarations instanceof InterfaceDeclaration id) {
            bodyDeclaration = id.getBodyDeclaration();
        }

        if (bodyDeclaration != null) {
            ClassFileBodyDeclaration cfbd = (ClassFileBodyDeclaration) bodyDeclaration;

            for (ClassFileConstructorOrMethodDeclaration md : cfbd.getMethodDeclarations()) {
                if (md instanceof ClassFileMethodDeclaration cfmd) {
                    if (cfmd.getName().equals(methodName) && ((methodDescriptor == null) || cfmd.getDescriptor().equals(methodDescriptor))) {
                        return cfmd.getMethod();
                    }
                } else if (md instanceof ClassFileConstructorDeclaration cfcd) {
                    if (cfcd.getMethod().getName().equals(methodName) && ((methodDescriptor == null) || cfcd.getDescriptor().equals(methodDescriptor))) {
                        return cfcd.getMethod();
                    }
                } else if (md instanceof ClassFileStaticInitializerDeclaration cfsid && cfsid.getMethod().getName().equals(methodName)) {
                    return cfsid.getMethod();
                }
            }
        }

        return null;
    }
}
