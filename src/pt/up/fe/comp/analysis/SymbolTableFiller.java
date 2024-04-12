package pt.up.fe.comp.analysis;

import pt.up.fe.comp.AstUtils;
import pt.up.fe.comp.ast.AstNode;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SymbolTableFiller extends PreorderJmmVisitor<SymbolTableBuilder, Integer> {

    private final List<Report> reports;

    public SymbolTableFiller() {
        addVisit(AstNode.IMPORT_DECLARATION.toString(), this::importDeclVisit);
        addVisit(AstNode.CLASS_DECLARATION.toString(), this::classDeclVisit);
        addVisit(AstNode.METHOD_DECLARATION.toString(), this::methodDeclVisit);
        addVisit(AstNode.VAR_DECLARATION.toString(), this::varDeclVisit);
        this.reports = new ArrayList<>();
    }


    public List<Report> getReports() {
        return reports;
    }

    private Integer importDeclVisit(JmmNode importDeclaration, SymbolTableBuilder symbolTable) {
        var importString = importDeclaration.getChildren().stream().map(id -> id.get("name")).collect(Collectors.joining("."));
        symbolTable.addImport(importString);
        return 0;
    }

    private Integer classDeclVisit(JmmNode classDeclaration, SymbolTableBuilder symbolTable) {
        symbolTable.setClassName(classDeclaration.get("name"));
        classDeclaration.getOptional("extends").ifPresent(superClass -> {
            for(String imp : symbolTable.getImports()){
                if(superClass.equals(importName(imp))){symbolTable.setSuperClass(superClass);    return;}

            }
            reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(classDeclaration.get("line")), Integer.valueOf(classDeclaration.get("col")), "Invalid type for extend", null));
        });
        return 0;
    }

    private Integer varDeclVisit(JmmNode varDeclaration, SymbolTableBuilder symbolTable) {
        var varName = varDeclaration.getJmmChild(1).get("name");

        if(symbolTable.hasField(varName)){
            reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(varDeclaration.get("line")), Integer.valueOf(varDeclaration.get("col")), "Found duplicated with signature " + varName, null));
            return -1;
        }

        var varTypeNode = varDeclaration.getJmmChild(0);
        if(!isTypeValid(varTypeNode.get("name"), symbolTable.getClassName(), symbolTable.getImports() , false)){
            reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(varDeclaration.get("line")), Integer.valueOf(varDeclaration.get("col")), "Invalid type for declaration " + varTypeNode.get("name"), null));
            return -1;
        }
        var varType = AstUtils.buildType(varTypeNode);

        symbolTable.addField( new Symbol(varType, varName));

        return 0;
    }

    private Integer methodDeclVisit(JmmNode methodDeclaration, SymbolTableBuilder symbolTable) {
        var methodName = methodDeclaration.getJmmChild(1).get("name");

        if(symbolTable.hasMethod(methodName)){
            reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(methodDeclaration.get("line")), Integer.valueOf(methodDeclaration.get("col")), "Found duplicated with signature " + methodName, null));
            return -1;
        }

        var returnTypeNode = methodDeclaration.getJmmChild(0);
        if(!isTypeValid(returnTypeNode.get("name"), symbolTable.getClassName(), symbolTable.getImports(), true)){
            reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(returnTypeNode.get("line")), Integer.valueOf(returnTypeNode.get("col")), "Invalid type for declaration " + returnTypeNode.get("name"), null));
            return -1;
        }
        var returnType = AstUtils.buildReturnType(returnTypeNode);

        var params = methodDeclaration.getChildren().subList(2, methodDeclaration.getNumChildren()).stream().filter(node -> node.getKind().equals("Param")).collect(Collectors.toList());
        //var paramSymbols = params.stream().map(param-> new Symbol(AstUtils.buildTypeParam(param.getJmmChild(0)), param.getJmmChild(1).get("name"))).collect(Collectors.toList());
        ArrayList<Symbol> paramSymbols = new ArrayList<>();
        ArrayList<String> paramNames = new ArrayList<>();

        for( JmmNode node: params){
            var paramName = node.getJmmChild(1).get("name");
            var paramType = AstUtils.buildTypeParam(node.getJmmChild(0));

            if(!methodName.equals("main") || !paramName.equals("args") || !paramType.isArray() || !paramType.getName().equals("String")){
                if(!isTypeValid(paramType.getName(), symbolTable.getClassName() , symbolTable.getImports(), false)){
                    reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(node.get("line")), Integer.valueOf(node.get("col")), "Invalid type for declaration " + paramType.getName(), null));
                    return -1;
                }
            }

            if(paramSymbols.isEmpty()){
                paramNames.add(paramName);
                paramSymbols.add(new Symbol(paramType, paramName));
            } else{
                if(paramNames.contains(paramName)){
                    reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(node.get("line")), Integer.valueOf(node.get("col")), "Found duplicated with signature " + paramName, null));
                    return -1;
                }
                paramNames.add(paramName);
                paramSymbols.add(new Symbol(paramType, paramName));
            }
        }

        symbolTable.addMethod(methodName, returnType, paramSymbols);


        // -------------- Local Variables ------------------------

        var memberInteriorNode = methodDeclaration.getChildren().subList(2, methodDeclaration.getNumChildren()).stream().filter(node -> node.getKind().equals(AstNode.METHOD_INTERIOR.toString())).collect(Collectors.toList()).get(0);
        var localDeclarationNodes = memberInteriorNode.getChildren().stream().filter(node -> node.getKind().equals(AstNode.LOCAL_VAR_DECLARATION.toString())).collect(Collectors.toList());

        for(JmmNode node: localDeclarationNodes){
            var varName = node.getJmmChild(1).get("name");
            var varType = AstUtils.buildType(node.getJmmChild(0));
            if(!isTypeValid(varType.getName(), symbolTable.getClassName(), symbolTable.getImports(), false)){
                reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(node.get("line")), Integer.valueOf(node.get("col")), "Invalid type for declaration " + varType.getName(), null));
                return -1;
            }
            Symbol localVariable = new Symbol(varType,varName);

            if(paramNames.contains(varName)){
                reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(node.get("line")), Integer.valueOf(node.get("col")), "Found parameter with such name " + varName, null));
                return -1;
            }

            if(symbolTable.hasLocalVariable(methodName, varName)){
                reports.add(Report.newError(Stage.SEMANTIC, Integer.valueOf(node.get("line")), Integer.valueOf(node.get("col")), "Found duplicated with signature " + varName, null));
                return -1;
            }

            symbolTable.addLocalVariable(methodName,localVariable);
        }


        return 0;
    }


    private boolean isTypeValid(String type, String className, List<String> imports, boolean isMethod){
        boolean isImport = false;

        for(String imp : imports){
            if (type.equals(importName(imp))) {
                isImport = true;
                break;
            }
        }
        switch (type){
            case "int": return true;
            case "boolean": return true;
            case "void":
                return isMethod;
            default:
                if(type.equals(className)|| isImport) return true;
                return false;
        }
    }


    private String importName(String imp){
        String[] impNames = imp.split("\\.", 2);
        return impNames[impNames.length-1];
    }


}
