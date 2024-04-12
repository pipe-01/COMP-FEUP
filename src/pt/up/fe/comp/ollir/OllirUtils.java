package pt.up.fe.comp.ollir;


import pt.up.fe.comp.analysis.SymbolTableBuilder;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.List;

public class OllirUtils {

    public static String getCode(Symbol symbol){
        return symbol.getName() + "." + getCode(symbol.getType());
    }

    public static String getCode(Type type){
        StringBuilder code = new StringBuilder();

        if(type.isArray()){
            code.append("array.");
        }

        code.append(getOllirType(type.getName()));

        return code.toString();
    }

    public static String getOllirType(String jmmType){

        switch (jmmType){
            case "void":
                return "V";
            case "int":
                return "i32";
            case "boolean":
                return "bool";
            default:
                return jmmType;
        }

    }

    public static String init(String className, String superName, List<Symbol> fields) {
        if (superName != null)
            return className + " extends " + superName + " {\n" + fields(fields) + "\n.construct " + className + "().V {\n" + invokeSpecial("this") + "}\n\n";
        else
            return className + " {\n" + fields(fields) + "\n.construct " + className + "().V {\n" + invokeSpecial("this") + "}\n\n";
    }

    public static String fields(List<Symbol> fields) {
        if (fields.isEmpty())
            return "";
        StringBuilder code = new StringBuilder();

        for (Symbol field : fields) {
            code.append(".field ").append("private ").append(getCode(field)).append(";\n");
        }

        return code.toString();
    }

    public static String invokeSpecial(String identifierClass) {
        return "invokespecial(" + identifierClass + ", \"<init>\").V;\n";
    }


    public static String methodDeclaration(String method_name, boolean isStatic, Type returnType, List<Symbol> parameters) {
        String methodDeclaration = "";
        methodDeclaration += ".method public ";
        if(isStatic) methodDeclaration+= "static ";
        methodDeclaration+= method_name + "(";

        for(var i= 0; i< parameters.size(); i++){
            var memberParam = getCode(parameters.get(i));
            methodDeclaration+= memberParam;
            if(i!= parameters.size()-1) methodDeclaration+= " ,";
        }

        methodDeclaration+= ")." + getCode(returnType) + "{\n";

        return methodDeclaration;

    }

    public static String getCodeFromSymbolTable(String idName, String methodName, SymbolTableBuilder symbolTable) {

        if(!methodName.equals("Error")){
            if (symbolTable.hasLocalVariable(methodName, idName)) {
                for (Symbol localVar : symbolTable.getLocalVariables(methodName)) {
                    if (localVar.getName().equals(idName)) return getCode(localVar);
                }
            }

            for (int i=0; i< symbolTable.getParameters(methodName).size(); i++) {
                if (symbolTable.getParameters(methodName).get(i).getName().equals(idName)) return "$" + (i + 1)+ "." + getCode(symbolTable.getParameters(methodName).get(i));
            }
        }

        if (symbolTable.hasField(idName)) {
            for (Symbol globalVar : symbolTable.getFields()) {
                if (globalVar.getName().equals(idName)) return getCode(globalVar);
            }
        }


        return null;
    }

    public static Symbol getSymbolFromSymbolTable(String idName, String methodName, SymbolTableBuilder symbolTable){
        if(!methodName.equals("Error")){
            if (symbolTable.hasLocalVariable(methodName, idName)) {
                for (Symbol localVar : symbolTable.getLocalVariables(methodName)) {
                    if (localVar.getName().equals(idName)) return localVar;
                }
            }

            for (int i=0; i< symbolTable.getParameters(methodName).size(); i++) {
                if (symbolTable.getParameters(methodName).get(i).getName().equals(idName)) return symbolTable.getParameters(methodName).get(i);
            }
        }

        if (symbolTable.hasField(idName)) {
            for (Symbol globalVar : symbolTable.getFields()) {
                if (globalVar.getName().equals(idName)) return globalVar;
            }
        }


        return null;

    }

    public static boolean isField(String idName, String methodName, SymbolTableBuilder symbolTable){
        if(!methodName.equals("Error")){
            if (symbolTable.hasLocalVariable(methodName, idName)) {
                for (Symbol localVar : symbolTable.getLocalVariables(methodName)) {
                    if (localVar.getName().equals(idName)) return false;
                }
            }

            for (int i=0; i< symbolTable.getParameters(methodName).size(); i++) {
                if (symbolTable.getParameters(methodName).get(i).getName().equals(idName)) return false;
            }
        }

        if (symbolTable.hasField(idName)) {
            for (Symbol globalVar : symbolTable.getFields()) {
                if (globalVar.getName().equals(idName)) return true;
            }
        }


        return false;
    }
}
