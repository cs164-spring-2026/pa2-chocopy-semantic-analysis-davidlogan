package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.astnodes.TypeAnnotation;
import chocopy.common.astnodes.ClassType;
import chocopy.common.astnodes.TypedVar;
import java.util.List;
import java.util.ArrayList;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.VarDef;
import chocopy.common.astnodes.ListType;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.ClassDef;


/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Type> {

    /** Current symbol table.  Changes with new declarative region. */
    private SymbolTable<Type> sym = new SymbolTable<>();
    /** Global symbol table. */
    private final SymbolTable<Type> globals = sym;
    /** Receiver for semantic error messages. */
    private final Errors errors;

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
        // Put in built print, len, and input in the global scope.
        sym.put("print", new FuncType(
            java.util.Arrays.asList(ValueType.OBJECT_TYPE),
            ValueType.OBJECT_TYPE
        ));
        
        sym.put("len", new FuncType(
            java.util.Arrays.asList(ValueType.OBJECT_TYPE),
            ValueType.INT_TYPE
        ));
        
        sym.put("input", new FuncType(
            java.util.Collections.emptyList(),
            ValueType.STR_TYPE
        ));
        // built in class names
        sym.put("object", ValueType.OBJECT_TYPE);
        sym.put("str", ValueType.STR_TYPE);
        sym.put("int", ValueType.INT_TYPE);
        sym.put("bool", ValueType.BOOL_TYPE);

    }


    public SymbolTable<Type> getGlobals() {
        return globals;
    }
    // Some functions
    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) { // Wow syntax.
            Identifier id = decl.getIdentifier();
            String name = id.name;

            Type type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }
        }

        return null;
    }
    // What does this do right now? What is this program split?
    @Override
    public Type analyze(VarDef vd) {
        checkTypeExists(vd.var.type); // Why dont I catch the D inside the function?
        return ValueType.annotationToValueType(vd.var.type);
    }
    @Override
    public Type analyze(FuncDef f) {
        // We have to keep both the parameters and the return type in the thing.
        List<ValueType> paramTypes = new ArrayList<>();
        for (TypedVar param : f.params) {
            checkTypeExists(param.type);
            ValueType paramType = ValueType.annotationToValueType(param.type);
            paramTypes.add(paramType);
        }   
        // Get return type
        ValueType returnType = ValueType.annotationToValueType(f.returnType);
        checkTypeExists(f.returnType);

        // I probably have to do things with creating a frame here, but for now, I'm brute force checking.
        // Bigger picture problem is how do frames work here? From lec -> I need like 4 contexts.

        for (Declaration decl : f.declarations) { // Wow syntax.
            Identifier id = decl.getIdentifier();
            String name = id.name;

            Type type = decl.dispatch(this);

            if (type == null) {
                continue;
            }

            if (sym.declares(name)) {
                errors.semError(id,
                                "Duplicate declaration of identifier in same "
                                + "scope: %s",
                                name);
            } else {
                sym.put(name, type);
            }
        }
        // Create function type
        return new FuncType(paramTypes, returnType);
    }
    @Override
    public Type analyze(ClassDef c) {
        if (!sym.declares(c.superClass.name)) {
            errors.semError(c,
                "Invalid superclass; there is no class named: %s",
                c.superClass.name);
        }
        return new ClassValueType(c.name.name);
    }
    private boolean checkTypeExists(TypeAnnotation typeAnnotation){ // What do I need to take in to give an error if type doesnt exist?
        // Type annotation is either a ClassType or ListType. 
        // Where the type annotation is depends on the statement.
        if (typeAnnotation instanceof ClassType) {
            ClassType classType = (ClassType) typeAnnotation;
            if (!sym.declares(classType.className)) {
                errors.semError(typeAnnotation,
                    "Invalid type annotation; there is no class named: %s",
                    classType.className);
                    return false;
            }
        } else if (typeAnnotation instanceof ListType) { 
            ListType listType = (ListType) typeAnnotation;
            // Recursively check the element type
            checkTypeExists(listType.elementType);
        }
        return true;
    }

}
