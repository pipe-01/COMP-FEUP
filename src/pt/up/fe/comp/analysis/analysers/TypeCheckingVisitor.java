package pt.up.fe.comp.analysis.analysers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pt.up.fe.comp.analysis.SemanticAnalyser;
import pt.up.fe.comp.analysis.SymbolTableBuilder;
import pt.up.fe.comp.ast.AstNode;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

public class TypeCheckingVisitor extends PostorderJmmVisitor<SymbolTableBuilder, Integer> implements SemanticAnalyser {

    private final List<Report> reports;


    public TypeCheckingVisitor() {
        reports = new ArrayList<>();

        addVisit(AstNode.BIN_OP.toString(), this::binOperationVisit);
        addVisit(AstNode.SELF_THIS.toString(), this::selfThisVisit);
        addVisit(AstNode.UNARY_OP.toString(), this::unaryOperationVisit);
        addVisit(AstNode.MEMBER_CALL.toString(), this::memberCallVisit);
        addVisit(AstNode.ASSIGNMENT.toString(), this::assignementVisit);
        addVisit(AstNode.CONSTRUCTOR.toString(), this::constructorVisit);
        addVisit(AstNode.LEN.toString(), this::lenVisit);
        addVisit(AstNode.WHILE_LOOP.toString(), this::conditionalVisit);
        addVisit(AstNode.IF_ELSE_STATEMENT.toString(), this::conditionalVisit);
        addVisit(AstNode.RETURN_VALUE.toString(), this::returnValueVisit);

    }



    private Integer lenVisit(JmmNode len, SymbolTableBuilder symbolTable) {
        len.put("type", "int");
        return 0;
    }

    private Integer conditionalVisit(JmmNode ifWhileNode, SymbolTableBuilder symbolTable) {
        var conditional = ifWhileNode.getJmmChild(0);
        var methodName = ifWhileNode.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");

        String conditionalType = "null";
        boolean isConditionalArray = false;

        switch (conditional.getKind()) {
            case "IntegerLiteral":
                conditionalType = "int";
                break;
            case "Identifier":
                var data = getIdentifierSymbolFromSymbolTable(conditional.get("name"), methodName, symbolTable);
                if(data == null){
                    return -1;
                }
                conditionalType = data.getType().getName();
                isConditionalArray = data.getType().isArray();
                break;
            case "BoolTrue":
            case "BoolFalse":
                conditionalType = "boolean";
                break;
            case "ArrayAssign":
                conditionalType = "int";
                isConditionalArray = true;
                break;
            case "BinOp":
                if(conditional.get("op").contains("lower") || conditional.get("op").contains("bigger") || conditional.get("op").contains("and") || conditional.get("op").contains("or"))
                    conditionalType = "boolean";
                break;
            default:
                conditionalType = conditional.get("type");

        }

        //If conditional is returned from an imported/extended method
        if(conditionalType.equals("undefined")){
            return 0;
        }

        if(!conditionalType.equals("boolean")){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(conditional.get("line")), Integer.valueOf(conditional.get("col")), "Condition must be of type 'boolean'"));
            return -1;
        }

        if(isConditionalArray){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(conditional.get("line")), Integer.valueOf(conditional.get("col")), "Condition can't be an array"));
            return -1;
        }
        return 0;
    }

    private Integer selfThisVisit(JmmNode selfThis, SymbolTableBuilder symbolTableBuilder) {
        selfThis.put("type", symbolTableBuilder.getClassName());
        return 0;
    }

    private Integer constructorVisit(JmmNode constructor, SymbolTableBuilder symbolTable) {
        constructor.put("type", constructor.getJmmChild(0).get("name"));

        return 0;
    }

    private Integer assignementVisit(JmmNode assign, SymbolTableBuilder symbolTable) {
        //TODO ASSIGNMENT TO EXPORTED CLASSES

        if(assign.getJmmChild(0).getKind().equals("BinOp")) {

            binOperationVisit(assign.getJmmChild(0), symbolTable);
        } else {

            var identifierNode = assign.getJmmChild(0);
            var assignee = assign.getJmmChild(1);

            var methodName = assign.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");


            Symbol identifier = getIdentifierSymbolFromSymbolTable(identifierNode.get("name"), methodName, symbolTable);

            String assigneeType;
            boolean isAssigneeArray = false;

            switch (assignee.getJmmChild(0).getKind()) {
                case "IntegerLiteral":
                    assigneeType = "int";
                    break;
                case "Identifier":
                    var data = getIdentifierSymbolFromSymbolTable(assignee.getJmmChild(0).get("name"), methodName, symbolTable);
                    if (data == null) {
                        return -1;
                    }
                    assigneeType = data.getType().getName();
                    isAssigneeArray = data.getType().isArray();
                    break;
                case "BoolTrue":
                case "BoolFalse":
                    assigneeType = "boolean";
                    break;
                case "ArrayAssign":
                    assigneeType = "int";
                    isAssigneeArray = true;
                    break;
                default:
                    assigneeType = assignee.getJmmChild(0).get("type");

            }

            if (isAssigneeArray != identifier.getType().isArray()) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(assign.get("line")), Integer.valueOf(assign.get("col")), "Both assigner and assignee must be or not arrays"));
                return -1;
            }

            //The assignee is from an import/extend
            if (assigneeType.equals("undefined")) {
                return 0;
            }

            //Verifies if both are imported
            for (String imp : symbolTable.getImports()) {
                if (identifier.getType().getName().equals(importName(imp))) {
                    for (String imp2 : symbolTable.getImports()) {
                        if (assigneeType.equals(importName(imp2))) {
                            return 0;
                        }
                    }
                }
            }

            //If it is super and receives this class
            if (identifier.getType().getName().equals(symbolTable.getSuper()) && assigneeType.equals(symbolTable.getClassName())) {
                return 0;
            }


            if (!assigneeType.equals(identifier.getType().getName())) {
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(assign.get("line")), Integer.valueOf(assign.get("col")), "Cannot assign something of type '" + identifier.getType().getName() + "' to a varible of type '" + assigneeType + "'"));
                return -1;
            }
        }
        //On VarDeclarationCheck we know if it is defined
        return 0;
    }

    private Integer memberCallVisit(JmmNode call, SymbolTableBuilder symbolTable) {
        var caller = call.getJmmChild(0);
        var methodName = caller.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");
        boolean isMethodInherited = false;

        if(call.getJmmChild(1).getKind().equals("Identifier")){
            isMethodInherited= !symbolTable.hasMethod(call.getJmmChild(1).get("name")) && (symbolTable.getSuper()!=null);
        }

        if(caller.getKind().equals("Identifier")){
            //check if caller is already defined
            var identifier = getIdentifierSymbolFromSymbolTable(caller.get("name"),methodName,symbolTable);
            if(identifier == null){
                if(!symbolTable.getImports().contains(caller.get("name"))){
                    call.put("type","INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(call.get("line")), Integer.valueOf(call.get("col")), "Invalid caller. Does not correspond to an method/variable/import"));
                    return -1;
                }
                call.put("type", "undefined");
                return 0;
            }
            caller.put("type",identifier.getType().getName() );
            if(identifier.getType().isArray()){
                caller.put("isArray", "true");
            }
            for (var imp : symbolTable.getImports()){
                if(identifier.getType().getName().equals(importName(imp))){
                    call.put("type", "undefined");
                    return 0;
                }
            }
        }

        if(caller.get("type").equals(symbolTable.getClassName()) && isMethodInherited ){
            call.put("type", "undefined");
            //its a method from imported class
            return 0;
        }

        var isCallerArray = caller.getOptional("isArray").map(isArray -> Boolean.valueOf(isArray)).orElse(false);

        //caller is Constructor
        var callee = call.getJmmChild(1);

        //Checks if
        if(callee.getKind().equals("Len")){
            if(!isCallerArray){
                call.put("type","INVALID");
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(callee.get("line")), Integer.valueOf(callee.get("col")), "To use length operator, caller must be an array"));
                return -1;
            }
            else{
                call.put("type","int");
                return 0;
            }

        }

        //If callee is memberCall
        if(callee.getKind().equals("MemberCall")){
            call.put("type", callee.get("type"));
        }

        if(!symbolTable.hasMethod(callee.get("name"))){
            call.put("type","undefined");
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(callee.get("line")), Integer.parseInt(callee.get("col")), "Method '" + callee.get("name")+ "' does not exist"));
            return -1;
        } else{
            call.put("type", symbolTable.getReturnType(callee.get("name")).getName());
        }



        //Verifies if method params are correct
        if(call.getChildren().size() > 2){
            var params = symbolTable.getParameters(callee.get("name"));
            var arguments =call.getJmmChild(2).getChildren();

            if(params.size()!= arguments.size()){
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(callee.get("line")), Integer.parseInt(callee.get("col")), "Were given " + arguments.size()+" arguments but it was supposed to be " + params.size() ));
                return -1;
            }

            for(int index = 0; index< params.size(); index++){
                String varType;
                boolean isVarArray = false;

                switch (arguments.get(index).getKind()) {
                    case "IntegerLiteral":
                        varType = "int";
                        break;
                    case "Identifier":
                        var data = getIdentifierSymbolFromSymbolTable(arguments.get(index).get("name"), methodName, symbolTable);
                        if(data == null){
                            return -1;
                        }
                        varType = data.getType().getName();
                        isVarArray = data.getType().isArray();
                        break;
                    case "BoolTrue":
                    case "BoolFalse":
                        varType = "boolean";
                        break;
                    case "ArrayAssign":
                        varType = "int";
                        isVarArray = true;
                        break;
                    default:
                        varType = arguments.get(index).get("type");

                }

                if(!params.get(index).getType().getName().equals(varType)){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(arguments.get(index).get("line")), Integer.parseInt(arguments.get(index).get("col")), "Invalid parameter. Was expected type '" +params.get(index).getType().getName()+ "' and got '"+ varType +"'" ));
                    return -1;
                }

                if(!(params.get(index).getType().isArray() == isVarArray)){
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(arguments.get(index).get("line")), Integer.parseInt(arguments.get(index).get("col")), "Invalid parameter. Should be and array and is not one or viceversa" ));
                    return -1;
                }
            }
        }else{
            if(!symbolTable.getParameters(callee.get("name")).isEmpty()){
                reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(callee.get("line")), Integer.parseInt(callee.get("col")), "Were given " + 0 +" arguments but it was supposed to have " + symbolTable.getParameters(callee.get("name")).size()  ));
                return -1;
            }
        }


        call.put("type", symbolTable.getReturnType(callee.get("name")).getName());

        return 0;
    }

    private Integer unaryOperationVisit(JmmNode operation, SymbolTableBuilder symbolTable) {
        var operator = operation.getJmmChild(0);
        var methodName = operation.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");

        String operatorType;
        boolean isOperatorArray = false;

        switch (operator.getKind()) {
            case "IntegerLiteral":
                operatorType = "int";
                break;
            case "Identifier":
                var data = getIdentifierSymbolFromSymbolTable(operator.get("name"), methodName, symbolTable);
                if(data == null){
                    return -1;
                }
                operatorType = data.getType().getName();
                isOperatorArray = data.getType().isArray();
                break;
            case "BoolTrue":
            case "BoolFalse":
                operatorType = "boolean";
                break;
            default:
                operatorType = operator.get("type");

        }

        switch (operation.get("op")){
            case "!":
                if (operatorType.equals("boolean") || operatorType.equals("undefined")) {
                    if (isOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '!' arrays"));
                        return -1;
                    }
                    operation.put("type", "boolean");
                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '!' with type '" + operatorType + "'" ));
                    return -1;
                }
            case "()":
                operation.put("type", operatorType);
                break;

        }


        return 0;
    }

    private Integer binOperationVisit(JmmNode operation, SymbolTableBuilder symbolTable) {

        //1º operator
        var leftOperator = operation.getJmmChild(0);
        var methodName = operation.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");
        String leftOperatorType;
        boolean isLeftOperatorArray = false;

        //Verificar o tipo do operador
        switch (leftOperator.getKind()) {
            case "IntegerLiteral":
                leftOperatorType = "int";
                break;
            case "Identifier":
                var data = getIdentifierSymbolFromSymbolTable(leftOperator.get("name"), methodName, symbolTable);
                if(data == null){
                    return -1;
                }
                leftOperatorType = data.getType().getName();
                isLeftOperatorArray = data.getType().isArray();
                break;
            case "BoolTrue":
            case "BoolFalse":
                leftOperatorType = "boolean";
                break;
            default:
                leftOperatorType = leftOperator.get("type");

        }

        //1º operator
        var rightOperator = operation.getJmmChild(1);
        String rightOperatorType;
        boolean isRightOperatorArray = false;

        //Verificar o tipo do operador
        switch (rightOperator.getKind()) {
            case "IntegerLiteral":
                rightOperatorType = "int";
                isRightOperatorArray = false;
                break;
            case "Identifier":
                var data = getIdentifierSymbolFromSymbolTable(rightOperator.get("name"), methodName, symbolTable);
                if(data == null){
                    return -1;
                }
                rightOperatorType = data.getType().getName();
                isRightOperatorArray = data.getType().isArray();
                break;
            case "BoolTrue":
            case "BoolFalse":
                rightOperatorType = "boolean";
                break;
            default:
                rightOperatorType = rightOperator.get("type");

        }

        boolean isPossibleToInt = (rightOperatorType.equals("int") && leftOperatorType.equals("int")) || (rightOperatorType.equals("undefined") && leftOperatorType.equals("int")) || (rightOperatorType.equals("int") && leftOperatorType.equals("undefined")) || (rightOperatorType.equals("undefined") && leftOperatorType.equals("undefined"));

        boolean isPossibleToBool = (rightOperatorType.equals("boolean") && leftOperatorType.equals("boolean")) || (rightOperatorType.equals("undefined") && leftOperatorType.equals("boolean")) || (rightOperatorType.equals("boolean") && leftOperatorType.equals("undefined")) || (rightOperatorType.equals("undefined") && leftOperatorType.equals("undefined"));
        switch (operation.get("op")) {
            case "add":
                if (isPossibleToInt) {
                    if (isLeftOperatorArray || isRightOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '+' arrays"));
                        return -1;
                    }
                    operation.put("type", "int");
                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '+' with types '" + leftOperatorType + "' and '" + rightOperatorType));
                    return -1;
                }
            case "sub":
                if (isPossibleToInt) {
                    if (isLeftOperatorArray || isRightOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '-' arrays"));
                        return -1;
                    }
                    operation.put("type", "int");
                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '-' with types '" + leftOperatorType + "' and '" + rightOperatorType));
                    return -1;
                }
            case "mult":
                if (isPossibleToInt) {
                    if (isLeftOperatorArray || isRightOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '*' arrays"));
                        return -1;
                    }
                    operation.put("type", "int");
                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '*' with types '" + leftOperatorType + "' and '" + rightOperatorType));
                    return -1;
                }
            case "div":
                if (isPossibleToInt) {
                    if (isLeftOperatorArray || isRightOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '/' arrays"));
                        return -1;
                    }
                    operation.put("type", "int");
                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '/' with types '" + leftOperatorType + "' and '" + rightOperatorType));
                    return -1;
                }

            case "and":
                if (isPossibleToBool) {
                    if (isLeftOperatorArray || isRightOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '&&' arrays"));
                        return -1;
                    }
                    operation.put("type", "boolean");
                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '&&' with types '" + leftOperatorType + "' and '" + rightOperatorType));
                    return -1;
                }

            case "lower":
                if (isPossibleToInt) {
                    if (isLeftOperatorArray || isRightOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '<' arrays"));
                        return -1;

                    }
                    operation.put("type", "boolean");

                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation '<' with types '" + leftOperatorType + "' and '" + rightOperatorType));
                    return -1;
                }

            case "index":
                if (rightOperatorType.equals("int") || rightOperatorType.equals("undefined")) {
                    if (!isLeftOperatorArray || isRightOperatorArray) {
                        operation.put("type", "INVALID");
                        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation index from variables that are not arrays or with indexes that are"));
                        return -1;
                    }
                    operation.put("type", leftOperatorType);
                    break;
                } else {
                    operation.put("type", "INVALID");
                    reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(operation.get("line")), Integer.valueOf(operation.get("col")), "Cannot realize operation index with index of a type '" + rightOperatorType + "'"));
                    return -1;
                }


        }

        return 0;


    }


    private Integer returnValueVisit(JmmNode ret, SymbolTableBuilder symbolTableBuilder) {

        var methodName = ret.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");

        var rightOperator = ret.getJmmChild(0);
        String returnType;
        boolean isReturnArray = false;

        //Verificar o tipo do operador
        switch (rightOperator.getKind()) {
            case "IntegerLiteral":
                returnType = "int";
                isReturnArray = false;
                break;
            case "Identifier":
                var data = getIdentifierSymbolFromSymbolTable(rightOperator.get("name"), methodName, symbolTableBuilder);
                if(data == null){
                    return -1;
                }
                returnType = data.getType().getName();
                isReturnArray = data.getType().isArray();
                break;
            case "BoolTrue":
            case "BoolFalse":
                returnType = "boolean";
                break;
            default:
                returnType = rightOperator.get("type");

        }

        var methodReturnType = symbolTableBuilder.getReturnType(methodName);

        if(!returnType.equals(methodReturnType.getName()) && !returnType.equals("undefined")){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(ret.get("line")), Integer.valueOf(ret.get("col")), "Return type is different from the method return type"));
            return -1;
        }

        if(isReturnArray!= methodReturnType.isArray()){
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.valueOf(ret.get("line")), Integer.valueOf(ret.get("col")), "Return type is different from the method return type"));
            return -1;
        }

        return 0;
    }

    private Symbol getIdentifierSymbolFromSymbolTable(String operator, String methodName, SymbolTableBuilder symbolTable) {

        if (symbolTable.hasLocalVariable(methodName, operator)) {
            for (Symbol localVar : symbolTable.getLocalVariables(methodName)) {
                if (localVar.getName().equals(operator)) return localVar;
            }
        }

        for (Symbol param : symbolTable.getParameters(methodName)) {
            if (param.getName().equals(operator)) return param;
        }

        if (symbolTable.hasField(operator)) {
            for (Symbol globalVar : symbolTable.getFields()) {
                if (globalVar.getName().equals(operator)) return globalVar;
            }
        }


        return null;

    }



    @Override
    public List<Report> getReports() {
        return reports;
    }

    private String importName(String imp){
        String[] impNames = imp.split("\\.");
        return impNames[impNames.length-1];
    }

}
