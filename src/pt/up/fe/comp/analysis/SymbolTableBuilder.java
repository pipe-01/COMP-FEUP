package pt.up.fe.comp.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.*;

public class SymbolTableBuilder implements SymbolTable {

    private final List<String> imports;
    private String className;
    private String superClass;
    private final List<Symbol> fields;
    private final List<String> methods;
    private final Map<String, Type> methodReturnType;
    private final Map<String, List<Symbol>> methodParams;
    private final Map<String, List<Symbol>> localVariables;

    public SymbolTableBuilder() {
        this.imports = new ArrayList<>();
        this.className = null;
        this.superClass = null;
        this.fields = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.methodReturnType = new HashMap<>();
        this.methodParams = new HashMap<>();
        this.localVariables = new HashMap<>();
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    public void addImport(String importString){
        imports.add(importString);
    }

    @Override
    public String getClassName() {
        return className;
    }

    public void setClassName(String className){
        this.className = className;
    }

    @Override
    public String getSuper() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    @Override
    public List<Symbol> getFields() {
        return this.fields ;
    }

    public boolean hasField(String fieldName){
        for (Symbol field: fields) {
            if(field.getName().equals(fieldName)){return true;}
        }

        return false;
    }

    public void addField( Symbol symbol){
        this.fields.add(symbol);
    }

    @Override
    public List<String> getMethods() {
        return methods;
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return methodReturnType.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return methodParams.get(methodSignature);
    }

    public boolean hasMethod(String methodSignature){
        return methods.contains(methodSignature);
    }

    public void addMethod(String methodSignature, Type returnType, List<Symbol> params){
        methods.add(methodSignature);
        methodReturnType.put(methodSignature, returnType);
        methodParams.put(methodSignature, params);
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return localVariables.get(methodSignature);
    }

    public void addLocalVariable(String methodSignature, Symbol variable){
        List<Symbol> variables;
        if(localVariables.containsKey(methodSignature)){
            variables = localVariables.get(methodSignature);
        }else{
            variables = new ArrayList<>();
        }

        variables.add(variable);
        localVariables.put(methodSignature, variables);


    }

    public boolean hasLocalVariable(String methodSignature, String variable){
        if(!localVariables.containsKey(methodSignature)){ return false;}

        if(localVariables.get(methodSignature).stream().anyMatch(symbol -> symbol.getName().equals(variable))){return true;}

        return false;
    }

}
