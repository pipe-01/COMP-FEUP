package pt.up.fe.comp.ollir;

import org.specs.comp.ollir.Ollir;

import pt.up.fe.comp.analysis.SymbolTableBuilder;
import pt.up.fe.comp.ast.AstNode;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.Optional;
import java.util.stream.Collectors;

public class OllirGenerator extends AJmmVisitor<Integer, Code> {

    private final SymbolTableBuilder symbolTable;
    private int counter = 0;

    private int cond_counter = 0;
    
    
    public OllirGenerator(SymbolTableBuilder symbolTable) {
        this.symbolTable = symbolTable;

        addVisit(AstNode.START.toString(), this::programVisit);
        addVisit(AstNode.CLASS_DECLARATION.toString(), this::classVisit);
        addVisit(AstNode.METHOD_DECLARATION.toString(), this::methodVisit);
        addVisit(AstNode.ASSIGNMENT.toString(), this::assignmentVisit);
        addVisit(AstNode.IDENTIFIER.toString(), this::idVisit);
        addVisit(AstNode.INTEGER_LITERAL.toString(), this::integralVisit);
        addVisit(AstNode.BOOL_FALSE.toString(), this::boolVisit);
        addVisit(AstNode.BOOL_TRUE.toString(), this::boolVisit);
        addVisit(AstNode.SELF_THIS.toString(),this::selfThisVisit);
        addVisit(AstNode.BIN_OP.toString(),this::binOpVisit);
        addVisit(AstNode.UNARY_OP.toString(), this::unaryVisit);
        addVisit(AstNode.MEMBER_CALL.toString(), this::memberVisit);
        addVisit(AstNode.RETURN_VALUE.toString(), this::returnValueVisit);
        addVisit(AstNode.CONSTRUCTOR.toString(), this::contructorVisit);
        addVisit(AstNode.ARRAY_ASSIGN.toString(), this::arrayAssignVisit);
        addVisit(AstNode.IF_ELSE_STATEMENT.toString(), this::ifElseVisit);
        addVisit(AstNode.IF_STATEMENTS.toString(), this::ifStatementsVisit);
        addVisit(AstNode.ELSE_STATEMENTS.toString(), this::elseStatementsVisit);
        addVisit(AstNode.WHILE_LOOP.toString(), this::whileVisit);
        addVisit(AstNode.WHILE_STATEMENTS.toString(), this::whileStatementsVisit);
        //TODO Length
        /*
        addVisit(AstNode.ARGUMENTS.toString(), this::argumentsVisit);

        */


    }

    private Code arrayAssignVisit(JmmNode jmmNode, Integer integer) {
        Code arrayAssign = new Code();

        Code index = visit(jmmNode.getJmmChild(0));

        arrayAssign.code += index.code;

        arrayAssign.address += "new(array,"+ index.address +").array.i32";

        return arrayAssign;
    }


    private Code contructorVisit(JmmNode constructorNode, Integer integer) {
        Code constructor = new Code();

        String className = constructorNode.getJmmChild(0).get("name") ;

        String temp = "t" + counter + "." +  className;
        counter++;
        constructor.address = temp;

        constructor.code += temp + " :=." + className + " new(" + className + ")." + className + ";\n";
        constructor.code += "invokespecial(" + constructor.address +  ", " + '"' + "<init>" +'"' + ").V;\n";

        return constructor;
    }


    private Code memberVisit(JmmNode memberCall, Integer integer) {
        Code memberVisit = new Code();

        Code target = visit(memberCall.getJmmChild(0));

        String methodName;

        if(memberCall.getJmmChild(1).getKind().equals(AstNode.LEN.toString())){
            String temp = "t" + counter +".i32";
            counter++;

            memberVisit.address = temp;
            memberVisit.code += temp + " :=.i32 arraylength(" + target.address + ").i32;\n";

            memberCall.put("name", "length");

            return memberVisit;
        }
        Code methodCalled = visit(memberCall.getJmmChild(1));
        methodName = memberCall.getJmmChild(1).get("name");
        String methodType = "V";
        String invokeType = "invokestatic(";
        String finalCall = "";

        memberVisit.code += target.code += methodCalled.code;


        if(symbolTable.hasMethod(methodName)){
            methodType = OllirUtils.getCode(symbolTable.getReturnType(methodName));
            invokeType = "invokevirtual(";
        }

        if(!methodType.equals("V")){
            String temp = "t" + counter +"." + methodType;
            counter++;
            finalCall += temp + " :=." + methodType + " ";

            memberVisit.address = temp;

        }

        finalCall += invokeType + target.address +", "+ '"'+ methodName + '"';

        if(memberCall.getNumChildren()>2){
            for( JmmNode arg : memberCall.getJmmChild(2).getChildren()){
                Code argument = visit(arg);

                memberVisit.code += argument.code;
                finalCall += ", " +argument.address;

            }
        }



        memberCall.put("name", methodName);

        finalCall += ")." + methodType + ";\n";    //Assuming it is always necessary for a temporary

        memberVisit.code+= finalCall;


        return memberVisit;
    }


    private Code selfThisVisit(JmmNode jmmNode, Integer integer) {
        Code selfThisVisit = new Code();

        selfThisVisit.address = "this";


        return selfThisVisit;
    }

    private Code boolVisit(JmmNode jmmNode, Integer integer) {
        Code boolVisit = new Code();

        switch (jmmNode.getKind()){
            case "BoolTrue": boolVisit.address = "0.bool";break;
            case "BoolFalse":  boolVisit.address = "1.bool";break;
        }

        return boolVisit;
    }

    private Code integralVisit(JmmNode jmmNode, Integer integer) {
        Code integralVisit = new Code();

        integralVisit.address = jmmNode.get("value") + ".i32";

        return integralVisit;
    }

    private Code idVisit(JmmNode id, Integer integer) {
        Code idVisit = new Code();

        var methodName = id.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");
        boolean isRightSideAssign =  id.getAncestor("AssignmentValue").map(jmmNode -> Boolean.TRUE).orElse(false);
        boolean isReturnValue = id.getAncestor("ReturnValue").map(jmmNode -> Boolean.TRUE).orElse(false);
        var idCode = OllirUtils.getCodeFromSymbolTable(id.get("name"), methodName, symbolTable);

        if(idCode == null){
            idVisit.address = id.get("name");
            return idVisit;
        }
        //Not in a method
        if(OllirUtils.isField(id.get("name"), methodName, symbolTable) && (isRightSideAssign || isReturnValue)){

            String idType = idCode.split("\\.", 2)[1];

            String temp = "t" + counter + "." + idType;
            counter++;

            idVisit.code = temp +" :=." + idType + " getfield(this, " + idCode + ")." + idType + ";\n";
            idVisit.address = temp;

            return idVisit;
        }


        idVisit.address = idCode;

        return idVisit;

    }


    private Code programVisit(JmmNode jmmNode, Integer integer) {
        Code program = new Code() ;

        for (var importString : symbolTable.getImports()){
            program.code += "import " + importString + ";\n";
        }

        Code classDeclaration = visit(jmmNode.getJmmChild(jmmNode.getNumChildren()-1));
        program.code += classDeclaration.code;
        return program;
    }

    private Code classVisit(JmmNode jmmNode, Integer integer) {
        Code classDeclaration = new Code();

        classDeclaration.code += OllirUtils.init(symbolTable.getClassName(), symbolTable.getSuper(), symbolTable.getFields());

        for( JmmNode child: jmmNode.getChildren()){
            if(child.getKind().equals("MethodDeclaration")){

                Code methodDeclaration = visit(child);
                classDeclaration.code += methodDeclaration.code;

            }
        }

        classDeclaration.code+= "}\n";

        return classDeclaration;
    }

    private Code methodVisit(JmmNode jmmNode, Integer integer) {
        Code methodDeclaration = new Code();

        var method_name = jmmNode.getJmmChild(1).get("name");

        var isStatic = jmmNode.getOptional("isStatic").map(Boolean::parseBoolean).orElse(false);

        String methodStart = OllirUtils.methodDeclaration(method_name, isStatic,symbolTable.getReturnType(method_name), symbolTable.getParameters(method_name));

        methodDeclaration.code += methodStart;

        for(JmmNode child: jmmNode.getChildren().get(jmmNode.getNumChildren()-1).getChildren()){
            if(!child.getKind().equals(AstNode.LOCAL_VAR_DECLARATION.toString())){
                System.out.printf(child.toString());
                Code memberCode = visit(child);
                methodDeclaration.code += memberCode.code;
            }

        }

        if(symbolTable.getReturnType(method_name).getName().equals("void"))
            methodDeclaration.code += "ret."+ OllirUtils.getCode(symbolTable.getReturnType(method_name)) + ";\n";;

        methodDeclaration.code+= "}\n";

        return methodDeclaration;
    }

    private Code assignmentVisit(JmmNode assign, Integer integer) {
        Code assignment = new Code();

        Code leftSide = visit(assign.getJmmChild(0));
        Code rightSide = visit(assign.getJmmChild(1).getJmmChild(0));

        assignment.code += rightSide.code;

        var methodName = assign.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");

        if(!assign.getJmmChild(0).getKind().contains("BinOp")) {
            if (OllirUtils.isField(assign.getJmmChild(0).get("name"), methodName, symbolTable)) {
                assignment.code += "putfield(this, " + leftSide.address + ", " + rightSide.address + ").V;\n";
            } else {
                assignment.code += leftSide.address + " :=." + OllirUtils.getCode(OllirUtils.getSymbolFromSymbolTable(assign.getJmmChild(0).get("name"), methodName, symbolTable).getType()) + " " + rightSide.address + ";\n";
            }
        }
        return assignment;
    }


    private Code binOpVisit(JmmNode jmmNode, Integer integer) {
        Code binOp = new Code();

        Code leftOp = visit(jmmNode.getJmmChild(0));
        Code rightOp = visit(jmmNode.getJmmChild(1));

        binOp.address  = "t" + counter;
        counter++;

        binOp.code = leftOp.code + rightOp.code;

        switch (jmmNode.get("op")){
            case "add":
                binOp.address += ".i32";
                binOp.code+= binOp.address + " :=.i32 " + leftOp.address + " +.i32 " + rightOp.address + ";\n";
                break;
            case "sub":
                binOp.address += ".i32";
                binOp.code+= binOp.address + ".i32 :=.i32 " + leftOp.address + " -.i32 " + rightOp.address + ";\n";
                break;
            case "div":
                binOp.address += ".i32";
                binOp.code+= binOp.address + ".i32 :=.i32 " + leftOp.address + " /.i32 " + rightOp.address + ";\n";
                break;
            case "mult":
                binOp.address += ".i32";
                binOp.code+= binOp.address + ".i32 :=.i32 " + leftOp.address + " *.i32 " + rightOp.address + ";\n";
                break;
            case "and":
                binOp.address += ".bool";
                binOp.code+= binOp.address + " :=.bool " + leftOp.address + " &&.bool " + rightOp.address + ";\n";
                break;
            case "lower":
                binOp.address += ".bool";

                binOp.code+= binOp.address + " :=.bool " + leftOp.address + " <.bool " + rightOp.address + ";\n";
                break;
            case "index":
                String temp = binOp.address + ".i32";
                binOp.code += binOp.address + ".i32 :=.i32 " + rightOp.address +";\n";
                binOp.address  = "t" + counter;
                counter++;
                binOp.address += ".i32";
                String leftName = leftOp.address.split("\\.")[0]+"." +leftOp.address.split("\\.")[1];
                binOp.code+= binOp.address + " :=.i32 " + leftName + "[" + temp + "].i32;\n";
                break;
        }


        return binOp;
    }


    private Code returnValueVisit(JmmNode ret, Integer integer) {
        Code returnValue = new Code();

        var methodName = ret.getAncestor("MethodDeclaration").map(jmmNode -> jmmNode.getJmmChild(1).get("name")).orElse("Error");

        Code member = visit(ret.getJmmChild(0));

        returnValue.code+= member.code;

        returnValue.code += "ret."+ OllirUtils.getCode(symbolTable.getReturnType(methodName)) + " " + member.address + ";\n";

        return returnValue;
    }

    private Code unaryVisit(JmmNode unary, Integer integer) {
        Code unaryVisit = new Code();

        Code operator = visit(unary.getJmmChild(0));

        unaryVisit.address = "t" + counter + ".bool";
        counter++;

        unaryVisit.code+= operator.code;

        if ("!".equals(unary.get("op"))) {
            unaryVisit.code += unaryVisit.address + " :=.bool !.bool " + operator.address + ";\n";
        }

        return unaryVisit;
    }

    private Code ifElseVisit(JmmNode ifElseNode, Integer integer) {
        Code ifElseValue = new Code();

        Code member = visit(ifElseNode.getJmmChild(0));
        Code ifStatement = visit(ifElseNode.getJmmChild(1));
        Code elseStatement = visit(ifElseNode.getJmmChild(2));

        ifElseValue.code += member.code;
        if(ifElseNode.getJmmChild(2).getKind().contains("ElseStatements"))
            ifElseValue.code+= "if (" + member.address + ") goto ifbody_" + cond_counter + ";\n" + elseStatement.code + "goto endif_" + cond_counter + ";\n" + "ifbody_" + cond_counter + ":\n" + ifStatement.code + "endif_" + cond_counter + ":\n";
        else
            ifElseValue.code+= "if (" + member.address + ") goto ifbody_" + cond_counter + ";\n" + "ifbody_" + cond_counter + ":\n" + ifStatement.code;

        cond_counter++;

        return ifElseValue;
    }

    private Code ifStatementsVisit(JmmNode ifNode, Integer integer) {
        Code ifStatementsValue = new Code();

        for(int i=0; i < ifNode.getNumChildren(); i++){
            Code memberCode = visit(ifNode.getJmmChild(i));
            ifStatementsValue.code += memberCode.code;
        }

        return ifStatementsValue;
    }

    private Code elseStatementsVisit(JmmNode elseNode, Integer integer) {
        Code elseStatementsValue = new Code();

        for(int i=0; i < elseNode.getNumChildren(); i++){
            Code memberCode = visit(elseNode.getJmmChild(i));
            elseStatementsValue.code += memberCode.code;
        }

        return elseStatementsValue;
    }

    private Code whileVisit(JmmNode whileNode, Integer integer) {
        Code whileValue = new Code();

        Code member = visit(whileNode.getJmmChild(0));
        Code whileStatement = visit(whileNode.getJmmChild(1));

        whileValue.code += member.code;
        whileValue.code += "if (" + member.address + ") goto whilebody_" + cond_counter + ";\n" + "goto endwhile_" + cond_counter + ";\n" + "whilebody_" + cond_counter + ":\n" + whileStatement.code + member.code+ "if (" + member.address + ") goto whilebody_" + cond_counter + ";\n" + "endwhile_" + cond_counter + ":\n";

        cond_counter++;

        return whileValue;
    }

    private Code whileStatementsVisit(JmmNode whileNode, Integer integer) {
        Code whileStatementsValue = new Code();

        for(int i=0; i < whileNode.getNumChildren(); i++){
                Code memberCode = visit(whileNode.getJmmChild(i));
                whileStatementsValue.code += memberCode.code;
        }

        return whileStatementsValue;
    }




    /*


    private Integer classVisit(JmmNode classDecl, Integer dummy){
        code.append(OllirUtils.init(symbolTable.getClassName(), symbolTable.getSuper(), symbolTable.getFields()));

        for (var child : classDecl.getChildren()){
            visit(child);
        }

        code.append("}");

        return 0;
    }

    private Integer expressionVisit(JmmNode exprStmt, Integer integer) {
        //se nao tiver a dar coomentar isto
        /*for (var expr : exprStmt.getChildren()){
            visit(expr);
        }
        return 0;
    }

    private Integer memberVisit(JmmNode memberNode, Integer integer) {
        for (var aux : memberNode.getChildren()){
            code.append("invokespecial ");
            visit(aux);
            code.append(", ");
            System.out.println(aux);
        }
        code.append(")\n");
        /*code.append("invokestatic(");
        //TODO: ExprToOLlir -> codebefore, value
        visit(memberNode.getJmmChild(0));
        code.append(", \"");
        visit(memberNode.getJmmChild(1));
        code.append(", \"");
        //visit(memberNode.getJmmChild(2));
        //TODO: getExprType(memberNode)
        code.append(")").append("V");
        return 0;
    }

    private Integer methodVisit(JmmNode methodDecl, Integer integer) {

        var methodSignature = methodDecl.getJmmChild(1).get("name");
        var isStatic = Boolean.valueOf(methodDecl.getOptional("isStatic").isPresent());


        code.append(".method public ");
        if(isStatic){
            code.append("static ");
        }

        code.append(methodSignature).append("(");

        var params = symbolTable.getParameters(methodSignature);

        var paramCode = params.stream().map(symbol -> OllirUtils.getCode(symbol)).collect(Collectors.joining(", "));

        code.append(paramCode);
        code.append(").");
        code.append(OllirUtils.getCode(symbolTable.getReturnType(methodSignature)));
        code.append(" {\n");

        var lastParamIndex = -1;
        for (int i=0; i < methodDecl.getNumChildren(); i++){
            if(methodDecl.getJmmChild(i).getKind().equals(AstNode.PARAM.toString())){
                lastParamIndex = i;
            }
        }

        var statements = methodDecl.getChildren().subList(lastParamIndex+1, methodDecl.getNumChildren());
        for (var stmt : statements){
            System.out.println("STANSSAS: " + stmt);
            visit(stmt);
        }

        code.append("\t}\n");

        return 0;
    }

    private Integer assignmentVisit(JmmNode assignmentNode, Integer integer) {

        return 0;
    }

    private Integer argumentsVisit(JmmNode argsNode, Integer integer) {
        for (var child : argsNode.getChildren()) {
            code.append(" ,");
            visit(child);
        }
        return 0;
    }

    private Integer idVisit(JmmNode idNode, Integer integer) {
        code.append(idNode.get("name"));
        return 0;
    }
    
    */
}
