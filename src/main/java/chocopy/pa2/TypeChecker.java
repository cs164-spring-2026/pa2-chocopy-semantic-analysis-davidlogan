package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.BinaryExpr;
import chocopy.common.astnodes.ListExpr;
import chocopy.common.astnodes.UnaryExpr;
import chocopy.common.astnodes.IfExpr;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.ExprStmt;
import chocopy.common.astnodes.VarDef;
import chocopy.common.astnodes.AssignStmt;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.StringLiteral;
import chocopy.common.astnodes.NoneLiteral;
import chocopy.common.astnodes.BooleanLiteral;
import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.Stmt;

import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;
import static chocopy.common.analysis.types.Type.BOOL_TYPE;
import static chocopy.common.analysis.types.Type.STR_TYPE;
import static chocopy.common.analysis.types.Type.NONE_TYPE;
import static chocopy.common.analysis.types.Type.EMPTY_TYPE;

import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.ClassValueType;


/** Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 *  collecting declarations. */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {

    /** The current symbol table (changes depending on the function
     *  being analyzed). */
    private SymbolTable<Type> sym;
    /** Collector for errors. */
    private Errors errors;


    /** Creates a type checker using GLOBALSYMBOLS for the initial global
     *  symbol table and ERRORS0 to receive semantic errors. */
    public TypeChecker(SymbolTable<Type> globalSymbols, Errors errors0) {
        sym = globalSymbols;
        errors = errors0;
    }

    /** Inserts an error message in NODE if there isn't one already.
     *  The message is constructed with MESSAGE and ARGS as for
     *  String.format. */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(ExprStmt s) {
        s.expr.dispatch(this);
        return null;
    }
    // Returns the LUB type between type t1 and t2 passed in. 
    private Type LUB(Type t1, Type t2){
        // its a stub for now.
        if(t1.equals(t2)) return t1;
        // Empty list type conforms to any list
        if (EMPTY_TYPE.equals(t1) && t2 instanceof ListValueType) {
            return t2;
        }
        if (EMPTY_TYPE.equals(t2) && t1 instanceof ListValueType) {
            return t1;
        }
        
        // TODO later: proper class hierarchy LUB
        return Type.OBJECT_TYPE;
    }

    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(Type.INT_TYPE);
    }
    @Override
    public Type analyze(StringLiteral i) {
        return i.setInferredType(Type.STR_TYPE);
    }
    
    @Override
    public Type analyze(BooleanLiteral u){
        return u.setInferredType(Type.BOOL_TYPE);
    }
    @Override
    public Type analyze(NoneLiteral n) {
        return n.setInferredType(Type.NONE_TYPE);
    }

    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);
        // Complete For all operators
        switch (e.operator) {
        // Plus is special case. Valid for int/int, str/str, list/list
        case "+":
            if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                return e.setInferredType(INT_TYPE);
            } else if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)){
                return e.setInferredType(STR_TYPE);
            } else if (t1 instanceof ListValueType && t2 instanceof ListValueType) {
                // [T1] + [T2] -> [join(T1, T2)]
                ListValueType list1 = (ListValueType) t1; //f
                ListValueType list2 = (ListValueType) t2;
                Type elemType = LUB(list1.elementType(), list2.elementType());
                return e.setInferredType(new ListValueType(elemType));
            } else {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                // In spec -> if at least one is int, recover as int, otherwise recover as object.
                if (INT_TYPE.equals(t1) || INT_TYPE.equals(t2)){
                    return e.setInferredType(INT_TYPE);
                }
                return e.setInferredType(OBJECT_TYPE); // as the default
            }

        // int, int -> int operators
        case "-": // int, int. No strings, its type error.
            return e.setInferredType(intMatch(t1, t2, e));
        case "*": 
            return e.setInferredType(intMatch(t1, t2, e));
        case "//":
            return e.setInferredType(intMatch(t1, t2, e));
        case "%":
            return e.setInferredType(intMatch(t1, t2, e));
        
        // Comparison operators. 
        // These ordered ones only work on int
        case ">":
            return e.setInferredType(orderComparisonMatch(t1, t2, e));
        case "<":
            return e.setInferredType(orderComparisonMatch(t1, t2, e));
        case ">=":
            return e.setInferredType(orderComparisonMatch(t1, t2, e));
        case "<=":
            return e.setInferredType(orderComparisonMatch(t1, t2, e));
        // works on int, string or binary.
        case "==":
            return e.setInferredType(comparisonMatch(t1, t2, e));
        case "!=":
            return e.setInferredType(comparisonMatch(t1, t2, e));
        // Logical

        case "and":
            return e.setInferredType(logicalMatch(t1, t2, e));
        case "or":
            return e.setInferredType(logicalMatch(t1, t2, e));

        // Chocopy isnt supposed to support is and it only shows up in the bad error cases? idk man.
        case "is":
            // Always error - not supported in typed ChocoPy
            if (t1.isSpecialType() || t2.isSpecialType()) {
                err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                    e.operator, t1, t2);
            }
            return e.setInferredType(BOOL_TYPE);  // Still infer bool
        default:
            return e.setInferredType(OBJECT_TYPE);
        }

    }
    private Type intMatch(Type t1, Type t2, BinaryExpr e){
        if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
            return INT_TYPE;
        } else {
            err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                 e.operator, t1, t2);
            return INT_TYPE;
        }
    }
    private Type orderComparisonMatch(Type t1, Type t2, BinaryExpr e){
        // Comparison operators ONLY work on int, bool, or str (same type)
        // NOT on None, object, or lists!
        if (t1.equals(t2) && (t1.equals(INT_TYPE))) {
            return BOOL_TYPE;
        } else {
            err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                e.operator, t1, t2);
            return BOOL_TYPE;
        }
    }
    private Type comparisonMatch(Type t1, Type t2, BinaryExpr e){
        // Comparison operators ONLY work on int, bool, or str (same type)
        // NOT on None, object, or lists!
        if (t1.equals(t2) && (t1.equals(INT_TYPE) || t1.equals(BOOL_TYPE) || t1.equals(STR_TYPE))) {
            return BOOL_TYPE;
        } else {
            err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                e.operator, t1, t2);
            return BOOL_TYPE;
        }
    }
    private Type logicalMatch(Type t1, Type t2, BinaryExpr e){
         if (BOOL_TYPE.equals(t1) && BOOL_TYPE.equals(t2)) {
            return BOOL_TYPE;
        } else {
            err(e, "Cannot apply operator `%s` on types `%s` and `%s`",
                e.operator, t1, t2);
            return BOOL_TYPE;
        }
    }
    @Override 
    public Type analyze(ListExpr l){
        // Empty list case:
        if (l.elements.isEmpty()){
            return l.setInferredType(EMPTY_TYPE);
        }
        Type firstType = l.elements.get(0).dispatch(this);
        // List type is least upper bound. 
        for (int i = 1; i < l.elements.size(); i++){
            Type t = l.elements.get(i).dispatch(this);
            firstType = LUB(firstType, t); // We have to  as a helper
        }
        return l.setInferredType(new ListValueType(firstType));
    }
    // LUB implementation

    @Override
    public Type analyze(UnaryExpr e) {
        Type t1 = e.operand.dispatch(this); // This is our literal/other expr. We run recursive analysis on this.
        switch (e.operator) {
        case "-": 
            if (INT_TYPE.equals(t1)) { // We allow negative
                return e.setInferredType(INT_TYPE);
            } else{
                err(e, "Cannot apply operator `%s` on type `%s`", // Cant apply negative to integers in chocopy. Its valid python syntax tho.;
                    e.operator, t1);
                return e.setInferredType(INT_TYPE); // error recovery -> I.e, we still return this here.
            }
        case "not":
            if (BOOL_TYPE.equals(t1)) { // We allow negative
                return e.setInferredType(BOOL_TYPE);
            } else {
                err(e, "Cannot apply operator `%s` on type `%s`", // Cant apply negative to integers in chocopy. Its valid python syntax tho.;
                    e.operator, t1);
                return e.setInferredType(BOOL_TYPE); // error recovery -> I.e, we still return this here.
            }
        default:
            return e.setInferredType(OBJECT_TYPE);
        }
    }
    
    @Override
    public Type analyze(IfExpr e) {
        Type t1 = e.condition.dispatch(this); // Goes to BinaryExpr
        Type t2 = e.thenExpr.dispatch(this); // in conditionals this is just a literal.
        Type t3 = e.elseExpr.dispatch(this); // same here.
        if (!BOOL_TYPE.equals(t1)){
            err(e, "Inside of if conditional is not a boolean");
        }
        return e.setInferredType(LUB(t2, t3)); // are we done?
    }
    @Override
    public Type analyze(Identifier id) {
        String varName = id.name;
        Type varType = sym.get(varName); // I get symbol table as sym here.

        if (varType != null && varType.isValueType()) {
            return id.setInferredType(varType);
        }

        err(id, "Not a variable: %s", varName);
        return id.setInferredType(ValueType.OBJECT_TYPE);
    }
    @Override 
    public Type analyze(VarDef v){
        Type t1 = v.value.dispatch(this);
        return null; // I'm not setting type for VarDef
    }
    @Override 
    public Type analyze(AssignStmt a) { 
        // I have many targets, and one value. We have to 
        Type value_type = a.value.dispatch(this);
        for (int i = 0; i < a.targets.size(); i++) {
            Type t = a.targets.get(i).dispatch(this);
            // Check that type t is LUB with with value?
            if (!conformsTo(value_type, t)) {
                err(a, "Cannot assign Identifier of type `%s` with value type `%s`", // Cant apply negative to integers in chocopy. Its valid python syntax tho.;
                    t, value_type);
            }
        }
        return null;
    }
    //Check 
    private boolean conformsTo(Type t1, Type t2){
            // Same type always conforms
        if (t1.equals(t2)) return true;
        
        // Everything conforms to object
        if (OBJECT_TYPE.equals(t2)) return true;
        
        // None conforms to any class type (but not int, bool, str)
        if (NONE_TYPE.equals(t1) && t2 instanceof ClassValueType && 
            !t2.isSpecialType()) {
            return true;
        }
        
        // TODO later: class hierarchy (B <: A if B extends A)
        // TODO later: lists are INVARIANT ([int] does NOT conform to [object])
        
        return false;
        
    }
}
